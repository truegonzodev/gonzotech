package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.menu.FireboxMenu;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Топка — как печка на 3 слота (вход-нагрузка, топливо, выход) + шкала GTH справа.
 * <p>
 * <ul>
 *   <li>Уголь в слоте топлива горит сам по себе и наполняет буфер {@code GTH}
 *       ({@link MachineDefs#FIREBOX_GTH_PER_TICK} за тик).</li>
 *   <li>Нагрузка (руда/стейк) плавится «как обычная печь» — казуальная побочка,
 *       GTH на переплавку НЕ тратится.</li>
 *   <li>Если рядом стоит паровой котёл — GTH перетекает в него.</li>
 * </ul>
 */
public class FireboxBlockEntity extends BaseMachineBlockEntity implements GthSink {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_FUEL = 1;
    public static final int SLOT_OUTPUT = 2;

    private final ResourceBuffer gth = new ResourceBuffer(MachineDefs.GTH_CAPACITY);

    /** Оставшееся время горения текущей единицы топлива, тиков. */
    private int litTime;
    /** Полное время горения текущей единицы топлива (для шкалы пламени). */
    private int litDuration;
    /** Прогресс переплавки нагрузки, тиков. */
    private int cookProgress;
    /** Полное время переплавки нагрузки, тиков. */
    private int cookTotal;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gth.amount();
                case 1 -> gth.capacity();
                case 2 -> litTime;
                case 3 -> litDuration;
                case 4 -> cookProgress;
                case 5 -> cookTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
            switch (i) {
                case 0 -> gth.set(v);
                case 2 -> litTime = v;
                case 3 -> litDuration = v;
                case 4 -> cookProgress = v;
                case 5 -> cookTotal = v;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 6;
        }
    };

    public FireboxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FIREBOX.get(), pos, state, 3);
    }

    public ResourceBuffer gth() {
        return gth;
    }

    public ContainerData data() {
        return data;
    }

    public boolean isLit() {
        return litTime > 0;
    }

    // ─────────────────────────── GthSink ───────────────────────────

    @Override
    public int receiveGth(int amount, boolean simulate) {
        return gth.receive(amount, simulate);
    }

    // ─────────────────────────── тик (сервер) ───────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, FireboxBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        // 1. Горение топлива → наполняем GTH.
        if (be.litTime > 0) {
            be.litTime--;
            be.gth.receive(MachineDefs.FIREBOX_GTH_PER_TICK, false);
            changed = true;
        }

        // Разжечь новую единицу топлива, если погасло и есть место под GTH.
        if (be.litTime <= 0 && !be.gth.isFull()) {
            ItemStack fuel = be.items.get(SLOT_FUEL);
            int burn = fuel.getBurnTime(RecipeType.SMELTING, server.fuelValues());
            if (burn > 0) {
                be.litTime = burn;
                be.litDuration = burn;
                ItemStack container = fuel.getCraftingRemainder();
                fuel.shrink(1);
                if (fuel.isEmpty() && !container.isEmpty()) {
                    be.items.set(SLOT_FUEL, container);
                }
                changed = true;
            }
        }

        // 2. Казуальная переплавка нагрузки (не тратит GTH, идёт только пока горит топка).
        if (be.isLit() && SmeltHelper.canOutput(server, be.items.get(SLOT_INPUT), be.items.get(SLOT_OUTPUT))) {
            if (be.cookTotal == 0) {
                be.cookTotal = SmeltHelper.cookTime(server, be.items.get(SLOT_INPUT), MachineDefs.FIREBOX_COOK_TIME);
            }
            be.cookProgress++;
            if (be.cookProgress >= be.cookTotal) {
                SmeltHelper.finish(server, be.items, SLOT_INPUT, SLOT_OUTPUT);
                be.cookProgress = 0;
                be.cookTotal = 0;
            }
            changed = true;
        } else if (be.cookProgress != 0 || be.cookTotal != 0) {
            be.cookProgress = 0;
            be.cookTotal = 0;
            changed = true;
        }

        // 3. Отдать GTH соседнему котлу.
        if (be.pushGth(server, pos)) {
            changed = true;
        }

        if (changed) {
            be.setChanged();
        }
    }

    /** Толкнуть GTH любому соседу-приёмнику (котлу). */
    private boolean pushGth(Level level, BlockPos pos) {
        if (gth.isEmpty()) return false;
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            if (gth.isEmpty()) break;
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be instanceof GthSink sink && !(be instanceof FireboxBlockEntity)) {
                int can = Math.min(MachineDefs.GTH_TRANSFER, gth.amount());
                int accepted = sink.receiveGth(can, false);
                if (accepted > 0) {
                    gth.extract(accepted, false);
                    moved = true;
                }
            }
        }
        return moved;
    }

    // ─────────────────────────── NBT ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gth.save(tag, "Gth");
        tag.putInt("LitTime", litTime);
        tag.putInt("LitDuration", litDuration);
        tag.putInt("CookProgress", cookProgress);
        tag.putInt("CookTotal", cookTotal);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gth.load(tag, "Gth");
        litTime = tag.getInt("LitTime");
        litDuration = tag.getInt("LitDuration");
        cookProgress = tag.getInt("CookProgress");
        cookTotal = tag.getInt("CookTotal");
    }

    // ─────────────────────────── Menu ───────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.firebox");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new FireboxMenu(id, inv, this, data);
    }
}
