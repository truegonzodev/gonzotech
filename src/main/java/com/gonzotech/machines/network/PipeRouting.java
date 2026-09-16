package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.steamgen.SteamGenStructure;
import com.gonzotech.machines.turbine.TurbineStructure;
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
 * <b>Закон пропускной способности живёт на ТРУБЕ, а не на источнике.</b> За один
 * тик суммарный поток через позицию трубы не превышает её throughput
 * ({@link PipeFlowLedger}: всё, что уже прошло через сегмент в этом тике,
 * вычитается из остатка). Поэтому:
 * <ul>
 *   <li>Y-ветка (1 источник → общий ствол → 2 конца): источник отдаёт не больше
 *       остатка ствола, и равномерное деление даёт по 19 на каждый конец тир-1
 *       провода (ствол 38) — не по 38 на каждый;</li>
 *   <li>обратная Y (2 источника → 1 приёмник): общий сегмент пропустит суммарно
 *       только 38 — второй источник увидит, что ствол уже занят, и отдаст остаток
 *       (first-come-first-served по порядку тиков);</li>
 *   <li>механики в режиме авто, принимающие и отдающие одновременно, больше не
 *       гоняют встречные потоки через тот же провод сверх его предела.</li>
 * </ul>
 * Параллельные entry-трубы у ОДНОГО источника суммируются (2 провода = 2
 * канала по 38) — но каждый канал по-прежнему подчиняется общему закону на
 * общих сегментах.
 * <p>
 * Попутно каждому проводу на пути от машины к приёмнику записывается фактически
 * прошедший через него объём ({@link FlowTracker}) — по мировым сторонам, куда
 * ресурс вышел. Так HUD гаечного ключа показывает живые числа, и после закона
 * на трубе число на HUD НИКОГДА не превышает номинальный throughput трубы.
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
     * ({@link #distributeWithSegmentCaps} — равномерное «левелинг»-деление с
     * учётом остатков пропускной способности на общих сегментах путей). С
     * проводами рядом слив «дотягивается» дальше; без проводов работает как
     * прямая передача соседу (поведение Фазы 2).
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

        // Пропускная способность труб бьёт бюджет: если рядом есть трубы этого
        // типа, принимающие слив из машины, то за тик через сеть уходит не больше
        // СУММЫ ОСТАТКОВ ВСЕХ прилегающих entry-труб — параллельные трубы
        // независимые каналы и складываются (2 провода тир-1 = 76 GTU/t у
        // источника). Остатки, а не полные лимиты: всё, что уже прошло через
        // эти трубы в этом тике (другие источники), вычтено
        // ({@link PipeFlowLedger}). Без труб рядом слив идёт напрямую соседу и
        // лимитом трубы не режется.
        long entryLimit = entryLimit(level, fromPos, type);
        if (entryLimit >= 0) {
            budget = Math.min(budget, entryLimit);
        }
        if (budget <= 0) return 0;

        // Дедуп приёмников по позиции. Порядок стабилен (TreeMap по asLong) — для
        // честной ротации остатка в distributeAmong.
        TreeMap<Long, Transfer.Receiver> receivers = new TreeMap<>();
        Map<Long, List<PathStep>> paths = new HashMap<>();

        // 1) Прямые соседи-приёмники (не трубы, не сам источник). Через провод не
        // идут — поток по проводам для них не пишем, потолка у них нет.
        for (Direction dir : Direction.values()) {
            BlockPos npos = fromPos.relative(dir);
            BlockState nstate = level.getBlockState(npos);
            if (isPipe(nstate, type)) continue;
            addReceiver(level, npos, fromPos, receivers, paths, receiverOf, type, null);
        }

        // 2) Приёмники за проводами (с путём — он и есть потолок по остаткам).
        collectThroughPipes(level, fromPos, type, receivers, paths, receiverOf);

        if (receivers.isEmpty()) return 0;
        return distributeWithSegmentCaps(level, type, receivers, paths, budget, rotation);
    }

    /**
     * Слив из специального встроенного порта турбины, который сам является
     * PipeCarrier. Делегирует в обобщённый {@link #drainFromMultiblockPort},
     * исключая из обхода остальные части той же турбины.
     */
    public static long drainFromTurbinePort(Level level, BlockPos port, PipeType type, long budget, long rotation,
                                            BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {
        return drainFromMultiblockPort(level, port, type, budget, rotation, receiverOf,
            (lvl, pos) -> TurbineStructure.isMember(lvl, pos));
    }

    /**
     * Слив из специального встроенного порта многоблока, который сам является
     * PipeCarrier. В отличие от {@link #drain} стартовая нода уже лежит в сети,
     * поэтому обход начинается с неё, а не из соседней трубы. Обход
     * «не заходит» в позиции, отмеченные {@code isMember} (остальные части той
     * же установки), чтобы ресурс не уходил обратно в корпус.
     *
     * <p>Бюджет порта задаёт контроллер до вызова; общие сегменты за портом
     * подчиняются тому же закону на трубе ({@link PipeFlowLedger}) — потолок
     * приёмника = остаток узкого сегмента его пути.</p>
     *
     * @param isMember проверка «принадлежит ли позиция этой установке» (порт сам
     *                 исключается по {@code !next.equals(port)})
     */
    public static long drainFromMultiblockPort(Level level, BlockPos port, PipeType type, long budget, long rotation,
                                               BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf,
                                               BiFunction<Level, BlockPos, Boolean> isMember) {
        if (budget <= 0 || !isPipe(level.getBlockState(port), type)) return 0;

        TreeMap<Long, Transfer.Receiver> receivers = new TreeMap<>();
        Map<Long, List<PathStep>> paths = new HashMap<>();
        Map<Long, BlockPos> parent = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        parent.put(port.asLong(), null);
        queue.add(port);
        visited.add(port);

        while (!queue.isEmpty()) {
            BlockPos pipe = queue.poll();
            // Виртуальный приёмник встроенного порта (паровой порт турбины) —
            // ровно как в collectThroughPipes: нода сама является и трубой,
            // и входом машины. Без этой проверки парогенератор не смог бы
            // кормить турбину: для BFS чужая нода — просто ещё одна труба.
            if (type == PipeType.STEAM) {
                addTurbineSteamReceiver(level, pipe, port, receivers, paths,
                    buildPath(level, pipe, pipe, parent));
            }
            BlockState pstate = level.getBlockState(pipe);
            PipeMode mode = modeOf(pstate, type);
            for (Direction dir : Direction.values()) {
                BlockPos next = pipe.relative(dir);
                // Через другую встроенную ноду не идём: порт — это граница
                // машины, не внутренняя связка из проводов.
                if (!next.equals(port) && isMember.apply(level, next)) continue;
                BlockState nextState = level.getBlockState(next);
                if (isPipe(nextState, type)) {
                    if (!pipesConnect(pstate, nextState, type, dir)) continue;
                    if (visited.add(next)) {
                        parent.put(next.asLong(), pipe);
                        queue.add(next);
                    }
                    continue;
                }
                if (!machineConnects(pstate, type, dir) || !mode.deliversToMachine()) continue;
                List<PathStep> path = buildPath(level, pipe, next, parent);
                addReceiver(level, next, port, receivers, paths, receiverOf, type, path);
            }
        }
        if (receivers.isEmpty()) return 0;
        return distributeWithSegmentCaps(level, type, receivers, paths, budget, rotation);
    }

    /**
     * Сумма остатков всех прилегающих entry-труб (см. {@link #drain}).
     * Возвращает {@code -1}, если таких труб нет (слив пойдёт напрямую соседу и
     * не режется лимитом трубы).
     */
    private static long entryLimit(Level level, BlockPos fromPos, PipeType type) {
        long total = -1;
        for (Direction dir : Direction.values()) {
            BlockPos ppos = fromPos.relative(dir);
            BlockState pstate = level.getBlockState(ppos);
            if (!isPipe(pstate, type)) continue;
            if (!machineConnects(pstate, type, dir)) continue;
            if (!modeOf(pstate, type).acceptsFromMachine()) continue;
            total = (total < 0 ? 0L : total) + PipeFlowLedger.remaining(level, ppos, pstate, type);
        }
        return total;
    }

    /**
     * «Левелинг» (max-min fair split) с законом на трубе: равномерно поднять
     * уровень ВСЕХ активных приёмников, но уровень ограничен:
     * <ul>
     *   <li>остатком бюджета / числом активных (честное деление);</li>
     *   <li>для КАЖДОЙ позиции трубы: остатком её пропускной способности
     *       / числом активных приёмников, чей путь через неё идёт. Это и есть
     *       закон «общий сегмент не берёт больше, чем пропустит»: два приёмника
     *       на общем стволе 38 получат по 19 (а не по 38), потому что уровень
     *       бьётся 38/2.</li>
     * </ul>
     * Позиция, у которой остаток исчерпан, «замораживает» приёмников на ней
     * (они не могут взять больше) — уровень продолжает расти у остальных.
     * Рунд заканчивается исчерпанием бюджета либо полным замораживанием.
     * Рундов ≤ n+1 (каждый рунд либо снимает с игры ≥1 приёмника, либо
     * добирает остаток), стоимость O(n × длина путей) — для реальных сетей
     * ничтожно.
     * <p>
     * Порядок приёмников стабилен (TreeMap по asLong), ротация остатка —
     * {@code rotation} (как в {@link Transfer#distributeAmong}).
     *
     * @param receivers приёмники ({@link #recording}-обёртки с биллингом пути)
     * @param paths     путь каждой позиции из {@code receivers} (null — прямой
     *                  сосед без проводов, ограничений сегментов нет)
     * @return сколько единиц суммарно принято (столько же списать из источника)
     */
    private static long distributeWithSegmentCaps(Level level, PipeType type,
                                                  TreeMap<Long, Transfer.Receiver> receivers,
                                                  Map<Long, List<PathStep>> paths,
                                                  long budget, long rotation) {
        List<Transfer.Receiver> list = new ArrayList<>(receivers.values());
        List<List<PathStep>> pathList = new ArrayList<>(list.size());
        for (Long key : receivers.keySet()) pathList.add(paths.get(key));

        int n = list.size();
        if (n == 0 || budget <= 0) return 0;

        long[] given = new long[n];
        boolean[] active = new boolean[n];
        for (int i = 0; i < n; i++) active[i] = true;
        int activeCount = n;
        long remaining = budget;

        // Остаток каждой позиции НА МОМЕНТ вызова: ledger не обновляется во время
        // раскладки (биллинг — в recording-обёртке, после). usage — сколько уже
        // разложено в этом вызове (для закона на общих сегментах).
        Map<Long, Long> rem0 = new HashMap<>();
        Map<Long, Long> usage = new HashMap<>();
        for (List<PathStep> path : pathList) {
            if (path == null) continue;
            for (PathStep s : path) {
                long key = s.pipe().asLong();
                rem0.putIfAbsent(key,
                    PipeFlowLedger.remaining(level, s.pipe(), level.getBlockState(s.pipe()), type));
                usage.putIfAbsent(key, 0L);
            }
        }

        // Рунды левелинга.
        while (remaining > 0 && activeCount > 0) {
            long x = remaining / activeCount;
            // Уровень не может поднять позицию выше её остатка, делённого на
            // число активных приёмников, идущих через неё.
            for (Long key : rem0.keySet()) {
                int count = 0;
                for (int i = 0; i < n; i++) {
                    if (active[i] && pathHas(pathList.get(i), key)) count++;
                }
                if (count > 0) {
                    long rem = rem0.get(key) - usage.get(key);
                    long lim = rem / count;
                    if (lim < x) x = lim;
                }
            }
            if (x <= 0) break;
            for (int i = 0; i < n; i++) {
                if (!active[i]) continue;
                given[i] += x;
                List<PathStep> path = pathList.get(i);
                if (path != null) {
                    for (PathStep s : path) usage.merge(s.pipe().asLong(), x, Long::sum);
                }
            }
            remaining -= x * activeCount;
            // Заморозить приёмников, чей путь уперся в исчерпанную позицию.
            for (int i = 0; i < n; i++) {
                if (!active[i]) continue;
                List<PathStep> path = pathList.get(i);
                if (path == null) continue;
                for (PathStep s : path) {
                    long key = s.pipe().asLong();
                    if (rem0.get(key) - usage.get(key) <= 0) {
                        active[i] = false;
                        activeCount--;
                        break;
                    }
                }
            }
        }

        // Остаток (меньше числа активных): по 1 единице с ротацией, пока есть
        // комната на сегментах. Прямые соседи (без пути) комнаты не теряют.
        int start = (int) Math.floorMod(rotation, n);
        while (remaining > 0) {
            long before = remaining;
            for (int k = 0; k < n && remaining > 0; k++) {
                int i = Math.floorMod(start + k, n);
                if (!active[i]) continue;
                List<PathStep> path = pathList.get(i);
                if (path == null) {
                    given[i]++;
                    remaining--;
                    continue;
                }
                boolean room = true;
                for (PathStep s : path) {
                    long key = s.pipe().asLong();
                    if (rem0.get(key) - usage.get(key) <= 0) {
                        room = false;
                        break;
                    }
                }
                if (!room) continue;
                for (PathStep s : path) usage.merge(s.pipe().asLong(), 1L, Long::sum);
                given[i]++;
                remaining--;
            }
            if (remaining == before) break;
        }

        long moved = 0;
        for (int i = 0; i < n; i++) {
            if (given[i] > 0) moved += list.get(i).receive(given[i], false);
        }
        return moved;
    }

    /** Проходит ли путь через позицию (asLong). */
    private static boolean pathHas(List<PathStep> path, long posKey) {
        if (path == null) return false;
        for (PathStep s : path) {
            if (s.pipe().asLong() == posKey) return true;
        }
        return false;
    }

    /** BFS по трубам от машины; наполняет {@code receivers} (и {@code paths}) машинами за трубами. */
    private static void collectThroughPipes(
            Level level, BlockPos fromPos, PipeType type,
            TreeMap<Long, Transfer.Receiver> receivers,
            Map<Long, List<PathStep>> paths,
            BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {

        // Родитель каждой посещённой трубы (труба ближе к машине), null у стартовых.
        // По нему восстанавливаем путь машина→…→труба для записи потока и потолка.
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
            // Порты турбины/парогенератора — это сами ноды, а не BlockEntity за
            // нодой. Регистрируем их как виртуальные приёмники, но продолжаем
            // BFS: та же нода остаётся нормальной частью ресурсной сети.
            if (type == PipeType.STEAM) {
                addTurbineSteamReceiver(level, pipe, fromPos, receivers, paths,
                    buildPath(level, pipe, pipe, parent));
            } else if (type == PipeType.WATER) {
                addSteamGenWaterReceiver(level, pipe, fromPos, receivers, paths,
                    buildPath(level, pipe, pipe, parent));
            } else if (type == PipeType.HEAT) {
                addSteamGenGthReceiver(level, pipe, fromPos, receivers, paths,
                    buildPath(level, pipe, pipe, parent));
            }
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
                addReceiver(level, npos, fromPos, receivers, paths, receiverOf, type, path);
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
            Map<Long, List<PathStep>> paths,
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
        if (path != null) paths.put(key, path);
    }

    /** Добавляет виртуальный SteamSink встроенного turbine port-а в обход без BE у ноды. */
    private static void addTurbineSteamReceiver(Level level, BlockPos pos, BlockPos fromPos,
                                                TreeMap<Long, Transfer.Receiver> receivers,
                                                Map<Long, List<PathStep>> paths,
                                                List<PathStep> path) {
        if (pos.equals(fromPos) || receivers.containsKey(pos.asLong())) return;
        Transfer.Receiver receiver = TurbineStructure.steamReceiverAt(level, pos);
        if (receiver == null) return;
        receivers.put(pos.asLong(), recording(level, receiver, PipeType.STEAM, path));
        if (path != null) paths.put(pos.asLong(), path);
    }

    /**
     * Виртуальный WaterSink водного порта продвинутого парогенератора: насос
     * «видит» ноду как обычную машину-приёмник за трубой.
     */
    private static void addSteamGenWaterReceiver(Level level, BlockPos pos, BlockPos fromPos,
                                                 TreeMap<Long, Transfer.Receiver> receivers,
                                                 Map<Long, List<PathStep>> paths,
                                                 List<PathStep> path) {
        if (pos.equals(fromPos) || receivers.containsKey(pos.asLong())) return;
        Transfer.Receiver receiver = SteamGenStructure.waterReceiverAt(level, pos);
        if (receiver == null) return;
        receivers.put(pos.asLong(), recording(level, receiver, PipeType.WATER, path));
        if (path != null) paths.put(pos.asLong(), path);
    }

    /**
     * Виртуальный GthSink теплового порта продвинутого парогенератора: топка
     * «видит» ноду как обычный тепловой потребитель за трубой.
     */
    private static void addSteamGenGthReceiver(Level level, BlockPos pos, BlockPos fromPos,
                                               TreeMap<Long, Transfer.Receiver> receivers,
                                               Map<Long, List<PathStep>> paths,
                                               List<PathStep> path) {
        if (pos.equals(fromPos) || receivers.containsKey(pos.asLong())) return;
        Transfer.Receiver receiver = SteamGenStructure.gthReceiverAt(level, pos);
        if (receiver == null) return;
        receivers.put(pos.asLong(), recording(level, receiver, PipeType.HEAT, path));
        if (path != null) paths.put(pos.asLong(), path);
    }

    /**
     * Обёртка-приёмник: сколько реально принято — столько же
     * <b>платят</b> все трубы на пути: {@link FlowTracker} (HUD, по сторонам) и
     * {@link PipeFlowLedger} (закон на трубе, по позициям). Биллинг фактического
     * принятого объёма, а не предложенного — если приёмник взял меньше (свой
     * intake), трубы за это не платят.
     */
    private static Transfer.Receiver recording(Level level, Transfer.Receiver real, PipeType type, List<PathStep> path) {
        return (amount, simulate) -> {
            long accepted = real.receive(amount, simulate);
            if (!simulate && accepted > 0) {
                for (PathStep s : path) {
                    FlowTracker.record(level, s.pipe(), type, s.out(), accepted);
                    PipeFlowLedger.add(level, s.pipe(), type, accepted);
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
