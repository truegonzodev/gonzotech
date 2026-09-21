package com.gonzotech.core.fluid;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseFireBlock;
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

    /**
     * Как ванильная лава, кориум поджигает горючие блоки: каждый случайный
     * тик источника/потока есть шанс разжечь огонь у соседнего горючего
     * блока (дублирует {@code LavaFluid#randomTick} из 1.21.4).
     */
    @Override
    protected boolean isRandomlyTicking() {
        return true;
    }

    @Override
    protected void randomTick(ServerLevel level, BlockPos pos, FluidState state, RandomSource random) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return;
        int passes = random.nextInt(3);
        if (passes > 0) {
            BlockPos testPos = pos;
            for (int pass = 0; pass < passes; pass++) {
                testPos = testPos.offset(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                if (!level.isLoaded(testPos)) return;
                BlockState blockState = level.getBlockState(testPos);
                if (blockState.isAir()) {
                    if (hasFlammableNeighbours(level, testPos)) {
                        level.setBlockAndUpdate(testPos, BaseFireBlock.getState(level, testPos));
                        return;
                    }
                } else if (blockState.blocksMotion()) {
                    return;
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                BlockPos testPos = pos.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (!level.isLoaded(testPos)) return;
                if (level.isEmptyBlock(testPos.above()) && isFlammable(level, testPos)) {
                    level.setBlockAndUpdate(testPos.above(), BaseFireBlock.getState(level, testPos));
                }
            }
        }
    }

    private static boolean hasFlammableNeighbours(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (isFlammable(level, pos.relative(direction))) return true;
        }
        return false;
    }

    private static boolean isFlammable(LevelReader level, BlockPos pos) {
        return level.isInsideBuildHeight(pos.getY()) && !level.hasChunkAt(pos)
            ? false
            : level.getBlockState(pos).ignitedByLava();
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
