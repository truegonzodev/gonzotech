package com.gonzotech.sunevent;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import org.slf4j.Logger;

/**
 * Суневеты фаза 4 — отладочные счётчики монстрового спавн-окна.
 *
 * <p>Половина конвейера «уже работает»: {@code MonsterSunEventSpawnMixin}
 * пропускает дневные проверки света ({@code SpawnPlacements.checkSpawnRules}),
 * но этот гейт стоит ДО noCollision/финальных проверок 1.21.4 — то есть
 * «пропущено правилами» ещё не значит «монстр создан». Вторая половина —
 * {@code ServerLevelMonsterSpawnCensusMixin}: считает реально добавленных в мир
 * монстров с раскладкой «видит небо / пещера» (в окне утром дня E горения нет,
 * так что созданные на поверхности обязаны оставаться видимыми).
 *
 * <p>Троттл — игровая минута (1200 тиков) по {@code gameTime}, как раньше.
 */
public final class SunEventSpawnDebug {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long FLUSH_TICKS = 1200L;

    private static long lastFlushGameTime = Long.MIN_VALUE;
    /** Вызовов checkSpawnRules (NATURAL+MONSTER) в окне за период. */
    private static int attempts;
    /** Пропущено гейтом на позициях, видящих небо (поверхность). */
    private static int permitsSky;
    /** Пропущено гейтом на закрытых позициях (пещеры/тень). */
    private static int permitsCave;
    /** Реально созданных, видят небо. */
    private static final Map<String, Integer> spawnedSky = new LinkedHashMap<>();
    /** Реально созданных, под землёй / в тени навеса. */
    private static final Map<String, Integer> spawnedCave = new LinkedHashMap<>();
    private static int spawnedSkyTotal;
    private static int spawnedCaveTotal;
    /** Одноразовый баннер об открытии окна. */
    private static boolean windowAnnounced;

    private SunEventSpawnDebug() {
    }

    /** Пульс из миксина-гейта: считает попытку и раз в игровую минуту сливает агрегат. */
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
    public static void recordPermit(boolean sky) {
        if (sky) {
            permitsSky++;
        } else {
            permitsCave++;
        }
    }

    /** В мир реально добавлен монстр (любой NATURAL-спавн окна; конверсии тоже попадут). */
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

    private static void flush(ServerLevel level) {
        lastFlushGameTime = level.getGameTime();
        if (attempts == 0 && spawnedSkyTotal == 0 && spawnedCaveTotal == 0) {
            return;
        }
        LOGGER.info(
            "[Gonzo Tech] Суневет: окно активно (время {}): проверок за минуту {},"
                + " пропущено небо {} / тень {},"
                + " создано — на поверхности {} {}, под землёй {} {}",
            level.getDayTime() % 24000L, attempts, permitsSky, permitsCave,
            spawnedSkyTotal, spawnedSky, spawnedCaveTotal, spawnedCave);
        attempts = 0;
        permitsSky = 0;
        permitsCave = 0;
        spawnedSky.clear();
        spawnedCave.clear();
        spawnedSkyTotal = 0;
        spawnedCaveTotal = 0;
    }

    private static String typeId(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }
}
