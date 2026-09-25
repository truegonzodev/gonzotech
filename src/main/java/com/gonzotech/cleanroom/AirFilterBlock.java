package com.gonzotech.cleanroom;

import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;

public final class AirFilterBlock extends BaseEntityBlock {
    public static final MapCodec<AirFilterBlock> CODEC = simpleCodec(AirFilterBlock::new);
    public AirFilterBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AirFilterBlockEntity(pos, state); }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                         Player player, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider provider) {
            player.openMenu(provider, pos);
        }
        return InteractionResult.SUCCESS;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, s, be) -> { if (be instanceof AirFilterBlockEntity filter) AirFilterBlockEntity.serverTick(lvl, p, s, filter); };
    }
}
