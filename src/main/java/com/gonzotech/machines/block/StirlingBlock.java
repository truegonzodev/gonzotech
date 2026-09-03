package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.StirlingBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Генератор Стирлинга. */
public class StirlingBlock extends MachineBlock {

    public static final MapCodec<StirlingBlock> CODEC = simpleCodec(StirlingBlock::new);

    public StirlingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<StirlingBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StirlingBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.STIRLING.get(), StirlingBlockEntity::serverTick);
    }
}
