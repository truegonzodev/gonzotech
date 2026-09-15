package com.gonzotech.core.fluid;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.redstone.Orientation;
import org.jetbrains.annotations.Nullable;

/**
 * Блок расплавленного кориума. Повторяет ванильную {@code LiquidBlock}-логику
 * лавы, но реакция с водой своя:
 * <ul>
 *   <li>источник рядом с водой → застывший блок {@code gonzotech:corium}
 *       (аналог «лавовый источник → обсидиан»);</li>
 *   <li>поток рядом с водой → {@code gonzotech:dead_stone}
 *       (аналог «текущая лава → булыжник»);</li>
 *   <li>затем обычное распространение, если воды рядом нет.</li>
 * </ul>
 */
public final class MoltenCoriumBlock extends LiquidBlock {

    /** Ванильный набор направлений течения: вниз и четыре горизонтали (без вверх). */
    private static final Direction[] POSSIBLE_FLOW_DIRECTIONS = {
        Direction.DOWN, Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST
    };

    public MoltenCoriumBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (shouldReactOrSpread(level, pos, state)) {
            level.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean isMoving) {
        if (shouldReactOrSpread(level, pos, state)) {
            level.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess access, BlockPos pos,
                                     Direction side, BlockPos neighbor, BlockState neighborState, RandomSource random) {
        if (state.getFluidState().isSource() || neighborState.getFluidState().isSource()) {
            access.scheduleTick(pos, state.getFluidState().getType(), this.fluid.getTickDelay(level));
        }
        return super.updateShape(state, level, access, pos, side, neighbor, neighborState, random);
    }

    /**
     * Реакция с примыкающей водой: источник застывает в блок кориум,
     * поток превращается в dead_stone, играет ванильный шипящий звук.
     * Возвращает false, если реакция произошла и распространять жидкость не нужно.
     */
    private boolean shouldReactOrSpread(Level level, BlockPos pos, BlockState state) {
        for (Direction direction : POSSIBLE_FLOW_DIRECTIONS) {
            BlockPos adjacent = pos.relative(direction);
            if (level.getFluidState(adjacent).is(FluidTags.WATER)) {
                Block result = state.getFluidState().isSource() ? ModBlocks.CORIUM.get() : ModBlocks.DEAD_STONE.get();
                level.setBlockAndUpdate(pos, result.defaultBlockState());
                level.levelEvent(1501, pos, 0);
                return false;
            }
        }
        return true;
    }
}
