package com.gonzotech.cleanroom;

import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

public final class AirCleanerBlock extends Block implements EntityBlock {
    public static final MapCodec<AirCleanerBlock> CODEC = simpleCodec(AirCleanerBlock::new);
    public AirCleanerBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<AirCleanerBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AirCleanerBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() && type == ModBlockEntities.AIR_CLEANER.get()
                ? (lvl, pos, blockState, be) -> AirCleanerBlockEntity.serverTick(lvl, pos, blockState, (AirCleanerBlockEntity) be)
                : null;
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                             @Nullable Orientation orientation, boolean moving) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AirCleanerBlockEntity cleaner) {
            cleaner.updateSignal(level.hasNeighborSignal(pos));
        }
    }
}
