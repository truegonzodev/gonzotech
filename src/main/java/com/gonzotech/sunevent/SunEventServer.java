package com.gonzotech.sunevent;

import com.gonzotech.chalkboard.network.NotesNetwork;
import com.gonzotech.chalkboard.notes.ScholarNoteFlags;
import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Драйвер суневетов (сервер, только Оверворлд): раз в новый ванильный день
 * докручивает счётчик, пересчитывает ближайший/последний багровые дни и
 * рассылает состояние клиентам. В багровый день одноразово развивает флаг
 * {@link ScholarNoteFlags#SUN_EVENT} всем онлайн-игрокам (стр. 35).
 *
 * <p>Правила мира (снег вместо дождя, монстры, горение в полдень, грозы) —
 * отдельные фазы; читают {@code data.nextEventDay}/{@code lastEventDay}
 * и {@code snowWindowVanillaDay(day)}.
 */
public final class SunEventServer {

    private SunEventServer() {
    }

    @SubscribeEvent
    public static void onLevelTickPost(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)
            || serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        long day = serverLevel.getDayCount();
        SunEventData data = SunEventNetwork.getData(serverLevel);
        if (day == data.lastVanillaDay) return;

        long delta = day - data.lastVanillaDay;
        if (delta <= 0) {
            // Откат времени (/time): просто синхронизируемся, счёт не откатываем.
            data.lastVanillaDay = day;
            data.setDirty();
            return;
        }

        data.suneventDays += delta;
        data.lastVanillaDay = day;
        boolean eventToday = SunEventData.isEventNumber(data.suneventDays);
        if (eventToday) {
            data.lastEventDay = day;
        }
        data.nextEventDay = day + (SunEventData.nextEventNumberFrom(data.suneventDays) - data.suneventDays);
        data.setDirty();

        SunEventNetwork.sendToAll(serverLevel);

        if (eventToday) {
            unlockSunEventFlag(serverLevel);
        }
    }

    /** Стр. 35 «Ослабевшее солнце»: флаг всем свидетелям наступившего дня. */
    private static void unlockSunEventFlag(ServerLevel overworld) {
        MinecraftServer server = overworld.getServer();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            PlayerChalkboardProgress progress = p.getData(ModAttachments.CHALKBOARD_PROGRESS);
            if (progress.unlockNoteFlag(ScholarNoteFlags.SUN_EVENT)) {
                p.setData(ModAttachments.CHALKBOARD_PROGRESS, progress);
                NotesNetwork.sendToPlayer(p);
            }
        }
    }

    /** При заходе: состояние суневетов + (бонус) персистентное состояние Солнца. */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel overworld = player.serverLevel();
        SunEventData data = SunEventNetwork.getData(overworld);
        com.gonzotech.space.SpaceSkyNetwork.currentSunState = data.sunState;
        PacketDistributor.sendToPlayer(
            player,
            new com.gonzotech.space.SpaceSkyNetwork.SunStatePayload(data.sunState.getSerializedName()));
        SunEventNetwork.sendToPlayer(player, data);
    }
}
