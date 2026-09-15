package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.NuclearFireboxBlockEntity;
import com.gonzotech.machines.energy.NuclearDefs;
import com.gonzotech.machines.nuclear.ThermalHazards;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Discovery-2 single-slot nuclear heat source. */
public final class NuclearFireboxBlock extends MachineBlock {

    public static final MapCodec<NuclearFireboxBlock> CODEC = simpleCodec(NuclearFireboxBlock::new);

    public NuclearFireboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<NuclearFireboxBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof NuclearFireboxBlockEntity firebox
            ? firebox.comparatorOutput()
            : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NuclearFireboxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.NUCLEAR_FIREBOX.get(), NuclearFireboxBlockEntity::serverTick);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide()
            && level.getBlockEntity(pos) instanceof NuclearFireboxBlockEntity firebox
            && firebox.storedGth() > NuclearDefs.NUCLEAR_FIREBOX_IGNITION_THRESHOLD) {
            ThermalHazards.igniteTouchingPlayer(entity);
        }
        super.entityInside(state, level, pos, entity);
    }
}
