package com.gonzotech.core.fluid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/** Bounded live-particle accounting, independent of Minecraft's rendering classes. */
public final class FoamPopulation {
    public static final int MAX_LIVE = 512;
    public static final int MAX_BIRTHS_PER_TICK = 64;
    public static final float MIN_SCALE = 0.8F;
    public static final float MAX_SCALE = 2.0F;

    public interface Life {
        boolean alive();
        int lifetime();
        void remove();
    }
    @FunctionalInterface
    public interface Emitter {
        Life spawn(FoamSurface.Pos cell, int kind, float scale);
    }
    private record Mote(FoamSurface.Pos origin, int kind, Life life, long deadline) {}
    private final List<Mote> live = new ArrayList<>();
    private long tick;

    public int size() { return live.size(); }
    public void clear() {
        for (Mote mote : live) mote.life.remove();
        live.clear();
        tick = 0;
    }

    /** Proportional cap, rounded by largest remainder: never grants +7 to each scan/chunk. */
    public static int[] budgets(FoamSurface.Snapshot surface, double density) {
        int n = surface.pools().size();
        int[] result = new int[n];
        long total = 0;
        for (var pool : surface.pools()) total += FoamSurface.target(pool.cells().size());
        if (total == 0) return result;
        int allowance = (int)Math.min(MAX_LIVE, Math.round(total * Math.clamp(density, 0.0, 1.0)));
        double[] fractions = new double[n];
        var order = new ArrayList<Integer>(n);
        int used = 0;
        for (int i = 0; i < n; i++) {
            double share = FoamSurface.target(surface.pools().get(i).cells().size()) * (double)allowance / total;
            result[i] = (int)Math.floor(share);
            used += result[i];
            fractions[i] = share - result[i];
            order.add(i);
        }
        order.sort(Comparator.<Integer>comparingDouble(i -> fractions[i]).reversed());
        for (int i = 0; used < allowance; i++, used++) result[order.get(i)]++;
        return result;
    }

    /** The engine owns motion/lifetime. Only replenish real vacancies, with a gradual warm-up. */
    public void tick(FoamSurface.Snapshot surface, int[] budgets, RandomGenerator random, Emitter emitter) {
        tick++;
        live.removeIf(mote -> {
            if (!mote.life.alive()) return true;
            // ParticleEngine.clear/eviction need not mark particles dead. Bound retained handles
            // by their actual original lifetime too, without extending or shortening normal dust.
            if (tick > mote.deadline) { mote.life.remove(); return true; }
            return false;
        });
        int n = surface.pools().size();
        if (n == 0) return;
        int[] counts = new int[n];
        for (Mote mote : live) {
            Integer index = surface.membership().get(mote.origin);
            if (index != null && surface.pools().get(index).kind() == mote.kind) counts[index]++;
        }
        // Re-map by birthplace rather than group ID: split/merge cannot double-count old foam.
        int first = random.nextInt(n), attempts = 0;
        for (int step = 0; step < n && live.size() < MAX_LIVE; step++) {
            int index = (first + step) % n;
            var pool = surface.pools().get(index);
            int missing = Math.max(0, budgets[index] - counts[index]);
            int births = Math.min(missing, Math.max(1, (budgets[index] + 11) / 12));
            for (int i = 0; i < births && live.size() < MAX_LIVE; i++) {
                if (attempts++ >= MAX_BIRTHS_PER_TICK) return;
                var cell = pool.cells().get(random.nextInt(pool.cells().size()));
                float scale = MIN_SCALE + random.nextFloat() * (MAX_SCALE - MIN_SCALE);
                Life particle = emitter.spawn(cell, pool.kind(), scale);
                if (particle != null && particle.alive()) {
                    live.add(new Mote(cell, pool.kind(), particle, tick + Math.max(1, particle.lifetime()) + 2L));
                }
            }
        }
    }
}
