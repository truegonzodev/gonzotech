package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.ReprocessingGrinderBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Измельчитель отработки (0.3.250): четыре слота — ТВЭЛ, пустой корпус, шлаки, бочка. */
public final class ReprocessingGrinderBlock extends MachineBlock {

    public static final MapCodec<ReprocessingGrinderBlock> CODEC = simpleCodec(ReprocessingGrinderBlock::new);

    public ReprocessingGrinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<ReprocessingGrinderBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReprocessingGrinderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.REPROCESSING_GRINDER.get(),
            ReprocessingGrinderBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
            && level.getBlockEntity(pos) instanceof ReprocessingGrinderBlockEntity grinder) {
            grinder.dropPendingOutputForBreak();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
