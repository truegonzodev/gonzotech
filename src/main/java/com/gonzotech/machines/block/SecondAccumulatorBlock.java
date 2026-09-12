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

/**
 * Аккумулятор второго открытия.
 *
 * <p>Пока это намеренно точная функциональная копия первого аккумулятора: отдельный
 * BlockEntityType нужен только потому, что NeoForge привязывает тип BE к списку
 * допустимых блоков. Баланс второго уровня будет вынесен в отдельный patch.</p>
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
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AccumulatorBlockEntity(ModBlockEntities.SECOND_ACCUMULATOR.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_ACCUMULATOR.get(),
            AccumulatorBlockEntity::serverTick);
    }
}
