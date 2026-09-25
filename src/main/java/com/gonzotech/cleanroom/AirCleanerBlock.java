package com.gonzotech.cleanroom;

import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class AirCleanerBlock extends BaseEntityBlock {
    public static final MapCodec<AirCleanerBlock> CODEC = simpleCodec(AirCleanerBlock::new);
    public AirCleanerBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AirCleanerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, s, be) -> { if (be instanceof AirCleanerBlockEntity cleaner) AirCleanerBlockEntity.serverTick(lvl, p, s, cleaner); };
    }
    @Override public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        if (!level.isClientSide && level.hasNeighborSignal(pos)
                && level.getBlockEntity(pos) instanceof AirCleanerBlockEntity cleaner) cleaner.pulse();
    }
}
