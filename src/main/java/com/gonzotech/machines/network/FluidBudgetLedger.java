package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Транзитный учёт ОБЩЕГО жидкостного бюджета универсальной трубы за тик — без
 * BlockEntity и без NBT (живёт в памяти сервера один тик, затем обнуляется).
 * <p>
 * Универсальная жидкостная труба несёт воду И пар одновременно как два
 * независимых потока, но их СУММАРНАЯ пропускная способность ограничена
 * ({@code 800 mB/t}). Вода и пар сливаются РАЗНЫМИ машинами в разных вызовах
 * {@link PipeRouting#drain} за один тик, поэтому общий лимит нельзя выразить
 * статично на тип — его нужно копить между вызовами. Этот леджер хранит, сколько
 * mB уже прошло через конкретную универсальную трубу (по позиции) в текущем тике;
 * следующий слив через неё получает лишь остаток бюджета.
 */
public final class FluidBudgetLedger {

    private static final Map<Level, Holder> LEVELS = new IdentityHashMap<>();

    private FluidBudgetLedger() {
    }

    private static final class Holder {
        long tick = Long.MIN_VALUE;
        final Map<Long, Long> used = new HashMap<>();
    }

    private static Holder holderFor(Level level) {
        Holder h = LEVELS.computeIfAbsent(level, k -> new Holder());
        long t = level.getGameTime();
        if (h.tick != t) {
            h.used.clear();
            h.tick = t;
        }
        return h;
    }

    /** Сколько mB уже прошло через универсальную трубу {@code pipe} в этом тике. */
    public static long used(Level level, BlockPos pipe) {
        return holderFor(level).used.getOrDefault(pipe.asLong(), 0L);
    }

    /** Учесть, что через универсальную трубу {@code pipe} прошло ещё {@code amount} mB. */
    public static void add(Level level, BlockPos pipe, long amount) {
        if (amount <= 0) return;
        Holder h = holderFor(level);
        h.used.merge(pipe.asLong(), amount, Long::sum);
    }

    /** Сброс при остановке сервера. */
    public static void clearAll() {
        LEVELS.clear();
    }
}
