package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Учёт фактического потока через провода — БЕЗ BlockEntity и БЕЗ NBT. Полностью
 * транзитный: живёт в памяти сервера один-два тика и обнуляется.
 * <p>
 * Провод пассивен и сам поток не хранит; вместо этого при каждом сливе
 * {@link PipeRouting} записывает сюда, сколько ресурса вышло из трубы в каждую из
 * 6 сторон за текущий тик. Так «средний» провод прямой линии показывает, сколько
 * ушло в один конец, а на встречных потоках — сразу оба конца (напр. восток 60,
 * запад 30 — ровно как хотел автор, без «направления через минус»).
 * <p>
 * Данные привязаны к тику: чтение старше одного тика считается устаревшим
 * (поток прекратился) и возвращает нули.
 */
public final class FlowTracker {

    private static final int DIRS = 6;
    private static final int TYPES = PipeType.values().length;
    private static final long[] EMPTY = new long[DIRS];
    private static final Map<Level, Holder> LEVELS = new IdentityHashMap<>();

    private FlowTracker() {
    }

    private static final class Holder {
        long tick = Long.MIN_VALUE;
        // Ключ — позиция трубы. Значение — [тип][сторона]: типы связки делят
        // позицию, но учитываются раздельно. long: GTU/GTH идут в милли.
        final Map<Long, long[][]> flow = new HashMap<>();
        // Кумулятивная потеря проноса от источника до этой трубы (по типам), milli.
        final Map<Long, long[]> loss = new HashMap<>();
    }

    /**
     * Записать, что из трубы типа {@code type} в позиции {@code pipe} вышло
     * {@code amount} единиц в сторону {@code out}. Поток учитывается ОТДЕЛЬНО по
     * типу — в связке несколько типов делят одну позицию. Единицы — базовые для
     * ресурса: GTU/GTH в милли, вода/пар в mB.
     */
    public static void record(Level level, BlockPos pipe, PipeType type, Direction out, long amount) {
        if (amount <= 0 || out == null) return;
        Holder h = LEVELS.computeIfAbsent(level, k -> new Holder());
        long t = level.getGameTime();
        if (h.tick != t) {
            h.flow.clear();
            // 0.3.73: loss тоже обязан сбрасываться здесь. Раньше чистился только
            // flow, а первый record() тика продвигал барьер h.tick — recordLoss
            // видел «тик актуален» и прибавлял потери к ВЧЕРАШНЕМУ значению:
            // «(+N)» на HUD ключа росло бесконечно (~+0.44 GTH за тик).
            h.loss.clear();
            h.tick = t;
        }
        long[][] byType = h.flow.computeIfAbsent(pipe.asLong(), k -> new long[TYPES][DIRS]);
        byType[type.ordinal()][out.get3DDataValue()] += amount;
    }

    /**
     * Записать кумулятивную потерю маршрута (milli) от источника до трубы
     * {@code pipe} типа {@code type} (0.3.60, подсказка «(+N)» на HUD ключа).
     * Берётся МАКСИМУМ по дорожкам тика (0.3.74): «(+N)» — потеря маршрута ДО
     * этой трубы, она не зависит ни от числа активных дорожек, ни от величины
     * потока; суммирование давало «(+3.96)» на девяти дорожках вместо «(+0.44)».
     */
    public static void recordLoss(Level level, BlockPos pipe, PipeType type, long lossMilli) {
        if (lossMilli <= 0) return;
        Holder h = LEVELS.computeIfAbsent(level, k -> new Holder());
        long t = level.getGameTime();
        if (h.tick != t) {
            h.flow.clear();
            h.loss.clear();
            h.tick = t;
        }
        long[] byType = h.loss.computeIfAbsent(pipe.asLong(), k -> new long[TYPES]);
        if (lossMilli > byType[type.ordinal()]) byType[type.ordinal()] = lossMilli;
    }

    /**
     * Кумулятивная потеря трубы типа {@code type} за последний актуальный тик
     * (milli); 0, если данных нет или они устарели.
     */
    public static long getLoss(Level level, BlockPos pipe, PipeType type) {
        Holder h = LEVELS.get(level);
        if (h == null) return 0;
        if (level.getGameTime() - h.tick > 1) return 0;
        long[] byType = h.loss.get(pipe.asLong());
        return byType == null ? 0 : byType[type.ordinal()];
    }

    /**
     * Поток через трубу типа {@code type} за последний актуальный тик: массив из
     * 6 значений, индексируемых {@link Direction#get3DDataValue()} — сколько вышло
     * в каждую сторону. Нули, если данных нет или они устарели.
     */
    public static long[] get(Level level, BlockPos pipe, PipeType type) {
        Holder h = LEVELS.get(level);
        if (h == null) return EMPTY;
        if (level.getGameTime() - h.tick > 1) return EMPTY;
        long[][] byType = h.flow.get(pipe.asLong());
        return byType == null ? EMPTY : byType[type.ordinal()];
    }

    /** Сброс при остановке сервера, чтобы не удерживать ссылки на уровни. */
    public static void clearAll() {
        LEVELS.clear();
    }
}
