package com.gonzotech.machines.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Железное правило передачи ресурсов Gonzo Tech: РАВНОМЕРНОЕ (не приоритетное)
 * распределение бюджета отдачи между всеми соседями-приёмниками.
 * <p>
 * Если у источника {@code N} приёмников, а бюджет отдачи за тик равен {@code B},
 * каждому приёмнику предлагается доля {@code B/N} (остаток от деления раздаётся
 * по одной единице первым приёмникам в фиксированном порядке граней, чтобы сумма
 * ровно равнялась {@code B}). Никакого приоритета/последовательного «залил
 * первого досыта» — до появления умных проводов/шин это правило неизменно.
 * <p>
 * Пример: генератор отдаёт 40 GTU/t на 2 электропечи → по 20 каждой.
 */
public final class Transfer {

    private Transfer() {
    }

    /** Приёмник одной порции ресурса (совпадает по сигнатуре с методами {@link Sinks}). */
    @FunctionalInterface
    public interface Receiver {
        /** @return сколько единиц реально принято */
        int receive(int amount, boolean simulate);
    }

    /**
     * Равномерно раздать {@code budget} единиц ресурса соседям-приёмникам.
     *
     * @param level  мир
     * @param pos    позиция источника
     * @param budget сколько единиц источник готов отдать за этот тик
     *               (обычно {@code min(maxOutput, stored)})
     * @param mapper для соседнего {@link BlockEntity} возвращает {@link Receiver}
     *               (если это валидный приёмник) или {@code null}
     * @return сколько единиц суммарно принято соседями (это же нужно списать из
     *         буфера источника)
     */
    public static int distribute(Level level, BlockPos pos, int budget,
                                 Function<BlockEntity, Receiver> mapper) {
        if (budget <= 0) return 0;

        List<Receiver> receivers = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            BlockEntity be = level.getBlockEntity(pos.relative(dir));
            if (be == null) continue;
            Receiver r = mapper.apply(be);
            if (r != null) receivers.add(r);
        }

        int n = receivers.size();
        if (n == 0) return 0;

        int base = budget / n;
        int extra = budget % n;

        int moved = 0;
        for (int i = 0; i < n; i++) {
            int share = base + (i < extra ? 1 : 0);
            if (share <= 0) continue;
            moved += receivers.get(i).receive(share, false);
        }
        return moved;
    }
}
