package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.ThirdAccumulatorBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Аккумулятор III: три обычных слота; зарядка предметов пока не реализована.
 *
 * <p>Использует самостоятельный BlockEntity третьего уровня: старые аккумуляторы
 * сохраняют свой баланс и формат сохранённых миров.</p>
 */
public final class ThirdAccumulatorBlock extends AccumulatorBlock {

    public static final MapCodec<ThirdAccumulatorBlock> CODEC = simpleCodec(ThirdAccumulatorBlock::new);

    public ThirdAccumulatorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ThirdAccumulatorBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ThirdAccumulatorBlockEntity accumulator
            ? accumulator.comparatorOutput()
            : 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThirdAccumulatorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.THIRD_ACCUMULATOR.get(),
            ThirdAccumulatorBlockEntity::serverTick);
    }
}
