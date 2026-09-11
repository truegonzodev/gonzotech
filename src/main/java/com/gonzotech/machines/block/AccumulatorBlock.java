package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.AccumulatorBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Энергохранилище (аккумулятор GTU). */
public class AccumulatorBlock extends MachineBlock {

    public static final MapCodec<AccumulatorBlock> CODEC = simpleCodec(AccumulatorBlock::new);

    public AccumulatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<AccumulatorBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AccumulatorBlockEntity accumulator
            ? accumulator.comparatorOutput()
            : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccumulatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.ACCUMULATOR.get(), AccumulatorBlockEntity::serverTick);
    }
}
