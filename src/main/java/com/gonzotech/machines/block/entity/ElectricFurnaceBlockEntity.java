package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.menu.ElectricFurnaceMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Электропечь: плавит предметы, питаясь GTU. Слота топлива НЕТ — только вход (0)
 * и выход (1). Скорость 160% ванили ({@link MachineDefs#ELECTRIC_COOK_TIME}
 * тиков/предмет), суммарный расход {@link MachineDefs#ELECTRIC_GTU_PER_ITEM} GTU
 * на предмет.
 * <p>
 * Питается GTU из своего буфера — ОТКУДА бы он ни пришёл (провод дотянет энергию
 * от любого генератора; примыкающий сосед не требуется). GTU-ёмкость
 * ({@link MachineDefs#ELECTRIC_GTU_CAPACITY}) влезает в short, поэтому
 * синхронизируется одним слотом {@link ContainerData}.
 */
public class ElectricFurnaceBlockEntity extends BaseMachineBlockEntity
    implements GtuSink, WorldlyContainer, ExperienceOutput {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    private static final int[] SLOTS_TOP = {SLOT_INPUT};
    private static final int[] SLOTS_BOTTOM = {SLOT_OUTPUT};
    private static final int[] SLOTS_SIDE = {SLOT_INPUT};

    /** Интервал между звуками работы печи, тиков. */
    private static final int SOUND_INTERVAL = 60;

    // GTU — в GtBuffer (BigInteger, милли).
    private final GtBuffer gtu = new GtBuffer((long) MachineDefs.ELECTRIC_GTU_CAPACITY);

    private int cookProgress;
    private int cookTotal;
    /** Накопленный опыт за переплавку — выдаётся игроку при заборе результата. */
    private float storedXp;
    /** Кулдаун звука работы. */
    private int soundCooldown;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                // GTU — в целых единицах (÷1000, ContainerData = short).
                case 0 -> gtu.amountUnitsInt();
                case 1 -> cookProgress;
                case 2 -> cookTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gtu.set(MachineDefs.toMilli(v));
                case 1 -> cookProgress = v;
                case 2 -> cookTotal = v;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 3;
        }
    };

    public ElectricFurnaceBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.ELECTRIC_FURNACE.get(), pos, state);
    }

    /**
     * Внутренний конструктор для функционально идентичной машины другого
     * открытия: состояние и баланс остаются общими, тип BE — свой.
     */
    public ElectricFurnaceBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState state) {
        super(blockEntityType, pos, state, 2);
    }

    public GtBuffer gtuBuffer() {
        return gtu;
    }

    public ContainerData data() {
        return data;
    }

    // ─────────────────────────── GtuSink ───────────────────────────

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        return gtu.receive(Math.min(amount, (long) MachineDefs.ELECTRIC_GTU_INTAKE), simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectricFurnaceBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // Развязано от «соседа»-генератора: печь работает от GTU, откуда бы он ни
        // пришёл (провод дотянет от любого источника). Псевдомногоблок оставлен
        // только для топка+котёл.
        boolean canSmelt = SmeltHelper.canOutput(server, be.items.get(SLOT_INPUT), be.items.get(SLOT_OUTPUT));

        boolean worked = false;
        if (canSmelt) {
            if (be.cookTotal == 0) {
                be.cookTotal = MachineDefs.ELECTRIC_COOK_TIME;
            }
            // Расход GTU за тик — РОВНО 1600 mGTU (1.6 GTU/t). Буфер теперь в милли,
            // поэтому дробь выражается точно и аккумулятор не нужен.
            int need = MachineDefs.ELECTRIC_GTU_MILLI_PER_TICK;
            if (be.gtu.has(need)) {
                be.gtu.extract(need, false);
                be.cookProgress++;
                if (be.cookProgress >= be.cookTotal) {
                    SmeltHelper.Result r = SmeltHelper.finish(server, be.items, SLOT_INPUT, SLOT_OUTPUT);
                    be.storedXp += r.experience();
                    be.cookProgress = 0;
                    be.cookTotal = 0;
                    // Фаза 3: побочки плавки (цезий → взрыв, железо → шанс свинца).
                    boolean exploded = SmeltSideEffects.apply(server, pos, r.produced(), be);
                    if (exploded) {
                        return; // блок печи уничтожен взрывом
                    }
                }
                worked = true;
                changed = true;

                // Звук работающей печи (угольки/пламя), периодически.
                if (be.soundCooldown <= 0) {
                    server.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F);
                    be.soundCooldown = SOUND_INTERVAL;
                } else {
                    be.soundCooldown--;
                }
            }
        }

        if (!worked) {
            be.soundCooldown = 0;
            if (be.cookProgress != 0 || be.cookTotal != 0) {
                be.cookProgress = Math.max(0, be.cookProgress - 2);
                if (be.cookProgress == 0) {
                    be.cookTotal = 0;
                }
                changed = true;
            }
        }

        if (changed) {
            be.setChanged();
        }
    }

    // ─────────────────────────── ExperienceOutput ───────────────────────────

    @Override
    public void awardExperienceTo(Player player) {
        if (level instanceof ServerLevel server) {
            storedXp = SmeltHelper.awardExperience(server, player, storedXp);
            setChanged();
        }
    }

    // ─────────────────────── WorldlyContainer (автоматизация) ───────────────────────
    // У электропечи слота топлива нет: сверху/сбоку — вход, снизу — выход.

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? SLOTS_BOTTOM : (side == Direction.UP ? SLOTS_TOP : SLOTS_SIDE);
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_INPUT;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == SLOT_OUTPUT;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("CookTotal", cookTotal);
        tag.putFloat("StoredXp", storedXp);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        cookProgress = tag.getInt("CookProgress");
        cookTotal = tag.getInt("CookTotal");
        storedXp = tag.getFloat("StoredXp");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ElectricFurnaceMenu(id, inv, this, data);
    }
}
