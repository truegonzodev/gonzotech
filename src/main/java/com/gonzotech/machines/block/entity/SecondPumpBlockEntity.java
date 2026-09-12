package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.ResourceBuffer;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Sinks.WaterSink;
import com.gonzotech.machines.menu.SecondPumpMenu;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * Помпа II без предметной автоматики: только шкалы GTU/Water и откачка мира.
 */
public final class SecondPumpBlockEntity extends BaseMachineBlockEntity implements GtuSink, WaterSink {

    private final GtBuffer gtu = new GtBuffer((long) SecondTierDefs.PUMP_GTU_CAPACITY);
    private final ResourceBuffer water = new ResourceBuffer(SecondTierDefs.PUMP_WATER_CAPACITY);

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> gtu.amountUnitsInt();
                case 1 -> water.amount();
                default -> 0;
            };
        }

        @Override
        public void set(int i, int value) {
            switch (i) {
                case 0 -> gtu.set((long) value * 1_000L);
                case 1 -> water.set(value);
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public SecondPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SECOND_PUMP.get(), pos, state, 0);
    }

    public ContainerData data() {
        return data;
    }

    @Override
    public long receiveGtu(long amount, boolean simulate) {
        return gtu.receive(Math.min(amount, (long) SecondTierDefs.PUMP_GTU_INTAKE), simulate);
    }

    /** Помпа остаётся источником воды; внешний приём воды ей не нужен. */
    @Override
    public long receiveWater(long amount, boolean simulate) {
        return 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SecondPumpBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;

        boolean powered = false;
        if (be.gtu.has(SecondTierDefs.PUMP_GTU_MILLI_PER_TICK)) {
            be.gtu.extract(SecondTierDefs.PUMP_GTU_MILLI_PER_TICK, false);
            powered = true;
            changed = true;
        }

        if (powered && be.water.space() >= 1_000
            && server.getGameTime() % SecondTierDefs.PUMP_SUCK_INTERVAL == 0
            && be.suckOneSource(server, pos)) {
            changed = true;
        }
        if (be.pushWater(server, pos)) changed = true;
        if (changed) be.setChanged();
    }

    private boolean suckOneSource(ServerLevel server, BlockPos pos) {
        for (int dy = 1; dy >= -1; dy--) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos found = pos.offset(dx, dy, dz);
                    FluidState fluid = server.getFluidState(found);
                    if (fluid.getType() != Fluids.WATER || !fluid.isSource()) continue;
                    removeWaterSource(server, found);
                    water.receive(1_000, false); // unspecified amount: unchanged from pump I
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeWaterSource(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED)
            && state.getValue(BlockStateProperties.WATERLOGGED)) {
            server.setBlock(pos, state.setValue(BlockStateProperties.WATERLOGGED, false), 3);
        } else {
            server.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private boolean pushWater(Level level, BlockPos pos) {
        if (water.isEmpty()) return false;
        long budget = Math.min((long) SecondTierDefs.PUMP_WATER_OUTPUT, water.amount());
        long moved = PipeRouting.drain(level, pos, PipeType.WATER, budget, level.getGameTime(), (target, ignored) -> {
            if (target instanceof PumpBlockEntity || target instanceof SecondPumpBlockEntity) return null;
            if (target instanceof WaterSink sink) return sink::receiveWater;
            return null;
        });
        if (moved <= 0) return false;
        water.extract(moved, false);
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gtu.save(tag, "Gtu");
        water.save(tag, "Water");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtu.load(tag, "Gtu");
        water.load(tag, "Water");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new SecondPumpMenu(id, inv, this, data);
    }
}
