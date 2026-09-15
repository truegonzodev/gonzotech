package com.gonzotech.machines.block;

import com.gonzotech.machines.block.entity.SecondGrinderBlockEntity;
import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Functional two-slot tier-two Grinder. */
public final class SecondGrinderBlock extends MachineBlock {

    public static final MapCodec<SecondGrinderBlock> CODEC = simpleCodec(SecondGrinderBlock::new);

    public SecondGrinderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<SecondGrinderBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecondGrinderBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return FireboxBlock.createTickerHelper(type, ModBlockEntities.SECOND_GRINDER.get(),
            SecondGrinderBlockEntity::serverTick);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SecondGrinderBlockEntity grinder) {
            grinder.dropPendingOutputForBreak();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
