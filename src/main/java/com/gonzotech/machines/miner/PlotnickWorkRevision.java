package com.gonzotech.machines.miner;

import com.gonzotech.GonzoTechMod;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Interest-only, dimension-scoped edit tokens. No chunk lookup, loading, BE or Level in values. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class PlotnickWorkRevision {
    private static final Map<Level, Map<Long, Object>> TOKENS = new WeakHashMap<>();
    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    public static Object at(Level level, int x, int z) {
        Map<Long, Object> chunks = TOKENS.computeIfAbsent(level, k -> new HashMap<>());
        // Dropping interests is safe: new tokens cannot compare equal to saved tokens (no ABA).
        if (chunks.size() > 8192) chunks.clear();
        return chunks.computeIfAbsent(key(x, z), k -> new Object());
    }
    public static void changed(Level level, int x, int z) {
        Map<Long, Object> chunks = TOKENS.get(level);
        if (chunks != null) chunks.computeIfPresent(key(x, z), (k, previous) -> new Object());
    }
    @SubscribeEvent public static void load(ChunkEvent.Load e) { chunk(e); }
    @SubscribeEvent public static void unload(ChunkEvent.Unload e) { chunk(e); }
    private static void chunk(ChunkEvent e) {
        if (e.getLevel() instanceof Level level && !level.isClientSide())
            changed(level, e.getChunk().getPos().x, e.getChunk().getPos().z);
    }
    @SubscribeEvent public static void levelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof Level level) TOKENS.remove(level);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { TOKENS.clear(); }
    @SubscribeEvent public static void data(OnDatapackSyncEvent e) { TOKENS.clear(); }
    private PlotnickWorkRevision() {}
}
