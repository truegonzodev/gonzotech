package com.gonzotech.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** Industrial-strength TNT whose blast is clipped to a forward-facing tunnel. */
public final class TunnelTntBlock extends CustomTntBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final double BLAST_LENGTH = 20.0D;
    public static final double BLAST_HALF_WIDTH = 2.0D;

    public TunnelTntBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void ignite(Level level, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState displayState = serverLevel.getBlockState(pos);
        if (!displayState.is(this)) {
            displayState = defaultBlockState();
        }
        Direction facing = displayState.getValue(FACING);
        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        TunnelPrimedTnt primedTnt = new TunnelPrimedTnt(
            serverLevel, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, igniter, facing);
        primedTnt.setBlockState(displayState);
        serverLevel.addFreshEntity(primedTnt);
        serverLevel.playSound(null, primedTnt.getX(), primedTnt.getY(), primedTnt.getZ(),
            net.minecraft.sounds.SoundEvents.TNT_PRIMED, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
