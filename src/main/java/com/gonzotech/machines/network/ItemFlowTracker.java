package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Учёт фактического потока ПРЕДМЕТОВ через предметные трубы — по образцу
 * {@link FlowTracker} (память сервера, транзитно, обнуляется каждый тик), но
 * значение — «сколько штук КАКОГО предмета» прошло через трубу за тик.
 * <p>
 * Нужен для HUD гаечного ключа: навёл на предметную трубу — видишь строки вида
 * «Булыжник — 16/т, Уголь — 1/т». Данные привязаны к тику: чтение старше одного
 * тика считается устаревшим (поток прекратился).
 */
public final class ItemFlowTracker {

    private ItemFlowTracker() {
    }

    private static final Map<Item, Integer> EMPTY = Map.of();
    private static final Map<Level, Holder> LEVELS = new IdentityHashMap<>();

    private static final class Holder {
        long tick = Long.MIN_VALUE;
        // Ключ — позиция трубы. Значение — предмет → количество за тик.
        final Map<Long, Map<Item, Integer>> flow = new HashMap<>();
    }

    /** Записать: через трубу в {@code pipe} прошло {@code count} шт. предмета {@code item}. */
    public static void record(Level level, BlockPos pipe, Item item, int count) {
        if (count <= 0 || item == null) return;
        Holder h = LEVELS.computeIfAbsent(level, k -> new Holder());
        long t = level.getGameTime();
        if (h.tick != t) {
            h.flow.clear();
            h.tick = t;
        }
        Map<Item, Integer> byItem = h.flow.computeIfAbsent(pipe.asLong(), k -> new LinkedHashMap<>());
        byItem.merge(item, count, Integer::sum);
    }

    /**
     * Поток предметов через трубу в {@code pipe} за последний актуальный тик:
     * предмет → штук. Пустая карта, если данных нет или устарели.
     */
    public static Map<Item, Integer> get(Level level, BlockPos pipe) {
        Holder h = LEVELS.get(level);
        if (h == null) return EMPTY;
        if (level.getGameTime() - h.tick > 1) return EMPTY;
        Map<Item, Integer> byItem = h.flow.get(pipe.asLong());
        return byItem == null ? EMPTY : byItem;
    }

    /** Сброс при остановке сервера. */
    public static void clearAll() {
        LEVELS.clear();
    }
}
