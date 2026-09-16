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
 * вычитается из остатка). Раскладка идёт по <b>дорожкам (lane)</b> — конкретным
 * маршрутам «entry-труба → … → приёмник», и из этого следуют обе картины:
 * <ul>
 *   <li><b>честные паралели:</b> приёмник, достижимый из НЕСКОЛЬКИХ entry-труб
 *       по НЕпересекающимся путям, получает через ВСЕ них (2 независимых провода
 *       от S к M — по 38 в каждом, M = 76/t у провода тир-1; 3 провода = 114);</li>
 *   <li><b>слияние в горлышко:</b> ветки, сходящиеся в общий сегмент, делят его
 *       остаток (Y: ствол 38 → по 19 на каждый конец; обратная Y: первый
 *       источник заполняет ствол на 38, второй отсечён — first-come-first-served
 *       по порядку тиков);</li>
 *   <li>механики в режиме авто, принимающие и отдающие одновременно, больше не
 *       гоняют встречные потоки через тот же провод сверх его предела.</li>
 * </ul>
 * Параллельные entry-трубы у ОДНОГО источника суммируются в бюджете (2 провода =
 * 2 канала по 38) — но каждый канал и каждый общий сегмент подчиняются закону.
 * <p>
 * Попутно каждому проводу на пути от машины к приёмнику записывается фактически
 * прошедший через него объём ({@link FlowTracker}) — по мировым сторонам, куда
 * ресурс вышел. Так HUD гаечного ключа показывает живые числа, и после закона
 * на трубе число на HUD НИКОГДА не превышает номинальный throughput трубы.
 * <p>
 * Обход выполняется лишь когда машине реально есть что слить (и провод рядом), а
 * не каждый тик у каждого провода. Стоимость зависит от размера цепи (один BFS
 * на entry-трубу; entry-труб ≤ 6).
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
     * Дорожка: конкретный маршрут ресурса к приёмнику. Приёмник, достижимый из
     * нескольких entry-труб, имеет несколько дорожек (честные паралели); суммарно
     * он ограничен собственным intake — «лишние» дорожки просто получают меньше.
     */
    private record Lane(Transfer.Receiver wrapped, List<PathStep> path) {
    }

    /**
     * Единая точка слива для машины: собрать дорожки (прямые соседи + маршруты по
     * проводам от каждой entry-трубы) и раздать по ним {@code budget} единиц
     * ({@link #distributeLanes} — max-min «левелинг» с законом на трубе). С
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

        // Entry-трубы — прилегающие трубы этого типа, чья грань принимает слив из
        // машины (AUTO/PULL). Параллельные entry-трубы — независимые каналы: бюджет
        // бьётся СУММОЙ их остатков (2 провода тир-1 = 76 GTU/t у источника), и каждая
        // становится стартом своих дорожек. Остатки, а не полные лимиты: всё, что уже
        // прошло через эти трубы в этом тике (другие источники), вычтено
        // ({@link PipeFlowLedger}).
        List<BlockPos> entries = new ArrayList<>();
        long entrySum = 0;
        for (Direction dir : Direction.values()) {
            BlockPos ppos = fromPos.relative(dir);
            BlockState pstate = level.getBlockState(ppos);
            if (!isPipe(pstate, type)) continue;
            if (!machineConnects(pstate, type, dir)) continue;
            if (!modeOf(pstate, type).acceptsFromMachine()) continue;
            entries.add(ppos);
            entrySum += PipeFlowLedger.remaining(level, ppos, pstate, type);
        }
        if (!entries.isEmpty()) {
            budget = Math.min(budget, entrySum);
            if (budget <= 0) return 0;
        }

        // Сырой sink приёмника — ОДИН на позицию и делится всеми дорожками к нему
        // (его собственный intake и ограничивает сумму по дорожкам). Дорожки же —
        // по (позиция, маршрут).
        Map<Long, Transfer.Receiver> rawByPos = new HashMap<>();
        Set<Long> direct = new HashSet<>();
        List<Lane> lanes = new ArrayList<>();

        // 1) Прямые соседи-приёмники (не трубы, не сам источник): одна дорожка без
        // сегментов — прямой поток через трубу не идёт. К той же позиции дорожки по
        // проводам уже не добавляются (прямая передача приоритетнее и не режется).
        for (Direction dir : Direction.values()) {
            BlockPos npos = fromPos.relative(dir);
            if (isPipe(level.getBlockState(npos), type)) continue;
            addDirectReceiver(level, npos, fromPos, rawByPos, direct, lanes, receiverOf);
        }

        // 2) Дорожки через провода: отдельный BFS от КАЖДОЙ entry-трубы. Приёмник,
        // достижимый из двух entry-труб, получает две дорожки (честные паралели);
        // общие сегменты между дорожками ограничивает закон при раскладке.
        for (BlockPos entry : entries) {
            collectLanes(level, fromPos, type, entry, receiverOf, rawByPos, direct, lanes);
        }

        if (lanes.isEmpty()) return 0;
        return distributeLanes(level, type, lanes, budget, rotation);
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
     * подчиняются тому же закону на трубе ({@link PipeFlowLedger}) в
     * {@link #distributeLanes}.</p>
     *
     * @param isMember проверка «принадлежит ли позиция этой установке» (порт сам
     *                 исключается по {@code !next.equals(port)})
     */
    public static long drainFromMultiblockPort(Level level, BlockPos port, PipeType type, long budget, long rotation,
                                               BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf,
                                               BiFunction<Level, BlockPos, Boolean> isMember) {
        if (budget <= 0 || !isPipe(level.getBlockState(port), type)) return 0;

        Map<Long, Transfer.Receiver> rawByPos = new HashMap<>();
        Set<Long> direct = new HashSet<>();
        List<Lane> lanes = new ArrayList<>();
        Map<Long, BlockPos> parent = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        parent.put(port.asLong(), null);
        queue.add(port);
        visited.add(port);

        while (!queue.isEmpty()) {
            BlockPos pipe = queue.poll();
            BlockState pstate = level.getBlockState(pipe);
            // Виртуальный приёмник встроенного порта (паровой порт турбины) —
            // нода сама является и трубой, и входом машины. Без этой проверки
            // парогенератор не смог бы кормить турбину: для BFS чужая нода —
            // просто ещё одна труба.
            if (type == PipeType.STEAM) {
                addTurbineLane(level, pipe, port, buildPath(level, pipe, pipe, parent), rawByPos, direct, lanes);
            }
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
                addMachineLane(level, next, port, type, receiverOf, path, rawByPos, direct, lanes);
            }
        }
        if (lanes.isEmpty()) return 0;
        return distributeLanes(level, type, lanes, budget, rotation);
    }

    /**
     * Прямой сосед (не труба): если это приёмник — сырой sink + дорожка БЕЗ
     * сегментов (прямая передача, лимитом трубы не режется).
     */
    private static void addDirectReceiver(Level level, BlockPos pos, BlockPos fromPos,
                                          Map<Long, Transfer.Receiver> rawByPos,
                                          Set<Long> direct, List<Lane> lanes,
                                          BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf) {
        if (pos.equals(fromPos) || direct.contains(pos.asLong())) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        Transfer.Receiver raw = receiverOf.apply(be, pos);
        if (raw == null) return;
        rawByPos.put(pos.asLong(), raw);
        direct.add(pos.asLong());
        lanes.add(new Lane(raw, null));
    }

    /**
     * BFS от entry-трубы; каждый найденный приёмник — его дорожка (маршрут
     * entry→…→приёмник). Позиции с прямой дорожкой пропускаются (прямой поток
     * через трубу не идёт).
     */
    private static void collectLanes(Level level, BlockPos fromPos, PipeType type, BlockPos entry,
                                     BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf,
                                     Map<Long, Transfer.Receiver> rawByPos,
                                     Set<Long> direct, List<Lane> lanes) {
        // Родитель каждой посещённой трубы (труба ближе к entry), null у самой entry.
        Map<Long, BlockPos> parent = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        parent.put(entry.asLong(), null);
        queue.add(entry);
        visited.add(entry);

        while (!queue.isEmpty()) {
            BlockPos pipe = queue.poll();
            BlockState pstate = level.getBlockState(pipe);
            // Порты турбины/парогенератора — это сами ноды, а не BlockEntity за
            // нодой. Регистрируем их как дорожки-приёмники, но продолжаем BFS:
            // та же нода остаётся нормальной частью ресурсной сети.
            List<PathStep> selfPath = buildPath(level, pipe, pipe, parent);
            if (type == PipeType.STEAM) {
                addTurbineLane(level, pipe, fromPos, selfPath, rawByPos, direct, lanes);
            } else if (type == PipeType.WATER) {
                addSteamGenWaterLane(level, pipe, fromPos, selfPath, rawByPos, direct, lanes);
            } else if (type == PipeType.HEAT) {
                addSteamGenGthLane(level, pipe, fromPos, selfPath, rawByPos, direct, lanes);
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
                addMachineLane(level, npos, fromPos, type, receiverOf, path, rawByPos, direct, lanes);
            }
        }
    }

    /**
     * Машина за трубой: дорожка по маршруту (если позиция без прямой дорожки и
     * машина — приёмник). Сырой sink кэшируется по позиции — несколько дорожек к
     * одному приёмнику делят его intake.
     */
    private static void addMachineLane(Level level, BlockPos pos, BlockPos fromPos, PipeType type,
                                       BiFunction<BlockEntity, BlockPos, Transfer.Receiver> receiverOf,
                                       List<PathStep> path,
                                       Map<Long, Transfer.Receiver> rawByPos,
                                       Set<Long> direct, List<Lane> lanes) {
        if (pos.equals(fromPos) || direct.contains(pos.asLong())) return;
        long key = pos.asLong();
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return;
        Transfer.Receiver raw = rawByPos.get(key);
        if (raw == null) {
            raw = receiverOf.apply(be, pos);
            if (raw == null) return;
            rawByPos.put(key, raw);
        }
        lanes.add(new Lane(recording(level, raw, type, path), path));
    }

    /** Дорожка виртуального SteamSink встроенного turbine port-а (без BE у ноды). */
    private static void addTurbineLane(Level level, BlockPos pos, BlockPos fromPos, List<PathStep> path,
                                       Map<Long, Transfer.Receiver> rawByPos, Set<Long> direct, List<Lane> lanes) {
        if (pos.equals(fromPos) || direct.contains(pos.asLong())) return;
        long key = pos.asLong();
        Transfer.Receiver raw = rawByPos.get(key);
        if (raw == null) {
            raw = TurbineStructure.steamReceiverAt(level, pos);
            if (raw == null) return;
            rawByPos.put(key, raw);
        }
        lanes.add(new Lane(recording(level, raw, PipeType.STEAM, path), path));
    }

    /**
     * Дорожка виртуального WaterSink водного порта продвинутого парогенератора:
     * насос «видит» ноду как обычный приёмник за трубой.
     */
    private static void addSteamGenWaterLane(Level level, BlockPos pos, BlockPos fromPos, List<PathStep> path,
                                             Map<Long, Transfer.Receiver> rawByPos, Set<Long> direct, List<Lane> lanes) {
        if (pos.equals(fromPos) || direct.contains(pos.asLong())) return;
        long key = pos.asLong();
        Transfer.Receiver raw = rawByPos.get(key);
        if (raw == null) {
            raw = SteamGenStructure.waterReceiverAt(level, pos);
            if (raw == null) return;
            rawByPos.put(key, raw);
        }
        lanes.add(new Lane(recording(level, raw, PipeType.WATER, path), path));
    }

    /**
     * Дорожка виртуального GthSink теплового порта продвинутого парогенератора:
     * топка «видит» ноду как обычный тепловой потребитель за трубой.
     */
    private static void addSteamGenGthLane(Level level, BlockPos pos, BlockPos fromPos, List<PathStep> path,
                                           Map<Long, Transfer.Receiver> rawByPos, Set<Long> direct, List<Lane> lanes) {
        if (pos.equals(fromPos) || direct.contains(pos.asLong())) return;
        long key = pos.asLong();
        Transfer.Receiver raw = rawByPos.get(key);
        if (raw == null) {
            raw = SteamGenStructure.gthReceiverAt(level, pos);
            if (raw == null) return;
            rawByPos.put(key, raw);
        }
        lanes.add(new Lane(recording(level, raw, PipeType.HEAT, path), path));
    }

    /**
     * «Левелинг» (max-min fair split) с законом на трубе, но по ДОРОЖКАМ:
     * равномерно поднять уровень ВСЕХ активных дорожек, но уровень ограничен:
     * <ul>
     *   <li>остатком бюджета / числом активных дорожек (честное деление);</li>
     *   <li>для КАЖДОЙ позиции трубы: остатком её пропускной способности
     *       / числом активных дорожек, чей маршрут через неё идёт. Это и есть
     *       закон «общий сегмент не берёт больше, чем пропустит»: две дорожки на
     *       общем стволе 38 получают по 19 (уровень бьётся 38/2), а две
     *       НЕпересекающиеся дорожки — по 38 каждая (честные паралели).</li>
     * </ul>
     * Позиция, у которой остаток исчерпан, «замораживает» идущие через неё
     * дорожки (они не могут взять больше) — уровень продолжает расти у
     * остальных. Рунд заканчивается исчерпанием бюджета либо полным
     * замораживанием; рундов ≤ 2n+1 (каждый рунд либо замораживает ≥1 дорожку,
     * либо добирает остаток), стоимость O(n × длина маршрутов) — для реальных
     * сетей ничтожно.
     * <p>
     * Порядок дорожек стабилен (порядок обхода), ротация остатка —
     * {@code rotation}. Выдача дорожкам последовательная: приёмник, чей intake
     * меньше суммы его дорожек, берёт меньше — поздние дорожки просто получают
     * 0, и трубы за это «не платят» (биллинг фактического в {@link #recording}).
     *
     * @param lanes дорожки (обёртки с биллингом маршрута; path = null — прямой
     *              сосед без сегментов)
     * @return сколько единиц суммарно принято (столько же списать из источника)
     */
    private static long distributeLanes(Level level, PipeType type, List<Lane> lanes, long budget, long rotation) {
        int n = lanes.size();
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
        for (Lane lane : lanes) {
            if (lane.path() == null) continue;
            for (PathStep s : lane.path()) {
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
            // число активных дорожек, идущих через неё.
            for (Long key : rem0.keySet()) {
                int count = 0;
                for (int i = 0; i < n; i++) {
                    if (active[i] && laneHas(lanes.get(i), key)) count++;
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
                List<PathStep> path = lanes.get(i).path();
                if (path != null) {
                    for (PathStep s : path) usage.merge(s.pipe().asLong(), x, Long::sum);
                }
            }
            remaining -= x * activeCount;
            // Заморозить дорожки, чей маршрут уперся в исчерпанную позицию.
            for (int i = 0; i < n; i++) {
                if (!active[i]) continue;
                List<PathStep> path = lanes.get(i).path();
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
                List<PathStep> path = lanes.get(i).path();
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
            if (given[i] > 0) moved += lanes.get(i).wrapped().receive(given[i], false);
        }
        return moved;
    }

    /** Проходит ли маршрут дорожки через позицию (asLong). */
    private static boolean laneHas(Lane lane, long posKey) {
        if (lane.path() == null) return false;
        for (PathStep s : lane.path()) {
            if (s.pipe().asLong() == posKey) return true;
        }
        return false;
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

    /**
     * Обёртка-приёмник: сколько реально принято — столько же
     * <b>платят</b> все трубы на маршруте: {@link FlowTracker} (HUD, по сторонам)
     * и {@link PipeFlowLedger} (закон на трубе, по позициям). Биллинг фактического
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
