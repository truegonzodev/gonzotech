package com.gonzotech.core.event;

import com.gonzotech.core.block.IndustrialPrimedTnt;
import com.gonzotech.core.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Delayed, server-synchronized smoke and block-sourced blast dust. */
public final class ExplosiveEffects {
    public static final int GUNPOWDER_SMOKE_MIN = 20;
    public static final int GUNPOWDER_SMOKE_MAX = 40;
    public static final int GUNPOWDER_SMOKE_MAX_DELAY = 2;
    public static final int INDUSTRIAL_DUST_MIN_DELAY = 2;
    public static final int INDUSTRIAL_DUST_MAX_DELAY = 5;

    private static final int MAX_INDUSTRIAL_DUST_PARTICLES = 128;
    private static final Map<ServerLevel, List<ScheduledParticle>> PENDING = new WeakHashMap<>();
    private static final ThreadLocal<Boolean> REDIRECTING_INDUSTRIAL_EXPLOSION = new ThreadLocal<>();

    private ExplosiveEffects() {
    }

    /** Schedule 20–40 vanilla large-smoke puffs through the powder blast volume. */
    public static void scheduleGunpowderSmoke(ServerLevel level, Vec3 center, double radius) {
        var random = level.random;
        int count = GUNPOWDER_SMOKE_MIN
            + random.nextInt(GUNPOWDER_SMOKE_MAX - GUNPOWDER_SMOKE_MIN + 1);
        for (int i = 0; i < count; i++) {
            Vec3 point = center.add(randomPointInSphere(random, radius))
                .add(randomPointInSphere(random, 0.5D));
            int delay = random.nextInt(GUNPOWDER_SMOKE_MAX_DELAY + 1);
            double vx = (random.nextDouble() - 0.5D) * 0.025D;
            double vy = 0.025D + random.nextDouble() * 0.055D;
            double vz = (random.nextDouble() - 0.5D) * 0.025D;
            schedule(level, delay, ParticleTypes.LARGE_SMOKE,
                point.x, point.y, point.z, vx, vy, vz, null);
        }
    }

    /**
     * PrimedTnt's vanilla explode() is private. Redirect only industrial TNT's
     * cancellable start event so the normal blast calculation uses our scaled
     * large-particle emitter instead of adding a second, unscaled vanilla one.
     */
    @SubscribeEvent
    public static void onExplosionStart(ExplosionEvent.Start event) {
        if (Boolean.TRUE.equals(REDIRECTING_INDUSTRIAL_EXPLOSION.get())
            || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Explosion explosion = event.getExplosion();
        if (!(explosion.getDirectSourceEntity() instanceof IndustrialPrimedTnt primedTnt)) {
            return;
        }

        Vec3 center = explosion.center();
        float power = explosion.radius();
        event.setCanceled(true);
        REDIRECTING_INDUSTRIAL_EXPLOSION.set(true);
        try {
            level.explode(primedTnt, null, null,
                center.x, center.y, center.z, power, false, Level.ExplosionInteraction.TNT,
                ParticleTypes.EXPLOSION, ModParticles.INDUSTRIAL_TNT_EXPLOSION_EMITTER.get(),
                SoundEvents.GENERIC_EXPLODE);
        } finally {
            REDIRECTING_INDUSTRIAL_EXPLOSION.remove();
        }
    }

    /** Build dust only from blocks the industrial explosion is actually about to remove. */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)
            || !(event.getExplosion().getDirectSourceEntity() instanceof IndustrialPrimedTnt)) {
            return;
        }

        List<BlockPos> affectedBlocks = event.getAffectedBlocks();
        if (affectedBlocks.isEmpty()) {
            return; // an air-only blast has no block debris and raises no dust
        }

        List<BlockPos> candidates = new ArrayList<>(affectedBlocks.size());
        for (BlockPos pos : affectedBlocks) {
            candidates.add(pos.immutable());
        }

        var random = level.random;
        int count = Math.min(MAX_INDUSTRIAL_DUST_PARTICLES, candidates.size());
        for (int i = 0; i < count; i++) {
            int selected = i + random.nextInt(candidates.size() - i);
            Collections.swap(candidates, i, selected);
            BlockPos block = candidates.get(i);

            // Particles begin inside the volume of a block hit by the blast.
            // Once it is removed, they drift down through the resulting crater/cavity.
            double x = block.getX() + 0.15D + random.nextDouble() * 0.70D;
            double y = block.getY() + 0.15D + random.nextDouble() * 0.70D;
            double z = block.getZ() + 0.15D + random.nextDouble() * 0.70D;
            double vx = (random.nextDouble() - 0.5D) * 0.018D;
            double vy = -0.008D - random.nextDouble() * 0.018D;
            double vz = (random.nextDouble() - 0.5D) * 0.018D;
            int delay = INDUSTRIAL_DUST_MIN_DELAY
                + random.nextInt(INDUSTRIAL_DUST_MAX_DELAY - INDUSTRIAL_DUST_MIN_DELAY + 1);
            schedule(level, delay, ModParticles.DUST.get(), x, y, z, vx, vy, vz, block);
        }
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<ScheduledParticle> particles = PENDING.get(level);
        if (particles == null || particles.isEmpty()) {
            return;
        }

        Iterator<ScheduledParticle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            ScheduledParticle particle = iterator.next();
            if (particle.ticksRemaining > 0) {
                particle.ticksRemaining--;
                continue;
            }

            BlockPos position = BlockPos.containing(particle.x, particle.y, particle.z);
            if (level.hasChunkAt(position)
                && (particle.mustBeAir == null || level.getBlockState(particle.mustBeAir).isAir())) {
                // count=0 means one particle at the exact position; the three
                // deltas become its initial velocity in the client provider.
                level.sendParticles(particle.type, particle.x, particle.y, particle.z,
                    0, particle.vx, particle.vy, particle.vz, 0.0D);
            }
            iterator.remove();
        }

        if (particles.isEmpty()) {
            PENDING.remove(level);
        }
    }

    private static void schedule(ServerLevel level, int delay, ParticleOptions type,
                                 double x, double y, double z,
                                 double vx, double vy, double vz, BlockPos mustBeAir) {
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(
            new ScheduledParticle(delay, type, x, y, z, vx, vy, vz,
                mustBeAir == null ? null : mustBeAir.immutable()));
    }

    private static Vec3 randomPointInSphere(net.minecraft.util.RandomSource random, double radius) {
        double vertical = random.nextDouble() * 2.0D - 1.0D;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = radius * Math.cbrt(random.nextDouble());
        double horizontal = distance * Math.sqrt(1.0D - vertical * vertical);
        return new Vec3(horizontal * Math.cos(angle), distance * vertical,
            horizontal * Math.sin(angle));
    }

    private static final class ScheduledParticle {
        private int ticksRemaining;
        private final ParticleOptions type;
        private final double x;
        private final double y;
        private final double z;
        private final double vx;
        private final double vy;
        private final double vz;
        private final BlockPos mustBeAir;

        private ScheduledParticle(int ticksRemaining, ParticleOptions type,
                                  double x, double y, double z,
                                  double vx, double vy, double vz, BlockPos mustBeAir) {
            this.ticksRemaining = ticksRemaining;
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.mustBeAir = mustBeAir;
        }
    }
}
