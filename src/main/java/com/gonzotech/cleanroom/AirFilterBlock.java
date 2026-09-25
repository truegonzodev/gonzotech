package com.gonzotech.cleanroom;

import com.gonzotech.machines.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Model-rendered block with a real server container, like the other machines. */
public final class AirFilterBlock extends Block implements EntityBlock {
    public static final MapCodec<AirFilterBlock> CODEC = simpleCodec(AirFilterBlock::new);
    public AirFilterBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<AirFilterBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new AirFilterBlockEntity(pos, state); }

    @Override
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AirFilterBlockEntity filter ? filter : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof AirFilterBlockEntity filter) {
            player.openMenu(filter, pos);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        return player.isSecondaryUseActive() ? super.useItemOn(stack, state, level, pos, player, hand, hit)
                : useWithoutItem(state, level, pos, player, hit);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide()
                && level.getBlockEntity(pos) instanceof AirFilterBlockEntity filter) {
            Containers.dropContents(level, pos, filter);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide() && type == ModBlockEntities.AIR_FILTER.get()
                ? (lvl, pos, blockState, be) -> AirFilterBlockEntity.serverTick(lvl, pos, blockState, (AirFilterBlockEntity) be)
                : null;
    }
}
