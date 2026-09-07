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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
 * Попутно каждому проводу на пути от машины к приёмнику записывается фактически
 * прошедший через него объём ({@link FlowTracker}) — по мировым сторонам, куда
 * ресурс вышел. Так провод не хранит поток, но HUD гаечного ключа может показать
 * живые числа по концам оси (напр. восток 60, запад 30 при встречных потоках).
 * <p>
 * Обход выполняется лишь когда машине реально есть что слить (и провод рядом), а
 * не каждый тик у каждого провода. Стоимость зависит от размера цепи.
 * <p>
 * <b>Соединения.</b> У обычной трубы ровно два конца — по её оси; она открыта
 * соседу ТОЛЬКО через торец. Блок-узел ({@link NodeBlock}) открыт во все 6 сторон
 * ({@link PipeBlock#connectsAllSides()}) — это точка ветвления/уголков. Правило
 * едино для всех точек связи:
 * <ul>
 *   <li>труба ↔ труба: соединяются, только если ОБЕ грани открыты навстречу
 *       (у обычной трубы грань открыта, если направление = её ось; у узла — всегда)
 *       и обе трубы одного типа;</li>
 *   <li>машина ↔ труба: машина цепляется к грани трубы, только если та открыта в
 *       её сторону (торец обычной трубы либо любая грань узла).</li>
 * </ul>
 */
public final class PipeRouting {

    private PipeRouting() {
    }

    /**
     * Открыта ли в блоке {@code state} труба типа {@code type} гранью в сторону
     * {@code dir}. Делегирует носителю ({@link PipeCarrier}) — работает и для
     * одиночной трубы/узла, и для составного блока.
     */
    private static boolean opensToward(BlockState state, PipeType type, Direction dir) {
        return state.getBlock() instanceof PipeCarrier c && c.opensToward(state, type, dir);
    }

    /** Два соседних носителя соединяются по типу, если обе грани открыты навстречу. */
    private static boolean pipesConnect(BlockState a, BlockState b, PipeType type, Direction dirAtoB) {
        return opensToward(a, type, dirAtoB) && opensToward(b, type, dirAtoB.getOpposite());
    }

    /**
     * Машина соединяется с трубой типа {@code type}, только если её грань открыта
     * в сторону машины (навстречу направлению машина→труба).
     */
    private static boolean machineConnects(BlockState pipe, PipeType type, Direction dirToPipe) {
        return opensToward(pipe, type, dirToPipe.getOpposite());
    }

    /** Шаг пути: труба {@code pipe} выпускает ресурс в сторону {@code out}. */
    private record PathStep(BlockPos pipe, Direction out) {
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
    public static long drain(Level level, BlockPos fromPos, PipeType type, long budget, long rotation,
                             BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {
        if (budget <= 0) return 0;

        // Пропускная способность трубы/узла бьёт бюджет: если рядом есть труба этого
        // типа, принимающая слив из машины, то за тик через цепь уходит не больше
        // maxThroughput единиц — труба «уровня 1» умышленно медленнее выхода машины
        // (напр. провод 38 GTU/t < аккумулятор 64 GTU/t). Без труб рядом слив идёт
        // напрямую соседу и лимитом трубы не режется.
        //
        // Универсальная жидкостная труба несёт воду+пар с ОБЩИМ бюджетом (800 mB/t):
        // берём как точку входа лучшую (с наибольшим остатком) прилегающую трубу и
        // ограничиваем слив её остаточным бюджетом за этот тик; после слива учитываем
        // фактический объём в {@link FluidBudgetLedger}.
        BlockPos universalEntry = null;
        long entryLimit = adjacentEntryLimit(level, fromPos, type);
        if (entryLimit >= 0) {
            budget = Math.min(budget, entryLimit);
            universalEntry = bestUniversalEntry(level, fromPos, type);
        }
        if (budget <= 0) return 0;

        // Дедуп приёмников по позиции. Порядок стабилен (TreeMap по asLong) — для
        // честной ротации остатка в distributeAmong.
        TreeMap<Long, Transfer.Receiver> receivers = new TreeMap<>();

        // 1) Прямые соседи-приёмники (не трубы, не сам источник). Через провод не
        // идут — поток по проводам для них не пишем.
        for (Direction dir : Direction.values()) {
            BlockPos npos = fromPos.relative(dir);
            BlockState nstate = level.getBlockState(npos);
            if (isPipe(nstate, type)) continue;
            addReceiver(level, npos, fromPos, receivers, receiverOf, type, null);
        }

        // 2) Приёмники за проводами (с записью потока по пути).
        collectThroughPipes(level, fromPos, type, receivers, receiverOf);

        if (receivers.isEmpty()) return 0;
        long moved = Transfer.distributeAmong(new ArrayList<>(receivers.values()), budget, rotation);
        if (universalEntry != null && moved > 0) {
            FluidBudgetLedger.add(level, universalEntry, moved);
        }
        return moved;
    }

    /**
     * Лимит слива за тик, если рядом есть труба типа {@code type}, принимающая слив
     * из машины (грань открыта к машине, режим AUTO/PULL — совпадает с условием
     * старта BFS в {@link #collectThroughPipes}). Возвращает {@code -1}, если такой
     * трубы нет (слив пойдёт напрямую соседу и не режется лимитом трубы).
     * <p>
     * Обычная труба даёт статичный {@link PipeType#maxThroughput()}. Универсальная
     * жидкостная труба ({@link UniversalFluidPipeBlock#isUniversal}) несёт воду+пар
     * с общим бюджетом на тик, поэтому её лимит — ОСТАТОК бюджета
     * ({@link FluidBudgetLedger}). Если рядом несколько подходящих труб, берём
     * наибольший из доступных лимитов (лучшую точку входа).
     */
    private static long adjacentEntryLimit(Level level, BlockPos fromPos, PipeType type) {
        long best = -1;
        for (Direction dir : Direction.values()) {
            BlockPos ppos = fromPos.relative(dir);
            BlockState pstate = level.getBlockState(ppos);
            if (!isPipe(pstate, type)) continue;
            if (!machineConnects(pstate, type, dir)) continue;
            if (!modeOf(pstate, type).acceptsFromMachine()) continue;
            long limit = pipeEntryLimit(level, ppos, pstate, type);
            if (limit > best) best = limit;
        }
        return best;
    }

    /** Позиция лучшей (наибольший остаток) прилегающей универсальной трубы, или null. */
    private static BlockPos bestUniversalEntry(Level level, BlockPos fromPos, PipeType type) {
        BlockPos best = null;
        long bestLimit = -1;
        for (Direction dir : Direction.values()) {
            BlockPos ppos = fromPos.relative(dir);
            BlockState pstate = level.getBlockState(ppos);
            if (!isPipe(pstate, type)) continue;
            if (!machineConnects(pstate, type, dir)) continue;
            if (!modeOf(pstate, type).acceptsFromMachine()) continue;
            if (!isUniversal(pstate)) continue;
            long remain = MachineDefsUniversalOutput() - FluidBudgetLedger.used(level, ppos);
            if (remain > bestLimit) {
                bestLimit = remain;
                best = ppos;
            }
        }
        return best;
    }

    /** Лимит одной прилегающей трубы. Универсальная — остаток общего бюджета. */
    private static long pipeEntryLimit(Level level, BlockPos ppos, BlockState pstate, PipeType type) {
        if (isUniversal(pstate)) {
            return Math.max(0, MachineDefsUniversalOutput() - FluidBudgetLedger.used(level, ppos));
        }
        return type.maxThroughput();
    }

    private static boolean isUniversal(BlockState state) {
        // Одиночная универсальная труба/узел, либо пучок, где FLUID-угол занят
        // универсальной трубой (вода+пар вместе) — в обоих случаях общий бюджет.
        return state.getBlock() instanceof UniversalFluidPipeBlock
            || CompositePipeBlock.carriesUniversalFluid(state);
    }

    private static long MachineDefsUniversalOutput() {
        return com.gonzotech.machines.energy.MachineDefs.UNIVERSAL_FLUID_OUTPUT;
    }

    /** BFS по трубам от машины; наполняет {@code receivers} машинами за трубами. */
    private static void collectThroughPipes(
            Level level, BlockPos fromPos, PipeType type,
            TreeMap<Long, Transfer.Receiver> receivers,
            BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {

        // Родитель каждой посещённой трубы (труба ближе к машине), null у стартовых.
        // По нему восстанавливаем путь машина→…→труба для записи потока.
        Map<Long, BlockPos> parent = new HashMap<>();

        // Старт — прилегающие провода, чья грань принимает слив из машины (AUTO/PULL)
        // И которые повёрнуты торцом к машине (машина на конце оси, не сбоку).
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos ppos = fromPos.relative(dir);
            BlockState pstate = level.getBlockState(ppos);
            if (!isPipe(pstate, type)) continue;
            if (!machineConnects(pstate, type, dir)) continue;
            if (!modeOf(pstate, type).acceptsFromMachine()) continue;
            if (visited.add(ppos)) {
                parent.put(ppos.asLong(), null);
                queue.add(ppos);
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pipe = queue.poll();
            BlockState pstate = level.getBlockState(pipe);
            PipeMode mode = modeOf(pstate, type);
            for (Direction dir : Direction.values()) {
                BlockPos npos = pipe.relative(dir);
                BlockState nstate = level.getBlockState(npos);
                if (isPipe(nstate, type)) {
                    // Соединяем, только если обе грани этого типа открыты навстречу.
                    if (!pipesConnect(pstate, nstate, type, dir)) continue;
                    if (visited.add(npos)) {
                        parent.put(npos.asLong(), pipe);
                        queue.add(npos);
                    }
                    continue;
                }
                // Машина за трубой — приёмник, только если грань трубы открыта к ней
                // и ЭТА труба отдаёт в машину.
                if (!machineConnects(pstate, type, dir)) continue;
                if (!mode.deliversToMachine()) continue;
                List<PathStep> path = buildPath(level, pipe, npos, parent);
                addReceiver(level, npos, fromPos, receivers, receiverOf, type, path);
            }
        }
    }

    /**
     * Путь от стартовой трубы до {@code viaPipe} и далее выход в {@code receiver}.
     * Каждый шаг — какая труба в какую сторону выпускает ресурс. Порядок не важен
     * для записи (пишем всем шагам одинаковый прошедший объём).
     */
    private static List<PathStep> buildPath(Level level, BlockPos viaPipe, BlockPos receiver,
                                            Map<Long, BlockPos> parent) {
        List<PathStep> steps = new ArrayList<>();
        BlockPos cur = viaPipe;
        BlockPos next = receiver; // узел ближе к приёмнику, куда выходит ресурс
        while (cur != null) {
            Direction out = dirFromTo(cur, next);
            if (out != null) steps.add(new PathStep(cur, out));
            next = cur;
            cur = parent.get(cur.asLong());
        }
        return steps;
    }

    private static void addReceiver(
            Level level, BlockPos pos, BlockPos fromPos,
            TreeMap<Long, Transfer.Receiver> receivers,
            BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf,
            PipeType type, List<PathStep> path) {
        if (pos.equals(fromPos)) return;
        long key = pos.asLong();
        if (receivers.containsKey(key)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        Transfer.Receiver r = receiverOf.apply(be, pos);
        if (r == null) return;
        receivers.put(key, path == null ? r : recording(level, r, type, path));
    }

    /**
     * Обёртка-приёмник: сколько реально принято — столько же записываем каждому
     * проводу на пути в его выходную сторону (учёт фактического потока за тик,
     * раздельно по типу — в связке типы делят позицию).
     */
    private static Transfer.Receiver recording(Level level, Transfer.Receiver real, PipeType type, List<PathStep> path) {
        return (amount, simulate) -> {
            long accepted = real.receive(amount, simulate);
            if (!simulate && accepted > 0) {
                for (PathStep s : path) {
                    FlowTracker.record(level, s.pipe(), type, s.out(), accepted);
                }
            }
            return accepted;
        };
    }

    private static Direction dirFromTo(BlockPos from, BlockPos to) {
        for (Direction d : Direction.values()) {
            if (from.relative(d).equals(to)) return d;
        }
        return null;
    }

    private static boolean isPipe(BlockState state, PipeType type) {
        return state.getBlock() instanceof PipeCarrier c && c.carries(state, type);
    }

    private static PipeMode modeOf(BlockState state, PipeType type) {
        return state.getBlock() instanceof PipeCarrier c ? c.modeFor(state, type) : PipeMode.AUTO;
    }
}
