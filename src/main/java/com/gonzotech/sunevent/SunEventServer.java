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
        long time = serverLevel.getDayTime();
        long day = time / 24000L;
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

    /**
     * Окно «дождь = снег» прямо сейчас: Оверворлд, ванильный день ∈ {E−1, E, E+1}.
     * (Дождь ли — решает сам ванильный код укладкой; здесь только окно дня.)
     */
    public static boolean snowWindowDay(Level level) {
        if (!(level instanceof ServerLevel serverLevel)
            || serverLevel.dimension() != Level.OVERWORLD) {
            return false;
        }
        SunEventData data = SunEventNetwork.getData(serverLevel);
        return data.snowWindowVanillaDay(serverLevel.getDayTime() / 24000L);
    }

    /** Багровый день E прямо сейчас (для «грозы ×10» — только день E). */
    public static boolean eventDayNow(ServerLevel level) {
        SunEventData data = SunEventNetwork.getData(level);
        return level.getDayTime() / 24000L == data.nextEventDay;
    }

    /**
     * Монстровое «ночное» окно (автор, фаза 4): ночные настройки спавна
     * на <b>день E0, 0→14500</b> (багровый день до конца тления).
     *
     * <p>Автор 2026-09-19 уточнил: дни E−1 и E+1 — <b>ТОЛЬКО СНЕГ</b>, монстров
     * там не должно быть («мобы спавнятся и не горят днём в E−1 и E1 — но
     * хотя гореть им положено только в E0»). Старое окно E−1 22000 → E 14500
     * снято: заход E−1 снова обычная ночь (ванильный спавн), E+1 — ваниль.
     * Размерность проверяется снаружи (Оверворлд). Ночь за окном — ваниль.
     */
    public static boolean monsterNightNow(ServerLevel level) {
        SunEventData data = SunEventNetwork.getData(level);
        long dayTime = level.getDayTime();
        long day = dayTime / 24000L;
        long timeOfDay = dayTime % 24000L;
        return day == data.nextEventDay && timeOfDay < 14500L;
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
