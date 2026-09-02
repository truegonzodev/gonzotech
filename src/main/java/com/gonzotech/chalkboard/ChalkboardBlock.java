package com.gonzotech.chalkboard;

import com.gonzotech.chalkboard.client.ChalkboardClientHandler;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Блок доски резонанса — ПКМ (пустой рукой или с любым предметом, не
 * перехватывающим клик на себя) открывает {@link ChalkboardClientHandler}.
 *
 * Направление установки (6 вариантов: NORTH, SOUTH, WEST, EAST, UP, DOWN)
 * задаётся через свойство {@link DirectionalBlock#FACING}:
 * при клике на стену/пол/потолок верхняя рабочая поверхность (top) смотрит на игрока/вверх.
 */
public class ChalkboardBlock extends DirectionalBlock {

    public static final MapCodec<ChalkboardBlock> CODEC = simpleCodec(ChalkboardBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public ChalkboardBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<ChalkboardBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            ChalkboardClientHandler.openScreen();
        }
        return InteractionResult.SUCCESS;
    }
}
