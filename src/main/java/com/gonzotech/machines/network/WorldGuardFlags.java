package com.gonzotech.machines.network;

import com.gonzotech.GonzoTechMod;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;

/** Server-thread-only optional integration. Null ONLY for genuinely absent WG;
 * installed/broken is false, never an actor/nearest-player fallback.
 * No block/flag decision cache: each attempted mutation sees current WG data.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class WorldGuardFlags {
    /** Caller label retained for compatibility; BOTH permissions are required for either action. */
    public enum Action { PLACE, BREAK }
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private static final class Session {
        BukkitWorldGuardAdapter adapter;
        BukkitWorldGuardAdapter.Failure discoveryFailure;
        final EnumSet<BukkitWorldGuardAdapter.FailureKind> warned = EnumSet.noneOf(BukkitWorldGuardAdapter.FailureKind.class);
        Session() {
            try { adapter = BukkitWorldGuardAdapter.discover(); }
            catch (BukkitWorldGuardAdapter.Failure failure) { discoveryFailure = failure; }
        }
        void warn(ServerLevel level, BukkitWorldGuardAdapter.Failure failure) {
            if (warned.add(failure.kind()))
                LOGGER.error("[GonzoTech/WG] Plotnick edits DENIED in {}: {}. Check enabled WE/WG and the hybrid world bridge; restart after plugin installation/API changes.",
                    level.dimension().location(), failure.getMessage(), failure);
        }
    }
    public static Boolean decision(ServerLevel level, BlockPos pos, Action action) {
        Session session = SESSIONS.computeIfAbsent(level.getServer(), server -> new Session());
        if (session.discoveryFailure != null) {
            session.warn(level, session.discoveryFailure);
            return false;
        }
        if (session.adapter == null) return null; // cached absence: no world/region/plugin scan per block
        try { return session.adapter.allows(level, pos.getX(), pos.getY(), pos.getZ()); }
        catch (BukkitWorldGuardAdapter.Failure failure) {
            session.warn(level, failure);
            return false;
        }
    }
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        // Discard any early bootstrap probe. First real edit detects the fully started plugin environment.
        SESSIONS.remove(event.getServer());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Session session = SESSIONS.get(level.getServer());
        if (session != null && session.adapter != null) session.adapter.unload(level);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SESSIONS.remove(event.getServer()); }
    private WorldGuardFlags() {}
}
