package com.gonzotech.core.fluid;

import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Independent, persistent burn and spread clocks for one liquid-fire overlay cell. */
public final class LiquidFireBlockEntity extends BlockEntity {
    private static final int MIN_BURN_TICKS = 8 * 20;
    private static final int MAX_BURN_TICKS = 17 * 20;
    private static final int BURN_MODE_TICKS = 11 * 20;
    private static final int MIN_SPREAD_TICKS = 8;
    private static final int MAX_SPREAD_TICKS = 32;

    private boolean initialized;
    private long activationAt;
    private long burnOutAt;
    private long nextSpreadAt;

    public LiquidFireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LIQUID_FIRE.get(), pos, state);
    }

    /** Starts a fresh flame instance; delayed ignition does not consume burn time while hidden. */
    void start(ServerLevel level, int ignitionDelay, RandomSource random) {
        long delay = Math.max(0, ignitionDelay);
        activationAt = level.getGameTime() + delay;
        burnOutAt = activationAt + sampleBurnTicks(random);
        nextSpreadAt = activationAt + sampleSpreadTicks(random);
        initialized = true;
        if (getBlockState().getBlock() instanceof LiquidFireBlock fire) {
            fire.setActive(level, worldPosition, delay == 0);
        }
        setChanged();
        scheduleNext(level);
    }

    void scheduledTick(ServerLevel level, BlockState state, RandomSource random) {
        if (!(state.getBlock() instanceof LiquidFireBlock fire)) return;
        if (!initialized) {
            start(level, 0, random);
            return;
        }

        if (!fire.matchesFuel(level.getFluidState(worldPosition.below()))
                || fire.touchesWater(level, worldPosition)) {
            level.removeBlock(worldPosition, false);
            return;
        }

        fire.synchronizeSurface(level, worldPosition);
        long now = level.getGameTime();
        if (now < activationAt) {
            fire.setActive(level, worldPosition, false);
            scheduleNext(level);
            return;
        }
        fire.setActive(level, worldPosition, true);

        if (level.isRainingAt(worldPosition) && random.nextInt(3) == 0) {
            level.removeBlock(worldPosition, false);
            return;
        }
        if (now >= burnOutAt) {
            fire.burnOut(level, worldPosition, random);
            return;
        }

        if (now >= nextSpreadAt) {
            if (level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
                fire.spread(level, worldPosition, random);
            }
            nextSpreadAt = now + sampleSpreadTicks(random);
            setChanged();
        }
        scheduleNext(level);
    }

    private void scheduleNext(ServerLevel level) {
        if (!(getBlockState().getBlock() instanceof LiquidFireBlock fire)) return;
        long now = level.getGameTime();
        long next = initialized
            ? (now < activationAt ? activationAt : Math.min(burnOutAt, nextSpreadAt))
            : now + 1;
        long delta = Math.max(1L, Math.min((long) Integer.MAX_VALUE, next - now));
        level.scheduleTick(worldPosition, fire, (int) delta);
    }

    /** Triangular 8–17 second lifetime with an 11-second mode and 12-second mean. */
    private static int sampleBurnTicks(RandomSource random) {
        double minimum = MIN_BURN_TICKS;
        double maximum = MAX_BURN_TICKS;
        double mode = BURN_MODE_TICKS;
        double value = random.nextDouble();
        double split = (mode - minimum) / (maximum - minimum);
        double sample = value < split
            ? minimum + Math.sqrt(value * (maximum - minimum) * (mode - minimum))
            : maximum - Math.sqrt((1.0D - value) * (maximum - minimum) * (maximum - mode));
        return (int) Math.max(MIN_BURN_TICKS, Math.min(MAX_BURN_TICKS, Math.round(sample)));
    }

    /** Uniform interval: 8–32 ticks, or 0.4–1.6 seconds at 20 TPS. */
    private static int sampleSpreadTicks(RandomSource random) {
        return MIN_SPREAD_TICKS + random.nextInt(MAX_SPREAD_TICKS - MIN_SPREAD_TICKS + 1);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && initialized) scheduleNext(server);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Initialized", initialized);
        tag.putLong("ActivationAt", activationAt);
        tag.putLong("BurnOutAt", burnOutAt);
        tag.putLong("NextSpreadAt", nextSpreadAt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        initialized = tag.getBoolean("Initialized");
        activationAt = tag.getLong("ActivationAt");
        burnOutAt = tag.getLong("BurnOutAt");
        nextSpreadAt = tag.getLong("NextSpreadAt");
    }
}
