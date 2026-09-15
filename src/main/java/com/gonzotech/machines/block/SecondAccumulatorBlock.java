package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SecondAccumulatorBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Аккумулятор второго открытия.
 *
 * <p>Использует самостоятельный BlockEntity второго уровня: первый аккумулятор
 * остаётся неизменным, включая свой баланс и сохранённые миры.</p>
 */
public final class SecondAccumulatorBlock extends AccumulatorBlock {

    public static final MapCodec<SecondAccumulatorBlock> CODEC = simpleCodec(SecondAccumulatorBlock::new);

    public SecondAccumulatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondAccumulatorBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SecondAccumulatorBlockEntity accumulator
            ? accumulator.comparatorOutput()
            : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecondAccumulatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_ACCUMULATOR.get(),
            SecondAccumulatorBlockEntity::serverTick);
    }
}
