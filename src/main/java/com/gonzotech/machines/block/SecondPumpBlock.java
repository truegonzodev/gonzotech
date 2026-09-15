package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SecondPumpBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Помпа второго открытия; работает без предметных слотов: только шкалы ресурсов и откачка воды.
 * Её баланс независим от помпы первого уровня.
 */
public final class SecondPumpBlock extends PumpBlock {

    public static final MapCodec<SecondPumpBlock> CODEC = simpleCodec(SecondPumpBlock::new);

    public SecondPumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondPumpBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecondPumpBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_PUMP.get(), SecondPumpBlockEntity::serverTick);
    }
}
