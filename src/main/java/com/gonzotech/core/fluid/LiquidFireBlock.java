package com.gonzotech.core.fluid;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Separate surface-fire overlay for one burning ethanol/formaldehyde fluid cell.
 * The matching source or flowing-fluid block remains ordinary liquid until this
 * overlay's own persisted burn timer expires.
 */
public final class LiquidFireBlock extends FireBlock implements EntityBlock {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    /** 0 = source; 1..7 = flowing levels; 8 = full-height falling fluid. */
    public static final IntegerProperty SURFACE = IntegerProperty.create("surface", 0, 8);

    private static final double[] SURFACE_HEIGHTS = {
        0.875D, 0.71875D, 0.60625D, 0.5D, 0.3875D, 0.28125D, 0.16875D, 0.05625D, 1.0D
    };

    private final Supplier<? extends Fluid> fuel;
    private final int flameColor;

    public LiquidFireBlock(Supplier<? extends Fluid> fuel, int flameColor, Properties properties) {
        super(properties);
        this.fuel = fuel;
        this.flameColor = flameColor;
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false).setValue(SURFACE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE, SURFACE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiquidFireBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.LIQUID_FIRE.get()) return null;
        return (tickLevel, pos, blockState, blockEntity) -> {
            if (tickLevel instanceof ServerLevel server
                    && blockEntity instanceof LiquidFireBlockEntity fire) {
                fire.tickServer(server, blockState, server.random);
            }
        };
    }

    /** The visual flame must not block interaction with the liquid below it. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Place an active custom flame immediately or after the requested vanilla-fire delay. */
    public boolean ignite(ServerLevel level, BlockPos fluidPos, int delay, RandomSource random) {
        FluidState fuelState = level.getFluidState(fluidPos);
        if (!matchesFuel(fuelState) || !level.hasChunkAt(fluidPos.above())) return false;

        BlockPos firePos = fluidPos.above();
        BlockState existing = level.getBlockState(firePos);
        if (existing.getBlock() instanceof LiquidFireBlock) return false;
        if (!existing.isAir() && !(existing.getBlock() instanceof BaseFireBlock)) return false;

        int ignitionDelay = Math.max(0, delay);
        BlockState fireState = defaultBlockState()
            .setValue(SURFACE, surfaceIndex(fuelState))
            .setValue(ACTIVE, ignitionDelay == 0);
        if (!level.setBlock(firePos, fireState, Block.UPDATE_ALL)) return false;

        if (level.getBlockEntity(firePos) instanceof LiquidFireBlockEntity fire) {
            fire.start(level, ignitionDelay, random);
            return true;
        }

        // A flame without its timer must never become a permanent or destructive block.
        level.removeBlock(firePos, false);
        return false;
    }

    boolean matchesFuel(FluidState state) {
        if (state.isEmpty()) return false;
        Fluid fluid = state.getType();
        Fluid source = fuel.get();
        if (fluid == source) return true;
        if (source == ModFluids.ETHANOL.get()) return fluid == ModFluids.FLOWING_ETHANOL.get();
        if (source == ModFluids.FORMALDEHYDE.get()) return fluid == ModFluids.FLOWING_FORMALDEHYDE.get();
        return false;
    }

    /** Surface level used by the blockstate model selector. */
    static int surfaceIndex(FluidState state) {
        if (state.isEmpty() || state.isSource()) return 0;
        int amount = state.getAmount();
        if (amount >= 8) return 8; // A falling fluid state is full-height but is not a source.
        return Math.max(1, Math.min(7, 8 - amount));
    }

    static double surfaceHeight(FluidState state) {
        return SURFACE_HEIGHTS[surfaceIndex(state)];
    }

    void synchronizeSurface(ServerLevel level, BlockPos firePos) {
        FluidState fluid = level.getFluidState(firePos.below());
        if (!matchesFuel(fluid)) return;
        BlockState current = level.getBlockState(firePos);
        int surface = surfaceIndex(fluid);
        if (current.getBlock() == this && current.getValue(SURFACE) != surface) {
            level.setBlock(firePos, current.setValue(SURFACE, surface), Block.UPDATE_CLIENTS);
        }
    }

    void setActive(ServerLevel level, BlockPos firePos, boolean active) {
        BlockState current = level.getBlockState(firePos);
        if (current.getBlock() == this && current.getValue(ACTIVE) != active) {
            level.setBlock(firePos, current.setValue(ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return matchesFuel(level.getFluidState(pos.below()));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess,
                                     BlockPos pos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        FluidState fuelState = level.getFluidState(pos.below());
        if (!matchesFuel(fuelState)) return Blocks.AIR.defaultBlockState();
        // Do not delegate to FireBlock.updateShape: its vanilla support/schedule rules
        // are for an ordinary fire resting on a combustible solid, not a liquid overlay.
        return state.setValue(SURFACE, surfaceIndex(fuelState));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        // Each overlay is advanced by its BlockEntity ticker; vanilla FireBlock ticks are disabled.
    }

    /** Vanilla random ticks must not shorten or spread the independently timed liquid flame. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // The BlockEntity owns this cell's burn and propagation clocks.
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof LiquidFireBlockEntity fire) {
            fire.tickServer(level, state, random);
        } else {
            level.removeBlock(pos, false);
        }
    }

    boolean touchesWater(LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(net.minecraft.tags.FluidTags.WATER)) return true;
        }
        return false;
    }

    /** One successful liquid-cell ignition attempt per 8–32 ticks (0.4–1.6 s). */
    void spread(ServerLevel level, BlockPos firePos, RandomSource random) {
        spreadToOneLiquidCell(level, firePos, random);
        igniteVanillaFlamesOnCombustibles(level, firePos, random);
    }

    /**
     * An ordinary fire searches its surrounding 3×3×3 cube for these fuels.
     * At most one candidate is ignited on each vanilla-fire tick.
     */
    public static boolean igniteNearbyFuel(ServerLevel level, BlockPos firePos, RandomSource random) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) return false;

        List<BlockPos> candidates = new ArrayList<>(27);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos fuelPos = firePos.offset(dx, dy, dz);
                    if (!level.hasChunkAt(fuelPos)) continue;
                    FluidState fuelState = level.getFluidState(fuelPos);
                    LiquidFireBlock fire = fireFor(fuelState.getType());
                    if (fire == null) continue;

                    BlockPos overlayPos = fuelPos.above();
                    if (!level.hasChunkAt(overlayPos)) continue;
                    BlockState overlayState = level.getBlockState(overlayPos);
                    if (overlayState.getBlock() instanceof LiquidFireBlock) continue;
                    if (!overlayState.isAir() && !(overlayState.getBlock() instanceof BaseFireBlock)) continue;
                    candidates.add(fuelPos.immutable());
                }
            }
        }

        if (candidates.isEmpty()) return false;
        BlockPos target = candidates.get(random.nextInt(candidates.size()));
        LiquidFireBlock targetFire = fireFor(level.getFluidState(target).getType());
        return targetFire != null && targetFire.ignite(level, target, 0, random);
    }

    private static void spreadToOneLiquidCell(ServerLevel level, BlockPos firePos, RandomSource random) {
        BlockPos fuelPos = firePos.below();
        List<BlockPos> candidates = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            BlockPos neighborFuel = fuelPos.relative(direction);
            if (!level.hasChunkAt(neighborFuel)) continue;
            FluidState neighbor = level.getFluidState(neighborFuel);
            if (neighbor.isEmpty()) continue;

            LiquidFireBlock targetFire = fireFor(neighbor.getType());
            if (targetFire == null) continue;
            BlockPos targetFirePos = neighborFuel.above();
            if (!level.hasChunkAt(targetFirePos)) continue;
            BlockState targetState = level.getBlockState(targetFirePos);
            if (targetState.getBlock() instanceof LiquidFireBlock) continue;
            if (!targetState.isAir() && !(targetState.getBlock() instanceof BaseFireBlock)) continue;
            candidates.add(neighborFuel.immutable());
        }

        if (!candidates.isEmpty()) {
            BlockPos target = candidates.get(random.nextInt(candidates.size()));
            LiquidFireBlock targetFire = fireFor(level.getFluidState(target).getType());
            if (targetFire != null) targetFire.ignite(level, target, 0, random);
        }
    }

    /** Ordinary combustible blocks receive vanilla fire; the liquid overlay never replaces them. */
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

    /** Consume only this matching liquid cell, then make a low-probability follow-on ignition attempt. */
    void burnOut(ServerLevel level, BlockPos firePos, RandomSource random) {
        BlockPos fuelPos = firePos.below();
        FluidState fuelState = level.getFluidState(fuelPos);
        boolean stillFuel = matchesFuel(fuelState);

        level.removeBlock(firePos, false);
        if (stillFuel) {
            level.setBlock(fuelPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!stillFuel || random.nextInt(4) != 0) return;

        BlockPos supportPos = fuelPos.below();
        FluidState supportFluid = level.getFluidState(supportPos);
        LiquidFireBlock supportFire = fireFor(supportFluid.getType());
        if (!supportFluid.isEmpty() && supportFire != null) {
            supportFire.ignite(level, supportPos, 0, random);
            return;
        }

        BlockState support = level.getBlockState(supportPos);
        if (!support.ignitedByLava() || !level.getBlockState(fuelPos).isAir()) return;
        if (!BaseFireBlock.canBePlacedAt(level, fuelPos, Direction.UP)) return;
        level.setBlock(fuelPos, BaseFireBlock.getState(level, fuelPos), Block.UPDATE_ALL);
    }

    /** Sparks use the matching source/flowing cell's specified fluid-surface height. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE)) return;
        BlockPos fuelPos = pos.below();
        FluidState fluid = level.getFluidState(fuelPos);
        if (!matchesFuel(fluid)) return;

        double surfaceY = fuelPos.getY() + surfaceHeight(fluid);
        double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
        double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        double y = surfaceY + 0.05 + random.nextDouble() * 0.45;
        if (random.nextInt(3) == 0) {
            level.addParticle(new DustParticleOptions(flameColor, 0.9F), x, y, z,
                (random.nextDouble() - 0.5) * 0.015, 0.025 + random.nextDouble() * 0.025,
                (random.nextDouble() - 0.5) * 0.015);
        }
        if (random.nextInt(7) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.035, 0.0);
        }
    }

    private static LiquidFireBlock fireFor(Fluid fluid) {
        if (fluid == ModFluids.ETHANOL.get() || fluid == ModFluids.FLOWING_ETHANOL.get()) {
            return ModBlocks.RECTIFICATE_FIRE.get();
        }
        if (fluid == ModFluids.FORMALDEHYDE.get() || fluid == ModFluids.FLOWING_FORMALDEHYDE.get()) {
            return ModBlocks.FORMALDEHYDE_FIRE.get();
        }
        return null;
    }
}
