package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.CentrifugeBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Центрифуга ЦФ1УР — первая машина промывки руд атомной эры. */
public class CentrifugeBlock extends MachineBlock {

    public static final MapCodec<CentrifugeBlock> CODEC = simpleCodec(CentrifugeBlock::new);

    public CentrifugeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<CentrifugeBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CentrifugeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.CENTRIFUGE.get(), CentrifugeBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CentrifugeBlockEntity centrifuge) {
            centrifuge.dropPendingOutputsForBreak();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
