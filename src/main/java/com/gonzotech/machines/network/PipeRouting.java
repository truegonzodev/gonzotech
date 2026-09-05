package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Маршрутизация слива через провода — БЕЗ сетей-объектов, буферов и тиков.
 * <p>
 * Провод здесь — просто «вынос слива за пределы блока». Он ничего не хранит и
 * ничего ни из кого не «высасывает». Когда машина ДОБРОВОЛЬНО сливает свою
 * выходную шкалу и рядом нет прямого приёмника, но есть провод — этот класс на
 * лету обходит связную цепь труб того же типа (BFS) и собирает все машины,
 * которым можно отдать (грань трубы в режиме {@link PipeMode#deliversToMachine}).
 * Ресурс телепортируется приёмникам за тот же тик.
 * <p>
 * Обход выполняется лишь когда машине реально есть что слить (и провод рядом), а
 * не каждый тик у каждого провода. Стоимость зависит от размера цепи.
 */
public final class PipeRouting {

    private PipeRouting() {
    }

    /**
     * Единая точка слива для машины: собрать приёмники (прямые соседи + машины за
     * проводами) и раздать им {@code budget} единиц РАВНОМЕРНО
     * ({@link Transfer#distributeAmong}). С проводами рядом слив «дотягивается»
     * дальше; без проводов работает как прямая передача соседу (поведение Фазы 2).
     *
     * @param level      мир
     * @param fromPos    позиция машины-источника (её саму в приёмники не берём)
     * @param type       тип провода/ресурса
     * @param budget     сколько единиц машина готова слить за этот тик
     * @param rotation   сдвиг ротации остатка (обычно {@code level.getGameTime()})
     * @param receiverOf для BlockEntity-приёмника → {@link Transfer.Receiver} (или null)
     * @return сколько единиц суммарно принято (столько же списать из машины)
     */
    public static int drain(Level level, BlockPos fromPos, PipeType type, int budget, long rotation,
                            BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {
        if (budget <= 0) return 0;

        // Дедуп приёмников по позиции. Порядок стабилен (TreeMap по asLong) — для
        // честной ротации остатка в distributeAmong.
        TreeMap<Long, Transfer.Receiver> receivers = new TreeMap<>();

        // 1) Прямые соседи-приёмники (не трубы, не сам источник).
        for (Direction dir : Direction.values()) {
            BlockPos npos = fromPos.relative(dir);
            BlockState nstate = level.getBlockState(npos);
            if (isPipe(nstate, type)) continue;
            addReceiver(level, npos, fromPos, receivers, receiverOf);
        }

        // 2) Приёмники за проводами.
        collectThroughPipes(level, fromPos, type, receivers, receiverOf);

        if (receivers.isEmpty()) return 0;
        return Transfer.distributeAmong(new ArrayList<>(receivers.values()), budget, rotation);
    }

    /** BFS по трубам от машины; наполняет {@code receivers} машинами за трубами. */
    private static void collectThroughPipes(
            Level level, BlockPos fromPos, PipeType type,
            TreeMap<Long, Transfer.Receiver> receivers,
            BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {

        // Старт — прилегающие провода, чья грань принимает слив из машины (AUTO/PULL).
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos ppos = fromPos.relative(dir);
            BlockState pstate = level.getBlockState(ppos);
            if (isPipe(pstate, type) && pstate.getValue(PipeBlock.MODE).acceptsFromMachine()) {
                if (visited.add(ppos)) queue.add(ppos);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pipe = queue.poll();
            PipeMode mode = level.getBlockState(pipe).getValue(PipeBlock.MODE);
            for (Direction dir : Direction.values()) {
                BlockPos npos = pipe.relative(dir);
                BlockState nstate = level.getBlockState(npos);
                if (isPipe(nstate, type)) {
                    if (visited.add(npos)) queue.add(npos);
                    continue;
                }
                // Машина за трубой — приёмник, только если ЭТА труба отдаёт в машину.
                if (!mode.deliversToMachine()) continue;
                addReceiver(level, npos, fromPos, receivers, receiverOf);
            }
        }
    }

    private static void addReceiver(
            Level level, BlockPos pos, BlockPos fromPos,
            TreeMap<Long, Transfer.Receiver> receivers,
            BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {
        if (pos.equals(fromPos)) return;
        long key = pos.asLong();
        if (receivers.containsKey(key)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        Transfer.Receiver r = receiverOf.apply(be, pos);
        if (r != null) receivers.put(key, r);
    }

    private static boolean isPipe(BlockState state, PipeType type) {
        return state.getBlock() instanceof PipeBlock pb && pb.pipeType() == type;
    }
}
