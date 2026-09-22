package com.gonzotech.machines.block.entity;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.energy.Sinks;
import com.gonzotech.machines.energy.Sinks.HotWaterSink;
import com.gonzotech.machines.menu.SnaketypeCondenserMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок-энтити Змеевикового конденсатора («Открытие 3»):
 * <ul>
 *   <li>Приём кипятка: до 1 560 mB/t в бак 8 000 mB;</li>
 *   <li>Бак охлаждённой воды: 8 000 mB;</li>
 *   <li>Базовая скорость охлаждения: 2 mB/t;</li>
 *   <li>Бонусы от смежных блоков льда (6 сторон):
 *       обычный лёд (+1), плотный (+3), европианский (+7), синий (+12), сверхплотный (+29 mB/t);</li>
 *   <li>Автослив воды: до 1 560 mB/t в водные трубы и приёмники воды.</li>
 * </ul>
 */
public class SnaketypeCondenserBlockEntity extends BaseMachineBlockEntity
    implements HotWaterSink, net.minecraft.world.WorldlyContainer {

    public static final int BOILING_WATER_CAPACITY = 8_000;
    public static final int WATER_CAPACITY = 8_000;
    public static final int MAX_INLET_PER_TICK = 1_560;
    public static final int BASE_COOLING_RATE = 2;
    public static final int MAX_WATER_DRAIN = 1_560;

    private int boilingWaterAmount = 0;
    private int waterAmount = 0;

    private int currentCoolingRate = BASE_COOLING_RATE;
    private int regularIceCount = 0;
    private int packedIceCount = 0;
    private int europanIceCount = 0;
    private int blueIceCount = 0;
    private int superdenseIceCount = 0;

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return getData(index);
        }

        @Override
        public void set(int index, int value) {
            setData(index, value);
        }

        @Override
        public int getCount() {
            return SnaketypeCondenserMenu.DATA_COUNT;
        }
    };

    public SnaketypeCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_SNAKETYPE_CONDENSER.get(), pos, state);
    }

    public int getBoilingWater() {
        return boilingWaterAmount;
    }

    public int getWater() {
        return waterAmount;
    }

    public int getCurrentCoolingRate() {
        return currentCoolingRate;
    }

    public int getRegularIceCount() {
        return regularIceCount;
    }

    public int getPackedIceCount() {
        return packedIceCount;
    }

    public int getEuropanIceCount() {
        return europanIceCount;
    }

    public int getBlueIceCount() {
        return blueIceCount;
    }

    public int getSuperdenseIceCount() {
        return superdenseIceCount;
    }

    // ─────────────────────────── HotWaterSink ───────────────────────────

    @Override
    public long receiveHotWater(long amount, boolean simulate) {
        long space = Math.max(0, BOILING_WATER_CAPACITY - boilingWaterAmount);
        long accepted = Math.min(amount, Math.min(MAX_INLET_PER_TICK, space));
        if (!simulate && accepted > 0) {
            boilingWaterAmount += (int) accepted;
            setChanged();
        }
        return accepted;
    }

    // ─────────────────────────── Тик ───────────────────────────

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        boolean changed = false;

        // 1. Опрос 6 смежных сторон на типы льда
        int regIce = 0;
        int pckIce = 0;
        int eurIce = 0;
        int bluIce = 0;
        int supIce = 0;

        for (Direction dir : Direction.values()) {
            BlockState neighbor = level.getBlockState(pos.relative(dir));
            if (neighbor.is(Blocks.ICE)) {
                regIce++;
            } else if (neighbor.is(Blocks.PACKED_ICE)) {
                pckIce++;
            } else if (neighbor.is(ModBlocks.EUROPAN_ICE.get())) {
                eurIce++;
            } else if (neighbor.is(Blocks.BLUE_ICE)) {
                bluIce++;
            } else if (neighbor.is(ModBlocks.SUPERDENSE_ICE.get())) {
                supIce++;
            }
        }

        regularIceCount = regIce;
        packedIceCount = pckIce;
        europanIceCount = eurIce;
        blueIceCount = bluIce;
        superdenseIceCount = supIce;

        int bonus = regIce * 1 + pckIce * 3 + eurIce * 7 + bluIce * 12 + supIce * 29;
        currentCoolingRate = BASE_COOLING_RATE + bonus;

        // 2. Конденсация кипятка в воду
        int waterSpace = Math.max(0, WATER_CAPACITY - waterAmount);
        int toCool = Math.min(currentCoolingRate, Math.min(boilingWaterAmount, waterSpace));
        if (toCool > 0) {
            boilingWaterAmount -= toCool;
            waterAmount += toCool;
            changed = true;
        }

        // 3. Автослив воды
        if (waterAmount > 0) {
            long budget = Math.min((long) MAX_WATER_DRAIN, (long) waterAmount);
            long moved = PipeRouting.drain(level, pos, PipeType.WATER, budget, level.getGameTime(), (beTarget, p) -> {
                if (beTarget instanceof SnaketypeCondenserBlockEntity) return null;
                if (beTarget instanceof Sinks.WaterSink sink) {
                    return sink::receiveWater;
                }
                return null;
            });
            if (moved > 0) {
                waterAmount -= (int) moved;
                changed = true;
            }
        }

        if (changed) {
            setChanged();
        }
    }

    // ─────────────────────────── ContainerData ───────────────────────────

    public int getData(int index) {
        return switch (index) {
            case 0 -> boilingWaterAmount;
            case 1 -> waterAmount;
            case 2 -> currentCoolingRate;
            case 3 -> regularIceCount;
            case 4 -> packedIceCount;
            case 5 -> europanIceCount;
            case 6 -> blueIceCount;
            case 7 -> superdenseIceCount;
            default -> 0;
        };
    }

    public void setData(int index, int value) {
        switch (index) {
            case 0 -> boilingWaterAmount = value;
            case 1 -> waterAmount = value;
            case 2 -> currentCoolingRate = value;
            case 3 -> regularIceCount = value;
            case 4 -> packedIceCount = value;
            case 5 -> europanIceCount = value;
            case 6 -> blueIceCount = value;
            case 7 -> superdenseIceCount = value;
            default -> {}
        }
    }

    // ─────────────────────────── WorldlyContainer (0 слотов) ───────────────────────────

    @Override
    public int getContainerSize() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return new int[0];
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction dir) {
        return false;
    }

    // ─────────────────────────── NBT & Menu ───────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("BoilingWater", boilingWaterAmount);
        tag.putInt("Water", waterAmount);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boilingWaterAmount = tag.getInt("BoilingWater");
        waterAmount = tag.getInt("Water");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.gonzotech.third_snaketype_condenser");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new SnaketypeCondenserMenu(id, playerInv, this, this.dataAccess);
    }
}
