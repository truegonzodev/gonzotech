package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Пер-тиковый учёт ФАКТИЧЕСКОГО потока через каждую позицию трубы (по типам) —
 * единственный источник правды для «сколько ещё пропускной способности трубы
 * осталось в этом тике».
 * <p>
 * <b>Закон пропускной способности живёт на ТРУБЕ, а не на источнике.</b> За один
 * тик суммарный поток через позицию трубы типа {@code type} не превышает её
 * throughput (у универсальной жидкостной — общий бюджет всех жидкостей, считаем
 * все {@link PipeType#isFluid()} вместе).
 * <p>
 * Теперь каждый слив видит, что уже прошло через сегменты, и дробит бюджет с
 * учётом остатков ({@link PipeRouting#drain} / {@link PipeRouting#drainFromMultiblockPort}).
 * <p>
 * Как и {@link FlowTracker}: без BlockEntity, без NBT — живёт в памяти сервера
 * один тик, затем сбрасывается.
 */
public final class PipeFlowLedger {

    private static final Map<Level, Holder> LEVELS = new IdentityHashMap<>();

    private PipeFlowLedger() {
    }

    private static final class Holder {
        long tick = Long.MIN_VALUE;
        final Map<Long, Map<PipeType, Long>> used = new HashMap<>();
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

    /** Сколько единиц типа {@code type} уже прошло через трубу {@code pipe} в этом тике. */
    public static long used(Level level, BlockPos pipe, PipeType type) {
        return holderFor(level).used.getOrDefault(pipe.asLong(), Map.of())
            .getOrDefault(type, 0L);
    }

    /** Учесть, что через трубу {@code pipe} ещё прошло {@code amount} единиц типа {@code type}. */
    public static void add(Level level, BlockPos pipe, PipeType type, long amount) {
        if (amount <= 0) return;
        holderFor(level).used
            .computeIfAbsent(pipe.asLong(), k -> new HashMap<>())
            .merge(type, amount, Long::sum);
    }

    /**
     * Остаток пропускной способности трубы {@code state} (позиция {@code pos})
     * для типа {@code type} в этом тике: лимит минус уже прошедшее.
     * <p>
     * Универсальная жидкостная труба/узел несёт всё жидкостное семейство в ОБЩЕМ
     * бюджете (800/1500 mB/t): остаток = общий бюджет минус прошедшее по ВСЕМ
     * жидкостям. Для браги лимит дополнительно срезается наполовину (×0.5).
     * Множитель универсального узла (×0.9) применяется ко всему бюджету заранее.
     * Все прочие типы — свой {@link PipeCarrier#throughputLimit} минус своё
     * потребление.
     */
    public static long remaining(Level level, BlockPos pos, BlockState state, PipeType type) {
        if (state.getBlock() instanceof PipeCarrier carrier) {
            if (PipeCarrier.isUniversal(state) && type.isFluid()) {
                long shared = scaled(carrier.sharedFluidThroughputLimit(state), carrier, state, type);
                if (type == PipeType.MASH) {
                    shared = shared / 2;
                }
                long usedShared = 0;
                for (PipeType pt : PipeType.values()) {
                    if (pt.isFluid()) {
                        usedShared += used(level, pos, pt);
                    }
                }
                return Math.max(0, shared - usedShared);
            }
            long capacity = scaled(carrier.throughputLimit(state, type), carrier, state, type);
            return Math.max(0, capacity - used(level, pos, type));
        }
        return Math.max(0, type.maxThroughput() - used(level, pos, type));
    }

    /** Лимит с множителем {@link PipeCarrier#throughputFactor} (узел универсала — 0.9). */
    private static long scaled(long limit, PipeCarrier carrier, BlockState state, PipeType type) {
        if (limit <= 0) return limit;
        double f = carrier.throughputFactor(state, type);
        return f < 1.0 ? Math.max(1L, (long) Math.floor(limit * f)) : limit;
    }

    /** Сброс при остановке сервера (держим ссылки на Level). */
    public static void clearAll() {
        LEVELS.clear();
    }
}
