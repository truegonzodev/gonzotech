package com.gonzotech.sunevent;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

/**
 * Суневеты фаза 4 — отладочная сводка монстрового спавн-окна (v5).
 *
 * <p>РУТ-КЕЙС НАЙДЁН (воронки v4.1–v4.3, полигон): последний инстанс-уровень
 * ванили — {@code PathfinderMob.checkSpawnRules = getWalkTargetValue >= 0}; у
 * {@code Monster} walk-target = минус световая цена позиции → дневная открытая
 * поверхность отвергалась, слизень (наследует {@code Mob}, не
 * {@code PathfinderMob}) проходил один. Финальный гейт —
 * {@code PathfinderMobSunEventRulesMixin}: в монстровом окне отвечает true для
 * NATURAL-монстров (меняет только освещённые позиции — тёмные и в ваниле
 * проходили). Эта сводка оставлена на время приёмки: попытки/пропуски по типам,
 * валидные позиции (небо/тень), финализации по типам, созданные (небо/тень,
 * по типам). Троттл — игровая минута (1200 тиков) по {@code gameTime}.
 */
public final class SunEventSpawnDebug {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long FLUSH_TICKS = 1200L;

    private static long lastFlushGameTime = Long.MIN_VALUE;
    /** Вызовов checkSpawnRules (NATURAL+MONSTER) в окне за период. */
    private static int attempts;
    /** Пропущено гейтом: позиция попытки видит небо / закрыта. */
    private static int permitsSky;
    private static int permitsCave;
    /** Пропущено гейтом, по типам. */
    private static final Map<String, Integer> permitsByType = new LinkedHashMap<>();
    /** isValidSpawnPostitionForType=true (после AABB): небо/тень (без типов —
     * имя record-аксессора SpawnerData под Parchment переименовано — не тащим). */
    private static int validSky;
    private static int validCave;
    /** Дошло до finalizeSpawn, по типам [небо,тень]. */
    private static final Map<String, int[]> finalizeByType = new LinkedHashMap<>();
    /** Реально созданных, по типам (суммы небо|тень отдельно). */
    private static final Map<String, Integer> spawnedSky = new LinkedHashMap<>();
    private static final Map<String, Integer> spawnedCave = new LinkedHashMap<>();
    private static int spawnedSkyTotal;
    private static int spawnedCaveTotal;
    /** Одноразовый баннер об открытии окна. */
    private static boolean windowAnnounced;

    private SunEventSpawnDebug() {
    }

    /** Пульс из миксина-гейта: считает попытку и раз в игровую минуту сливает сводку. */
    public static void recordAttempt(ServerLevel level) {
        if (!windowAnnounced) {
            windowAnnounced = true;
            LOGGER.info("[Gonzo Tech] Суневет: монстровое спавн-окно открыто (время {})",
                level.getDayTime() % 24000L);
        }
        attempts++;
        if (lastFlushGameTime == Long.MIN_VALUE) {
            lastFlushGameTime = level.getGameTime();
        } else if (level.getGameTime() - lastFlushGameTime >= FLUSH_TICKS) {
            flush(level);
        }
    }

    /** Гейт пропустил проверку правил; sky = позиция попытки видит небо. */
    public static void recordPermit(EntityType<?> type, boolean sky) {
        permitsByType.merge(typeId(type), 1, Integer::sum);
        if (sky) {
            permitsSky++;
        } else {
            permitsCave++;
        }
    }

    /** isValidSpawnPostitionForType вернул true (позиция валидна: плейсмент + гейт + AABB). */
    public static void recordValid(boolean sky) {
        if (sky) {
            validSky++;
        } else {
            validCave++;
        }
    }

    /** До spawn'а дошла finalizeSpawn (последний шаг перед addFreshEntity). */
    public static void recordFinalize(EntityType<?> type, boolean sky) {
        increment(finalizeByType, type, sky);
    }

    /** В мир реально добавлен монстр (из ServerLevel.addFreshEntity). */
    public static void recordSpawn(EntityType<?> type, boolean canSeeSky) {
        (canSeeSky ? spawnedSky : spawnedCave).merge(typeId(type), 1, Integer::sum);
        if (canSeeSky) {
            spawnedSkyTotal++;
        } else {
            spawnedCaveTotal++;
        }
    }

    /** Сброс баннера вне окна — чтобы следующее открытие снова отметилось. */
    public static void onWindowClosed() {
        windowAnnounced = false;
    }

    private static void increment(Map<String, int[]> map, EntityType<?> type, boolean sky) {
        map.computeIfAbsent(typeId(type), k -> new int[2])[sky ? 0 : 1]++;
    }

    private static void flush(ServerLevel level) {
        lastFlushGameTime = level.getGameTime();
        if (attempts == 0 && permitsByType.isEmpty() && spawnedSkyTotal == 0 && spawnedCaveTotal == 0) {
            return;
        }
        LOGGER.info(
            "[Gonzo Tech] Суневет: окно активно (время {}), попыток {}, пропущено небо {} / тень {}"
                + "\n  пропущено по типам: {}"
                + "\n  валидно: небо {} / тень {}"
                + "\n  финализация [небо+тень]: {}"
                + "\n  создано: небо {} {}, тень {} {}",
            level.getDayTime() % 24000L, attempts, permitsSky, permitsCave,
            joinSingles(permitsByType),
            validSky, validCave,
            joinPairs(finalizeByType),
            spawnedSkyTotal, joinSingles(spawnedSky), spawnedCaveTotal, joinSingles(spawnedCave));
        attempts = 0;
        permitsSky = 0;
        permitsCave = 0;
        permitsByType.clear();
        validSky = 0;
        validCave = 0;
        finalizeByType.clear();
        spawnedSky.clear();
        spawnedCave.clear();
        spawnedSkyTotal = 0;
        spawnedCaveTotal = 0;
    }

    /** {zombie=5, spider=3} с укороченными id. */
    private static String joinSingles(Map<String, Integer> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(shortId(e.getKey())).append('=').append(e.getValue());
        }
        return sb.append('}').toString();
    }

    /** {zombie=5+3} — небо+тень. */
    private static String joinPairs(Map<String, int[]> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, int[]> e : map.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(shortId(e.getKey())).append('=').append(e.getValue()[0]).append('+').append(e.getValue()[1]);
        }
        return sb.append('}').toString();
    }

    private static String typeId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    private static String shortId(String full) {
        return full.replace("minecraft:", "");
    }
}
