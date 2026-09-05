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
    private static final int[] EMPTY = new int[DIRS];
    private static final Map<Level, Holder> LEVELS = new IdentityHashMap<>();

    private FlowTracker() {
    }

    private static final class Holder {
        long tick = Long.MIN_VALUE;
        // Ключ — позиция трубы. Значение — [тип][сторона]: типы связки делят
        // позицию, но учитываются раздельно.
        final Map<Long, int[][]> flow = new HashMap<>();
    }

    /**
     * Записать, что из трубы типа {@code type} в позиции {@code pipe} вышло
     * {@code amount} единиц в сторону {@code out}. Поток учитывается ОТДЕЛЬНО по
     * типу — в связке несколько типов делят одну позицию.
     */
    public static void record(Level level, BlockPos pipe, PipeType type, Direction out, int amount) {
        if (amount <= 0 || out == null) return;
        Holder h = LEVELS.computeIfAbsent(level, k -> new Holder());
        long t = level.getGameTime();
        if (h.tick != t) {
            h.flow.clear();
            h.tick = t;
        }
        int[][] byType = h.flow.computeIfAbsent(pipe.asLong(), k -> new int[TYPES][DIRS]);
        byType[type.ordinal()][out.get3DDataValue()] += amount;
    }

    /**
     * Поток через трубу типа {@code type} за последний актуальный тик: массив из
     * 6 значений, индексируемых {@link Direction#get3DDataValue()} — сколько вышло
     * в каждую сторону. Нули, если данных нет или они устарели.
     */
    public static int[] get(Level level, BlockPos pipe, PipeType type) {
        Holder h = LEVELS.get(level);
        if (h == null) return EMPTY;
        if (level.getGameTime() - h.tick > 1) return EMPTY;
        int[][] byType = h.flow.get(pipe.asLong());
        return byType == null ? EMPTY : byType[type.ordinal()];
    }

    /** Сброс при остановке сервера, чтобы не удерживать ссылки на уровни. */
    public static void clearAll() {
        LEVELS.clear();
    }
}
