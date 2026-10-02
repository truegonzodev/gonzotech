package com.gonzotech.core.item;

import java.util.Set;

/**
 * Чистая логика щипцов (0.3.79) — БЕЗ зависимостей от Minecraft (компилируется
 * и тестируется вне игры, audit/tongs_ladle_test.py).
 *
 * <p>Щипцы — предмет-контейнер на 64 единицы ОДНОГО вида. Носитель снижает
 * дозу содержимого: −40 % радиоактивности, −80 % токсичности
 * ({@link CarrierItem#RADIOACTIVITY_FACTOR}, автор 01.10.2026 — вместо старых
 * −60/−70/−90 из EPOCH3-BASE §2.8).
 *
 * <p>Запрет автора: щипцы НЕ берут ртуть и цезий ни в каком виде, кроме руды,
 * поллуцита и киновари (те идут в ковш).
 *
 * <p>Правило носителей (автор, 02.10.2026): механика — как у ванильного
 * мешочка/связки; единственный запрет на вложение — СОБСТВЕННЫЕ носители
 * (щипцы/ковш) не упаковываются друг в друга; шалкеры/связки — обычные предметы.
 */
public final class TongsLogic {

    /** Вместимость щипцов: 64 единицы одного вида (автор, EPOCH3-BASE §2.8). */
    public static final int CAPACITY = 64;

    /** Ртуть/цезий в «чистом» виде: только в ковш (щипцы не берут). */
    private static final Set<String> MERCURY_CESIUM = Set.of(
            "mercury_ingot", "mercury_nugget", "mercury_dust",
            "cesium_ingot", "cesium_nugget", "cesium_dust");

    /** Носители в носители не кладутся (иначе бездонная рекурсия). */
    private static final Set<String> CARRIERS = Set.of("tongs", "ladle");

    private TongsLogic() {
    }

    /** Ртуть/цезий «чистого» вида? (их щипцы НЕ берут — это профиль ковша). */
    public static boolean isMercuryCesium(String itemIdPath) {
        return MERCURY_CESIUM.contains(itemIdPath);
    }

    /** Мой ли это носитель (щипцы/ковш)? Такие предметы не гнездятся. */
    public static boolean isCarrier(String itemIdPath) {
        return CARRIERS.contains(itemIdPath);
    }

    /** Может ли щипец принять предмет с таким путём id. */
    public static boolean canPick(String itemIdPath) {
        return !MERCURY_CESIUM.contains(itemIdPath) && !CARRIERS.contains(itemIdPath);
    }

    /**
     * Сколько единиц {@code incoming} влезает в щипцы с текущим содержимым:
     * пустые принимают любой разрешённый вид (до 64), тот же вид — доCapacity,
     * другой вид (или запрещённый) — ноль.
     */
    public static int roomFor(String currentPath, int currentCount,
                              String incomingPath, int incomingCount) {
        if (incomingCount <= 0 || !canPick(incomingPath)) return 0;
        if (currentCount <= 0) return Math.min(incomingCount, CAPACITY);
        if (!currentPath.equals(incomingPath)) return 0;
        return Math.min(incomingCount, CAPACITY - currentCount);
    }
}
