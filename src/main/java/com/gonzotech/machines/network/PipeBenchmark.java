package com.gonzotech.machines.network;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread, bounded aggregate samples. No per-route strings or world scans. */
public final class PipeBenchmark {
    public enum Metric {
        REQUESTS, PLAN_HITS, RESTARTS, EXPANDED, STATE_READS, INVALIDATIONS,
        LOCAL_LEAVES, PRECISE_SKIPS, ROUTE_KEEPS, WELD_BUILDS, WELD_HITS, WELD_STEPS,
        REFRESH_PROBES, PLANS_READY, NODE_ITEMS, ITEM_CALLS, FILTER_CALLS, RESOURCE_CALLS,
        SEARCH_NS, ITEM_NS, FILTER_NS, RESOURCE_NS
    }
    private static final int WINDOW = 200;
    private static final long[] COUNTERS = new long[Metric.values().length];
    private static final long[] TICKS = new long[WINDOW];
    private static final Map<String, Long> RESOURCE_CHARGES = new LinkedHashMap<>();
    private static String session = UUID.randomUUID().toString();
    private static long started, wallStart, gcTime, gcCount, lifetimeTicks;
    private static int size, pendingMax;
    private PipeBenchmark() {}
    public static String identity() { return " snapshot=" + PipeExperiment.SNAPSHOT + " session=" + session; }
    public static void add(Metric metric, long amount) { if (PipeExperiment.PROFILE) COUNTERS[metric.ordinal()] += amount; }
    public static long clock() { return PipeExperiment.PROFILE ? System.nanoTime() : 0; }
    public static void elapsed(Metric metric, long start) { if (PipeExperiment.PROFILE) add(metric, System.nanoTime() - start); }
    public static void resource(String channel, long charged) {
        if (PipeExperiment.PROFILE) RESOURCE_CHARGES.merge(channel, charged, Long::sum);
    }
    public static void beginTick() {
        if (!PipeExperiment.PROFILE) return;
        started = System.nanoTime();
        if (size == 0) { wallStart = System.currentTimeMillis(); gcTime = gc(false); gcCount = gc(true); }
    }
    public static void endTick(int pending) {
        if (!PipeExperiment.PROFILE) return;
        TICKS[size++] = System.nanoTime() - started; lifetimeTicks++;
        pendingMax = Math.max(pendingMax, pending);
    }
    public static boolean ready() { return PipeExperiment.PROFILE && size == WINDOW; }
    /** Called after the tick sample; logging overhead is not part of tickSpanUs. */
    public static String report(String cacheStats) {
        if (!PipeExperiment.PROFILE || size == 0) return null;
        long[] sorted = Arrays.copyOf(TICKS, size); Arrays.sort(sorted);
        long sum = 0; for (long t : sorted) sum += t;
        StringBuilder s = new StringBuilder("[GONZOTECH PIPE-BENCH] schema=1").append(identity())
            .append(" variant=").append(PipeExperiment.VARIANT).append(" ticks=").append(size)
            .append(" totalTicks=").append(lifetimeTicks).append(" wallMs=").append(System.currentTimeMillis()-wallStart)
            .append(" tickSpanMeanUs=").append(sum / size / 1000)
            .append(" tickSpanP50Us=").append(sorted[(size-1)/2]/1000)
            .append(" tickSpanP95Us=").append(sorted[(int)Math.ceil(size*.95)-1]/1000)
            .append(" tickSpanMaxUs=").append(sorted[size-1]/1000)
            .append(" pendingMax=").append(pendingMax).append(" gcCount=").append(gc(true)-gcCount)
            .append(" gcMs=").append(gc(false)-gcTime)
            .append(" heapUsedBytes=").append(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
        for (Metric m : Metric.values()) s.append(' ').append(m.name()).append('=').append(COUNTERS[m.ordinal()]);
        s.append(" resourceChargeMilli=").append(RESOURCE_CHARGES).append(' ').append(cacheStats);
        Arrays.fill(COUNTERS, 0); RESOURCE_CHARGES.clear(); size = 0; pendingMax = 0;
        return s.toString();
    }
    private static long gc(boolean count) {
        long sum = 0;
        for (var bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            long n = count ? bean.getCollectionCount() : bean.getCollectionTime(); if (n > 0) sum += n;
        }
        return sum;
    }
    public static void reset() {
        Arrays.fill(COUNTERS,0); RESOURCE_CHARGES.clear(); size=0; pendingMax=0; lifetimeTicks=0;
        session=UUID.randomUUID().toString();
    }
}
