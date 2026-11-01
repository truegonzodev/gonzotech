package com.gonzotech.machines.miner;

import com.gonzotech.GonzoTechMod;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.util.IdentityHashMap;
import java.util.Map;

/** All dimensions share one server-thread FIFO; whole jobs, NOT a millisecond budget. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class PlotnickServerBudget {
    public static final int STARTS_PER_TICK = 2;
    public static final int CHECKS_PER_TICK = 64;
    private static final Logger LOGGER = LogUtils.getLogger();
    private record Work(ServerLevel level, Runnable run) {}
    private static final class State {
        final PlotnickFairQueue<Object, Work> queue = new PlotnickFairQueue<>();
        int tick = Integer.MIN_VALUE, started, checked;
        long totalStarted, totalChecked;
        boolean dispatching;
    }
    private static final Map<MinecraftServer, State> SERVERS = new IdentityHashMap<>();
    public record Snapshot(int tick, int started, int checked, int waiting, long totalStarted, long totalChecked) {}

    public static void enqueue(ServerLevel level, Object owner, Runnable work) {
        SERVERS.computeIfAbsent(level.getServer(), s -> new State()).queue.offer(owner, new Work(level, work));
    }
    public static boolean queued(ServerLevel level, Object owner) {
        State state = SERVERS.get(level.getServer());
        return state != null && state.queue.contains(owner);
    }
    public static void cancel(ServerLevel level, Object owner) {
        State state = SERVERS.get(level.getServer());
        if (state != null) state.queue.cancel(owner);
    }
    /** Called ONLY after live preflight, immediately before a whole calculation starts. */
    public static boolean start(ServerLevel level) {
        State state = SERVERS.get(level.getServer());
        if (state == null || !state.dispatching || state.started >= STARTS_PER_TICK) return false;
        state.started++;
        state.totalStarted++;
        return true;
    }
    public static Snapshot snapshot(MinecraftServer server) {
        State s = SERVERS.get(server);
        return s == null ? new Snapshot(Integer.MIN_VALUE, 0, 0, 0, 0, 0)
            : new Snapshot(s.tick, s.started, s.checked, s.queue.size(), s.totalStarted, s.totalChecked);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        State state = SERVERS.get(server);
        if (state == null || state.dispatching) return;
        if (state.tick != server.getTickCount()) {
            state.tick = server.getTickCount();
            state.started = state.checked = 0;
        }
        state.dispatching = true;
        try {
            while (state.started < STARTS_PER_TICK && state.checked < CHECKS_PER_TICK) {
                Work work = state.queue.poll();
                if (work == null) break;
                state.checked++;
                state.totalChecked++;
                work.run().run();
            }
        } finally {
            state.dispatching = false;
            if (state.checked > 0 && LOGGER.isDebugEnabled())
                LOGGER.debug("[PlotnickBudget] tick={} started={}/{} checked={}/{} waiting={} totalStarted={}",
                    state.tick, state.started, STARTS_PER_TICK, state.checked, CHECKS_PER_TICK,
                    state.queue.size(), state.totalStarted);
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = SERVERS.get(level.getServer());
        if (state != null) state.queue.cancelIf(work -> work.level() == level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }
    private PlotnickServerBudget() {}
}
