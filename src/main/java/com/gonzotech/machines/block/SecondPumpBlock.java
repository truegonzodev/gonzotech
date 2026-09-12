package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.PumpBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Помпа второго открытия; до отдельного балансного patch использует ровно ту же
 * механику, буферы и лимиты, что и помпа первого открытия.
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
        return new PumpBlockEntity(ModBlockEntities.SECOND_PUMP.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_PUMP.get(), PumpBlockEntity::serverTick);
    }
}
