package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Аналоговый выход универсального узла: число РЕАЛЬНО работающих потоков.
 * Состояние не сохраняется: поток транзитный, поэтому при перезапуске/загрузке
 * сигнал снова получается из {@link FlowTracker} и {@link ItemFlowTracker}.
 */
public final class UniversalNodeComparator {

    /** Только пять потоков, определённых для first_universal_node. */
    private static final PipeType[] TRACKED_PIPES = {
        PipeType.WIRE, PipeType.HEAT, PipeType.WATER, PipeType.STEAM
    };

    /** Последнее отправленное ненулевое значение по позиции, для редких neighbour update. */
    private static final Map<Level, Map<Long, Integer>> LAST_SIGNALS = new IdentityHashMap<>();

    private UniversalNodeComparator() {
    }

    /** Возвращает 0..5: Wire, Heat, Water, Steam и Items, только если они реально двигались. */
    public static int signal(Level level, BlockPos pos) {
        int active = 0;
        for (PipeType type : TRACKED_PIPES) {
            if (hasResourceFlow(level, pos, type)) active++;
        }
        if (!ItemFlowTracker.get(level, pos).isEmpty()) active++;
        return active;
    }

    /**
     * Сверяет текущее значение с опубликованным и будит соседние компараторы,
     * только когда число активных потоков действительно изменилось.
     */
    public static void update(ServerLevel level, BlockPos pos) {
        int next = signal(level, pos);
        Map<Long, Integer> signals = LAST_SIGNALS.computeIfAbsent(level, ignored -> new HashMap<>());
        long key = pos.asLong();
        int previous = signals.getOrDefault(key, 0);
        if (previous == next) return;

        if (next == 0) {
            signals.remove(key);
            if (signals.isEmpty()) LAST_SIGNALS.remove(level);
        } else {
            signals.put(key, next);
        }
        level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
    }

    /** Забывает положение удалённого/заменённого узла, чтобы координата могла быть использована заново. */
    public static void forget(Level level, BlockPos pos) {
        Map<Long, Integer> signals = LAST_SIGNALS.get(level);
        if (signals == null) return;
        signals.remove(pos.asLong());
        if (signals.isEmpty()) LAST_SIGNALS.remove(level);
    }

    /** Сброс на остановке сервера: карта держит ссылки на Level. */
    public static void clearAll() {
        LAST_SIGNALS.clear();
    }

    private static boolean hasResourceFlow(Level level, BlockPos pos, PipeType type) {
        for (long amount : FlowTracker.get(level, pos, type)) {
            if (amount > 0) return true;
        }
        return false;
    }
}
