package com.gonzotech.radiation;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Five-second trace of radiation transfers within the caller's 3×3 chunk area. */
public final class RadiationTrace {

    public static final int DURATION_TICKS = 5 * 20;
    public static final int CHUNK_RADIUS = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final ServerPlayer viewer;
        private final MinecraftServer server;
        private final ResourceLocation dimension;
        private final int centerChunkX;
        private final int centerChunkZ;
        private final long startedAtTick;
        private final long endsAtTick;
        private long eventLines;

        private Session(ServerPlayer viewer, MinecraftServer server, long startedAtTick,
                        int centerChunkX, int centerChunkZ) {
            this.viewer = viewer;
            this.server = server;
            this.dimension = viewer.serverLevel().dimension().location();
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.startedAtTick = startedAtTick;
            this.endsAtTick = startedAtTick + DURATION_TICKS;
        }

        private boolean includesChunk(int chunkX, int chunkZ) {
            return Math.abs((long) chunkX - centerChunkX) <= CHUNK_RADIUS
                    && Math.abs((long) chunkZ - centerChunkZ) <= CHUNK_RADIUS;
        }
    }

    private RadiationTrace() {
    }

    /** Start or restart the caller's exact 100-server-tick trace window. */
    public static void start(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        long now = server.getTickCount();
        ChunkPos center = new ChunkPos(player.blockPosition());
        Session previous = SESSIONS.remove(player.getUUID());
        Session session = new Session(player, server, now, center.x, center.z);
        SESSIONS.put(player.getUUID(), session);

        String action = previous == null ? "запущена" : "перезапущена";
        player.sendSystemMessage(Component.literal(
                "[GonzoTech] Трассировка радиации " + action + " на 5 секунд: область 3×3 чанка "
                        + "вокруг чанка [" + center.x + "," + center.z + "] в измерении "
                        + session.dimension + ". Полный поток отправляется в этот чат и журнал сервера.")
                .withStyle(ChatFormatting.AQUA));
        LOGGER.info("[RadiationTrace] Игрок {} начал трассировку на 100 тиков (server tick {}, "
                        + "измерение {}, центр чанка [{},{}], область 3×3)",
                player.getGameProfile().getName(), now, session.dimension, center.x, center.z);
    }

    /** True while a trace is active in this dimension (the event itself is still range-filtered). */
    public static boolean isActive(ServerLevel level) {
        long now = level.getServer().getTickCount();
        for (Session session : SESSIONS.values()) {
            if (matchesDimension(session, level, now)) return true;
        }
        return false;
    }

    /** True while a trace covers this position. */
    public static boolean isActive(ServerLevel level, BlockPos pos) {
        return isActiveChunk(level, pos.getX() >> 4, pos.getZ() >> 4);
    }

    /** True while a trace covers this chunk. */
    public static boolean isActiveChunk(ServerLevel level, int chunkX, int chunkZ) {
        long now = level.getServer().getTickCount();
        for (Session session : SESSIONS.values()) {
            if (matchesDimension(session, level, now) && session.includesChunk(chunkX, chunkZ)) return true;
        }
        return false;
    }

    /** A transfer is relevant when either its source or destination is in a traced chunk. */
    public static boolean isActiveChunkTransfer(ServerLevel level, int fromX, int fromZ, int toX, int toZ) {
        long now = level.getServer().getTickCount();
        for (Session session : SESSIONS.values()) {
            if (matchesDimension(session, level, now)
                    && (session.includesChunk(fromX, fromZ) || session.includesChunk(toX, toZ))) return true;
        }
        return false;
    }

    private static boolean matchesDimension(Session session, ServerLevel level, long now) {
        return session.server == level.getServer()
                && session.dimension.equals(level.dimension().location())
                && now < session.endsAtTick
                && !session.viewer.isRemoved();
    }

    /** Record a position-based flow only for viewers whose 3×3 area contains it. */
    public static void log(ServerLevel level, BlockPos pos, String flow, String details) {
        if (pos == null) return;
        logScoped(level, pos.getX() >> 4, pos.getZ() >> 4, false, 0, 0,
                " @" + pos.getX() + "," + pos.getY() + "," + pos.getZ(), flow, details, ChatFormatting.GRAY);
    }

    /** Record a highlighted position-based flow (used for source-to-chunk diagnostics). */
    public static void logHighlighted(ServerLevel level, BlockPos pos, String flow, String details) {
        if (pos == null) return;
        logScoped(level, pos.getX() >> 4, pos.getZ() >> 4, false, 0, 0,
                " @" + pos.getX() + "," + pos.getY() + "," + pos.getZ(), flow, details, ChatFormatting.GOLD);
    }

    /** Record a chunk-local event only for viewers whose 3×3 area contains that chunk. */
    public static void logChunk(ServerLevel level, int chunkX, int chunkZ, String flow, String details) {
        logScoped(level, chunkX, chunkZ, false, 0, 0, "", flow, details, ChatFormatting.GRAY);
    }

    /** Record an inter-chunk flow for viewers covering either endpoint. */
    public static void logChunkTransfer(ServerLevel level, int fromX, int fromZ, int toX, int toZ,
                                        String flow, String details) {
        logScoped(level, fromX, fromZ, true, toX, toZ, "", flow, details, ChatFormatting.GRAY);
    }

    private static void logScoped(ServerLevel level, int chunkX, int chunkZ,
                                  boolean hasSecondChunk, int secondChunkX, int secondChunkZ,
                                  String location, String flow, String details, ChatFormatting color) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();
        String dimension = level.dimension().location().toString();
        boolean delivered = false;

        for (Session session : SESSIONS.values()) {
            if (!matchesDimension(session, level, now)) continue;
            boolean inScope = session.includesChunk(chunkX, chunkZ)
                    || (hasSecondChunk && session.includesChunk(secondChunkX, secondChunkZ));
            if (!inScope) continue;

            double elapsedSeconds = Math.max(0L, now - session.startedAtTick) / 20.0D;
            String line = String.format(Locale.ROOT, "[RadTrace +%.2fs][%s][%s]%s %s",
                    elapsedSeconds, dimension, flow, location, details);
            session.viewer.sendSystemMessage(Component.literal(line).withStyle(color));
            session.eventLines++;
            delivered = true;
        }

        if (delivered) {
            LOGGER.info("[RadiationTrace][tick={}][{}]{} {} {}", now, dimension, location, flow, details);
        }
    }

    /** Report a direct change to a player's radiation scale (including treatment/admin changes). */
    public static void playerDoseChange(ServerPlayer player, int beforePermille, int afterPermille, String cause) {
        if (beforePermille == afterPermille) return;
        ServerLevel level = player.serverLevel();
        if (!isActive(level, player.blockPosition())) return;
        log(level, player.blockPosition(), "ШКАЛА ДОЗЫ ИГРОКА",
                "игрок=" + player.getGameProfile().getName()
                        + "; причина=" + cause
                        + "; " + beforePermille + "→" + afterPermille + " ‰"
                        + " (" + number(beforePermille / 10.0D) + "→" + number(afterPermille / 10.0D) + " %)");
    }

    /** Finish sessions after precisely 100 server ticks; called once per server tick. */
    public static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        Iterator<Map.Entry<UUID, Session>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next().getValue();
            if (session.server != server) continue;
            if (session.viewer.isRemoved()) {
                iterator.remove();
                continue;
            }
            if (now < session.endsAtTick) continue;

            iterator.remove();
            session.viewer.sendSystemMessage(Component.literal(
                    "[GonzoTech] Трассировка завершена: 5 секунд, область 3×3 чанка, событийных строк в чате/журнале: "
                            + session.eventLines + ".")
                    .withStyle(ChatFormatting.AQUA));
            LOGGER.info("[RadiationTrace] Игрок {} завершил трассировку: {} строк за 100 тиков",
                    session.viewer.getGameProfile().getName(), session.eventLines);
        }
    }

    /** End a caller's trace on logout so it cannot retain a player reference. */
    public static void stop(ServerPlayer player, String reason) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        LOGGER.info("[RadiationTrace] Трассировка игрока {} завершена раньше срока: {} ({} строк)",
                player.getGameProfile().getName(), reason, session.eventLines);
    }

    /** Clear transient sessions when a server stops. */
    public static void clearAll() {
        SESSIONS.clear();
    }

    public static String rate(double nztPerSecond) {
        return number(nztPerSecond) + " nZt/s";
    }

    public static String number(double value) {
        return String.format(Locale.ROOT, "%.6g", value);
    }
}
