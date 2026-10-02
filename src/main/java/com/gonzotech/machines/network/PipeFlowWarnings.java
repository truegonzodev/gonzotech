package com.gonzotech.machines.network;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * «Горячие» трубы (0.3.92, автор 04.10.2026): игрок должен ВИДЕТЬ, что блок
 * делает маршрутизацию накладной — куб узлов 5×5×5 на всю систему означает
 * смерть TPS. Если через позицию трубы/узла за итерацию раскладки проходит
 * ≥ {@link #HOT_LANE_THRESHOLD} дорожек, позиция подсвечивается красной пылью
 * (язык радиационной подсветки, но красным) в течение минуты после последнего
 * горячего прохода.
 *
 * <p>Серверная static-карта: пишется только из {@code PipeRouting.distributeLanes},
 * чистится в методе записи и в тике (просроченные метки). Частиц — не более
 * {@link #MAX_MARKS_PER_TICK} позиций за эмиссию, раз в 10 тиков.</p>
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class PipeFlowWarnings {

    /** Дорожек через одну позицию, при которых блок помечается «горячим» (автор: >400). */
    public static final int HOT_LANE_THRESHOLD = 400;
    /** Сколько тиков позиция остаётся горячей после последнего горячего прохода. */
    public static final long HOLD_TICKS = 60;
    /** Позиций-подсветок за одну эмиссию (граница пакетов). */
    public static final int MAX_MARKS_PER_TICK = 64;

    /** levelKey (dimension) → (posKey → тик-истечения). */
    private static final Map<String, Map<Long, Long>> HOT = new ConcurrentHashMap<>();

    private PipeFlowWarnings() {
    }

    /** Пометить позицию горячей до {@code untilTick} (пишется только маршрутизатором). */
    public static void mark(ServerLevel level, BlockPos pos, long untilTick) {
        HOT.computeIfAbsent(level.dimension().location().toString(), k -> new ConcurrentHashMap<>())
            .put(pos.asLong(), untilTick);
    }

    /** Только сервер: раз в 10 тиков — красная пыль над живыми горячими позициями. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel server)) return;
        if (server.getGameTime() % 10L != 0L) return;
        Map<Long, Long> hot = HOT.get(server.dimension().location().toString());
        if (hot == null || hot.isEmpty()) return;

        long now = server.getGameTime();
        List<Long> alive = new ArrayList<>();
        for (Iterator<Map.Entry<Long, Long>> it = hot.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
            } else {
                alive.add(entry.getKey());
            }
        }

        // Тик очистки — тоже запись: просроченные метки удалены выше.
        int sent = 0;
        for (Long key : alive) {
            if (sent >= MAX_MARKS_PER_TICK) break;
            BlockPos pos = BlockPos.of(key);
            server.sendParticles(new DustParticleOptions(0xCC2222, 1.0F),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                2, 0.25, 0.25, 0.25, 0.0);
            sent++;
        }
    }
}
