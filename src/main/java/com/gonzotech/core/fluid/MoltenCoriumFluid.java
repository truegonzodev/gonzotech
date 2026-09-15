package com.gonzotech.core.fluid;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Расплавленный кориум — серый лаво-подобный поток, рождённый meltdown'ом
 * ядерной топки.
 * <p>
 * Реакция с водой повторяет ванильную лаву (1.21):
 * <ul>
 *   <li>соседняя вода: источник → блок {@code gonzotech:corium}
 *       (аналог «источник лавы → обсидиан»), поток → {@code gonzotech:dead_stone};</li>
 *   <li>распространение вниз в воду → {@code gonzotech:dead_stone}.</li>
 * </ul>
 * Сама реакция с соседней водой реализована в {@link MoltenCoriumBlock}.
 */
public abstract class MoltenCoriumFluid extends BaseFlowingFluid {

    protected MoltenCoriumFluid(Properties properties) {
        super(properties);
    }

    /** Тот же «шипящий» level-событие 1501, что и у ванильной лавы. */
    protected static void fizz(LevelAccessor level, BlockPos pos) {
        level.levelEvent(1501, pos, 0);
    }

    /**
     * Как у ванильной лавы: вода вытесняет кориум только при достаточной
     * глубине (высота &ge; 0.444), тонкий поток не отдаёт место.
     */
    @Override
    protected boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid fluid, Direction direction) {
        return state.getHeight(level, pos) >= 0.44444445F && fluid.is(FluidTags.WATER);
    }

    /**
     * Распространение вниз прямо в воду: вместо залития позиция становится
     * dead_stone и звучит шипение (ванильная лава здесь даёт stone — у нас
     * свой результат по задумке мира).
     */
    @Override
    protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState flowingState) {
        if (direction == Direction.DOWN) {
            FluidState fluidState = level.getFluidState(pos);
            if (fluidState.is(FluidTags.WATER)) {
                if (state.getBlock() instanceof LiquidBlock) {
                    level.setBlock(pos,
                        net.neoforged.neoforge.event.EventHooks.fireFluidPlaceBlockEvent(
                            level, pos, pos, ModBlocks.DEAD_STONE.get().defaultBlockState()), 3);
                }
                fizz(level, pos);
                return;
            }
        }
        super.spreadTo(level, pos, state, direction, flowingState);
    }

    /** Источник (статичный уровень, уровень = 8). */
    public static final class Source extends MoltenCoriumFluid {

        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public int getAmount(FluidState state) {
            return 8;
        }

        @Override
        public boolean isSource(FluidState state) {
            return true;
        }
    }

    /** Течущий поток с уровнем 1..7. */
    public static final class Flowing extends MoltenCoriumFluid {

        public Flowing(Properties properties) {
            super(properties);
            registerDefaultState(getStateDefinition().any().setValue(LEVEL, 7));
        }

        @Override
        public int getAmount(FluidState state) {
            return state.getValue(LEVEL);
        }

        @Override
        public boolean isSource(FluidState state) {
            return false;
        }

        @Override
        protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
            super.createFluidStateDefinition(builder);
            builder.add(LEVEL);
        }
    }
}
