package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SecondPressBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Functional tier-two Press. */
public final class SecondPressBlock extends MachineBlock {

    public static final MapCodec<SecondPressBlock> CODEC = simpleCodec(SecondPressBlock::new);

    public SecondPressBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondPressBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecondPressBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_PRESS.get(),
            SecondPressBlockEntity::serverTick);
    }
}
