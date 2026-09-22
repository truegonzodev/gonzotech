package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.FluidBlends.MashBlend;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.DistillateSink;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Sinks.HotWaterSink;
import com.gonzotech.machines.energy.Sinks.MashSink;
import com.gonzotech.machines.energy.Sinks.PoisonPotionSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.energy.Sinks.WortSink;
import com.gonzotech.machines.menu.DistillerMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Блок-сущность Дистиллятора (Эпоха III, «Открытие 3»).
 * <ul>
 *   <li>Вход сырья (брага/сусло): 8 040 mB ({@link MashBlend}).</li>
 *   <li>Охлаждающая вода: 2 006 mB; кипяток на выходе: 512 mB.</li>
 *   <li>Буфер GTH: 12 096.</li>
 *   <li>Выход дистиллята / зелья: 6 096 mB.</li>
 *   <li>Перегонка: 11 mB/t сырья + 20 mB воды + 64 GTH/t → 11 mB дистиллята 48% + 20 mB кипятка.</li>
 *   <li>При гнили > 8% (или яде на выходе > 127 mB): партия превращается в зелье отравления II 2:1 (11 mB → 5.5 mB).</li>
 *   <li>Автовыдача дистиллята, кипятка и зелья: до 688 mB/t.</li>
 * </ul>
 */
public class DistillerBlockEntity extends BaseMachineBlockEntity
    implements GthSink, WaterSink, MashSink, WortSink, WorldlyContainer {

    public static final int INPUT_CAPACITY = 8_040;
    public static final int WATER_CAPACITY = 2_006;
    public static final int GTH_CAPACITY = 12_096;
    public static final int OUTPUT_CAPACITY = 6_096;
    public static final int HOT_WATER_CAPACITY = 512;

    public static final int PROCESS_RAW_RATE = 11;
    public static final int PROCESS_WATER_RATE = 20;
    public static final int PROCESS_GTH_COST = 64;
    public static final int MAX_DRAIN_PER_TICK = 688;

    private static final int GTH_PACKET_BASE = 10_000;
    private static final int[] NO_SLOTS = new int[0];

    private long currentGthMilli = 0;
    private MashBlend rawInput = MashBlend.EMPTY;
    private final ResourceBuffer water = new ResourceBuffer(WATER_CAPACITY);
    private final ResourceBuffer boilingWater = new ResourceBuffer(HOT_WATER_CAPACITY);
    private final ResourceBuffer distillate = new ResourceBuffer(OUTPUT_CAPACITY);
    private final ResourceBuffer poison = new ResourceBuffer(OUTPUT_CAPACITY);
    private int poisonRemainder = 0;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return getData(i);
        }

        @Override
        public void set(int i, int value) {
        }

        @Override
        public int getCount() {
            return DistillerMenu.DATA_COUNT;
        }
    };

    public DistillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_DISTILLER.get(), pos, state, 0);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_distiller");
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new DistillerMenu(id, inv, this, data);
    }

    public int getCurrentGth() {
        return (int) (currentGthMilli / MachineDefs.MILLI);
    }

    public int getMaxGth() {
        return GTH_CAPACITY;
    }

    public MashBlend getRawInput() {
        return rawInput;
    }

    public ResourceBuffer getWater() {
        return water;
    }

    public ResourceBuffer getBoilingWater() {
        return boilingWater;
    }

    public ResourceBuffer getDistillate() {
        return distillate;
    }

    public ResourceBuffer getPoison() {
        return poison;
    }

    public int outputSpace() {
        return Math.max(0, OUTPUT_CAPACITY - (distillate.amount() + poison.amount()));
    }

    // ─────────────────────────── Sinks ───────────────────────────

    @Override
    public long receiveGth(long amountMilli, boolean simulate) {
        long maxMilli = (long) GTH_CAPACITY * MachineDefs.MILLI;
        long space = Math.max(0, maxMilli - currentGthMilli);
        long accepted = Math.min(amountMilli, space);
        if (!simulate && accepted > 0) {
            currentGthMilli += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveWater(long amount, boolean simulate) {
        int accepted = water.receive(amount, simulate);
        if (!simulate && accepted > 0) {
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveMash(long amount, double alcoholPercent, double rotPercent, boolean simulate) {
        long space = Math.max(0, INPUT_CAPACITY - rawInput.amount());
        long accepted = Math.min(amount, space);
        if (!simulate && accepted > 0) {
            rawInput = rawInput.withAdded(accepted, alcoholPercent, rotPercent);
            setChanged();
        }
        return accepted;
    }

    @Override
    public long receiveWort(long amount, double alcoholPercent, boolean simulate) {
        // Сусло — очищенное сырьё с 0% гнили
        return receiveMash(amount, alcoholPercent, 0.0, simulate);
    }

    // ─────────────────────────── Тик ───────────────────────────

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        boolean changed = false;

        // 1. Перегонка
        long gthCostMilli = (long) PROCESS_GTH_COST * MachineDefs.MILLI;
        if (rawInput.amount() >= PROCESS_RAW_RATE
            && water.amount() >= PROCESS_WATER_RATE
            && currentGthMilli >= gthCostMilli
            && boilingWater.space() >= PROCESS_WATER_RATE
            && outputSpace() >= PROCESS_RAW_RATE) {

            boolean isPoison = rawInput.rotPercent() > 8.0 || poison.amount() > 127;

            currentGthMilli -= gthCostMilli;
            water.extract(PROCESS_WATER_RATE, false);
            boilingWater.receive(PROCESS_WATER_RATE, false);
            rawInput = rawInput.withExtracted(PROCESS_RAW_RATE);

            if (isPoison) {
                // Конверсия 2:1 (1000 mB -> 500 mB)
                int totalPoisonUnits = PROCESS_RAW_RATE + poisonRemainder;
                int poisonMb = totalPoisonUnits / 2;
                poisonRemainder = totalPoisonUnits % 2;
                poison.receive(poisonMb, false);
            } else {
                distillate.receive(PROCESS_RAW_RATE, false);
            }
            changed = true;
        }

        // 2. Автослив кипятка до 688 mB/t
        if (boilingWater.amount() > 0) {
            long budget = Math.min((long) MAX_DRAIN_PER_TICK, (long) boilingWater.amount());
            long moved = PipeRouting.drain(level, pos, PipeType.BOILING_WATER, budget, level.getGameTime(), (be, p) -> {
                if (be instanceof DistillerBlockEntity) return null;
                if (be instanceof HotWaterSink sink) return sink::receiveHotWater;
                return null;
            });
            if (moved > 0) {
                boilingWater.extract(moved, false);
                changed = true;
            }
        }

        // 3. Автослив дистиллята до 688 mB/t
        if (distillate.amount() > 0) {
            long budget = Math.min((long) MAX_DRAIN_PER_TICK, (long) distillate.amount());
            long moved = PipeRouting.drain(level, pos, PipeType.DISTILLATE, budget, level.getGameTime(), (be, p) -> {
                if (be instanceof DistillerBlockEntity) return null;
                if (be instanceof DistillateSink sink) return sink::receiveDistillate;
                return null;
            });
            if (moved > 0) {
                distillate.extract(moved, false);
                changed = true;
            }
        }

        // 4. Автослив зелья отравления до 688 mB/t
        if (poison.amount() > 0) {
            long budget = Math.min((long) MAX_DRAIN_PER_TICK, (long) poison.amount());
            long moved = PipeRouting.drain(level, pos, PipeType.POISON_POTION, budget, level.getGameTime(), (be, p) -> {
                if (be instanceof DistillerBlockEntity) return null;
                if (be instanceof PoisonPotionSink sink) return sink::receivePoisonPotion;
                return null;
            });
            if (moved > 0) {
                poison.extract(moved, false);
                changed = true;
            }
        }

        if (changed) {
            setChanged();
        }
    }

    // ─────────────────────────── ContainerData ───────────────────────────

    public int getData(int index) {
        int gth = getCurrentGth();
        return switch (index) {
            case 0 -> gth % GTH_PACKET_BASE;
            case 1 -> gth / GTH_PACKET_BASE;
            case 2 -> water.amount();
            case 3 -> boilingWater.amount();
            case 4 -> (int) Math.min(Integer.MAX_VALUE, rawInput.amount());
            case 5 -> (int) Math.round(rawInput.alcoholPercent() * 100.0);
            case 6 -> (int) Math.round(rawInput.rotPercent() * 100.0);
            case 7 -> distillate.amount();
            case 8 -> poison.amount();
            default -> 0;
        };
    }

    // ─────────────────────────── WorldlyContainer ───────────────────────────

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction dir) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // ─────────────────────────── Сериализация ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("GthMilli", currentGthMilli);
        tag.putInt("PoisonRemainder", poisonRemainder);
        rawInput.save(tag, "Raw");
        water.save(tag, "Water");
        boilingWater.save(tag, "BoilingWater");
        distillate.save(tag, "Distillate");
        poison.save(tag, "Poison");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        currentGthMilli = tag.getLong("GthMilli");
        poisonRemainder = tag.getInt("PoisonRemainder");
        rawInput = MashBlend.load(tag, "Raw");
        water.load(tag, "Water");
        boilingWater.load(tag, "BoilingWater");
        distillate.load(tag, "Distillate");
        poison.load(tag, "Poison");
    }
}
