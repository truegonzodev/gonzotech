package com.gonzotech.radiation;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Five-second, server-wide trace of the radiation system's actual transfers. */
public final class RadiationTrace {

    public static final int DURATION_TICKS = 5 * 20;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        private final ServerPlayer viewer;
        private final MinecraftServer server;
        private final long startedAtTick;
        private final long endsAtTick;
        private long eventLines;

        private Session(ServerPlayer viewer, MinecraftServer server, long startedAtTick) {
            this.viewer = viewer;
            this.server = server;
            this.startedAtTick = startedAtTick;
            this.endsAtTick = startedAtTick + DURATION_TICKS;
        }
    }

    private RadiationTrace() {
    }

    /** Start or restart the caller's exact 100-server-tick trace window. */
    public static void start(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        long now = server.getTickCount();
        Session previous = SESSIONS.remove(player.getUUID());
        Session session = new Session(player, server, now);
        SESSIONS.put(player.getUUID(), session);

        String action = previous == null ? "запущена" : "перезапущена";
        player.sendSystemMessage(Component.literal(
                "[GonzoTech] Трассировка радиации " + action + " на 5 секунд: все измерения сервера. "
                        + "Полный поток отправляется в этот чат и журнал сервера.")
                .withStyle(ChatFormatting.AQUA));
        LOGGER.info("[RadiationTrace] Игрок {} начал трассировку на 100 тиков (server tick {}, все измерения)",
                player.getGameProfile().getName(), now);
    }

    /** True while at least one caller is observing radiation on this server. */
    public static boolean isActive(ServerLevel level) {
        long now = level.getServer().getTickCount();
        for (Session session : SESSIONS.values()) {
            if (session.server == level.getServer() && now < session.endsAtTick && !session.viewer.isRemoved()) {
                return true;
            }
        }
        return false;
    }

    /** Record one flow in every active viewer's chat and the server log. */
    public static void log(ServerLevel level, @Nullable BlockPos pos, String flow, String details) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = level.getServer();
        long now = server.getTickCount();
        String dimension = level.dimension().location().toString();
        String location = pos == null ? "" : " @" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        boolean delivered = false;

        for (Session session : SESSIONS.values()) {
            if (session.server != server || now >= session.endsAtTick || session.viewer.isRemoved()) continue;
            double elapsedSeconds = Math.max(0L, now - session.startedAtTick) / 20.0D;
            String line = String.format(Locale.ROOT, "[RadTrace +%.2fs][%s][%s]%s %s",
                    elapsedSeconds, dimension, flow, location, details);
            session.viewer.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY));
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
        if (!isActive(level)) return;
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
                    "[GonzoTech] Трассировка завершена: 5 секунд, событийных строк в чате/журнале: "
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
