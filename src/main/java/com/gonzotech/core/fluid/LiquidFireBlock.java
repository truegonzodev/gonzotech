package com.gonzotech.core.fluid;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.function.Supplier;

/**
 * Fire rendered one block above a source liquid, with its model lowered to the
 * source surface. The fluid remains intact while burning; its source block is
 * consumed only when the 106–196 tick burn timer expires.
 */
public final class LiquidFireBlock extends FireBlock {
    private static final int BURN_STEPS = 15;
    private static final int MIN_STEP_DELAY = 7;
    private static final int STEP_DELAY_RANGE = 7; // 7..13 ticks per step

    private final Supplier<? extends Fluid> fuel;
    private final int flameColor;

    public LiquidFireBlock(Supplier<? extends Fluid> fuel, int flameColor, Properties properties) {
        super(properties);
        this.fuel = fuel;
        this.flameColor = flameColor;
        registerDefaultState(defaultBlockState().setValue(AGE, BURN_STEPS));
    }

    /** Start or refresh this fluid's surface flame. The source itself is never replaced here. */
    public boolean ignite(Level level, BlockPos fluidPos) {
        if (level.isClientSide || !hasFuelSource(level, fluidPos)) return false;

        BlockPos firePos = fluidPos.above();
        if (!level.hasChunkAt(firePos)) return false;
        BlockState existing = level.getBlockState(firePos);
        if (existing.getBlock() instanceof LiquidFireBlock) return false; // Never replace another liquid's active flame.
        if (!existing.isAir() && !(existing.getBlock() instanceof BaseFireBlock)) return false;

        BlockState fireState = getStateForPlacement(level, firePos).setValue(AGE, BURN_STEPS);
        return level.setBlock(firePos, fireState, Block.UPDATE_ALL);
    }

    /** Fire survives only directly over its matching source fluid. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return hasFuelSource(level, pos.below());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Vanilla FireBlock random ticks would spread this same custom block to wood; all spread is handled explicitly below. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Intentionally empty: combustible blocks are ignited with vanilla fire by our scheduled tick.
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos fuelPos = pos.below();
        if (!hasFuelSource(level, fuelPos) || touchesWater(level, pos)) {
            level.removeBlock(pos, false);
            return;
        }

        if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            if (level.isRainingAt(pos) && random.nextInt(3) == 0) {
                level.removeBlock(pos, false);
                return;
            }
            igniteNeighborLiquids(level, fuelPos, random);
            igniteVanillaFlamesOnCombustibles(level, pos, random);
        }

        int remaining = state.getValue(AGE);
        if (remaining <= 0) {
            // Consume exactly the source that sustained this flame; flowing
            // states and other blocks are never removed by this fire.
            if (hasFuelSource(level, fuelPos)) {
                level.setBlock(fuelPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            level.removeBlock(pos, false);
            return;
        }

        level.setBlock(pos, state.setValue(AGE, remaining - 1), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, MIN_STEP_DELAY + random.nextInt(STEP_DELAY_RANGE));
    }

    /** Colored sparks rise from the same FluidState#getHeight used by cosmetic surface foam. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        BlockPos fuelPos = pos.below();
        FluidState fluid = level.getFluidState(fuelPos);
        if (!fluid.isSource() || fluid.getType() != fuel.get()) return;

        double surfaceY = fuelPos.getY() + fluid.getHeight(level, fuelPos);
        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        double y = surfaceY + random.nextDouble() * 0.45;
        if (random.nextInt(3) == 0) {
            level.addParticle(new DustParticleOptions(flameColor, 0.9F), x, y, z,
                    (random.nextDouble() - 0.5) * 0.015, 0.025 + random.nextDouble() * 0.025,
                    (random.nextDouble() - 0.5) * 0.015);
        }
        if (random.nextInt(7) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.035, 0.0);
        }
    }

    private boolean hasFuelSource(LevelReader level, BlockPos pos) {
        FluidState state = level.getFluidState(pos);
        return state.isSource() && state.getType() == fuel.get();
    }

    private static boolean touchesWater(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    /**
     * A liquid flame may ignite another exposed source, but flowing fluid is not
     * treated as fuel. Each destination chooses its own blue/cyan fire block.
     */
    private static void igniteNeighborLiquids(ServerLevel level, BlockPos fuelPos, RandomSource random) {
        for (Direction direction : Direction.values()) {
            if (random.nextInt(4) != 0) continue;
            BlockPos neighborPos = fuelPos.relative(direction);
            if (!level.hasChunkAt(neighborPos)) continue;
            FluidState neighbor = level.getFluidState(neighborPos);
            if (!neighbor.isSource()) continue;
            LiquidFireBlock fire = fireFor(neighbor.getType());
            if (fire != null) fire.ignite(level, neighborPos);
        }
    }

    /** Any solid combustible reached by custom fire gets vanilla fire, never this custom block. */
    private static void igniteVanillaFlamesOnCombustibles(ServerLevel level, BlockPos flamePos, RandomSource random) {
        for (Direction direction : Direction.values()) {
            BlockPos combustiblePos = flamePos.relative(direction);
            if (!level.hasChunkAt(combustiblePos)) continue;
            BlockState combustible = level.getBlockState(combustiblePos);
            if (!combustible.ignitedByLava() || random.nextInt(5) != 0) continue;

            BlockPos vanillaFirePos = combustiblePos.above();
            if (!level.hasChunkAt(vanillaFirePos) || !level.getBlockState(vanillaFirePos).isAir()) continue;
            if (!BaseFireBlock.canBePlacedAt(level, vanillaFirePos, Direction.UP)) continue;
            level.setBlock(vanillaFirePos, BaseFireBlock.getState(level, vanillaFirePos), Block.UPDATE_ALL);
        }
    }

    private static LiquidFireBlock fireFor(Fluid fluid) {
        if (fluid == ModFluids.ETHANOL.get()) return ModBlocks.RECTIFICATE_FIRE.get();
        if (fluid == ModFluids.FORMALDEHYDE.get()) return ModBlocks.FORMALDEHYDE_FIRE.get();
        return null;
    }
}
