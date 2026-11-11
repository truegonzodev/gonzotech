package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Маршрутизация ПРЕДМЕТОВ по предметным трубам ({@link PipeType#ITEM}) —
 * МГНОВЕННО, БЕЗ буферов в трубах.
 * <p>
 * Модель — та же, что у жидкостей/энергии ({@link PipeRouting}): труба ничего не
 * хранит. Разница лишь в том, что у сундуков нет «добровольного слива», как у
 * машин, поэтому <b>тянут сами трубы/узлы</b>: грань предметной трубы в режиме
 * {@link PipeMode#acceptsFromMachine ЗАБОР/АВТО}, прилегающая к контейнеру,
 * каждый тик вынимает до {@link PipeType#maxThroughput()} предметов и тут же
 * раскидывает их по достижимым приёмникам (грань трубы в режиме
 * {@link PipeMode#deliversToMachine ОТДАЧА/АВТО} у контейнера со свободным
 * местом). Обход — BFS по связной цепи предметных труб, ровно как в
 * {@link PipeRouting}.
 * <p>
 * <b>Почему это решает «проблему воронок».</b> Предметы никогда не «лежат» в
 * трубах — за тик они уходят источник→приёмник. Забиваться нечему, при ломании
 * трубы выпадает только сама труба (внутри пусто). Если приёмника нет / все полны
 * — предмет просто НЕ извлекается и остаётся в источнике (обратное давление).
 * <p>
 * <b>Равномерность.</b> Предметы дискретны, поэтому раздаём их приёмникам по
 * одному по кругу (rotation по gameTime) — прямой аналог
 * {@code Transfer.distributeAmong} для неделимой единицы.
 * <p>
 * Трубы остаются пассивными блоками без {@code BlockEntity}: extract-проход
 * запускается запланированным тиком блока (см. {@code ItemPipeBlock}).
 */
public final class ItemRouting {

    private ItemRouting() {
    }

    /** Тип, которым оперирует этот маршрутизатор. */
    private static final PipeType T = PipeType.ITEM;

    /**
     * Предел на ОДИН вид предмета за тик через точку забора. Суммарный лимит —
     * {@link PipeType#maxThroughput()} (5 шт/т), но каждый конкретный вид едет не
     * быстрее 1 шт/т. Следствие: одновременно через трубу проходит максимум 5
     * разных видов.
     */
    static final int PER_ITEM_TICK_CAP = 1;

    /**
     * Один проход извлечения для трубы/узла в {@code pos}: по всем граням, где
     * труба открыта, режим позволяет забор из машины/контейнера и рядом есть
     * контейнер-источник — вынимаем до общего лимита {@link PipeType#maxThroughput()}
     * предметов за тик и раскидываем их по сети.
     *
     * @return сколько предметов суммарно перемещено за этот тик
     */
    // One tick of directed transfer history per world. Values contain only primitive
    // addresses/UUIDs, never a Container -> Level reference back to the weak key.
    private static final Map<Level, NodeTickBudget> NODE_BUDGETS = new java.util.WeakHashMap<>();
    private static final class NodeTickBudget {
        long tick = Long.MIN_VALUE;
        final Map<Long, Map<net.minecraft.world.item.Item, Integer>> nodes = new HashMap<>();
        Map<net.minecraft.world.item.Item, Integer> at(long now, BlockPos pos) {
            if (tick != now) { nodes.clear(); tick = now; }
            return nodes.computeIfAbsent(pos.asLong(), k -> new HashMap<>());
        }
    }

    /** Explicit lifecycle cleanup: cached containers can retain their world. */
    public static void closeLevel(Level level) {
        TRANSFER_GUARDS.remove(level); NODE_BUDGETS.remove(level);
        TOPO_CACHE.remove(level);
    }
    public static void clearAll() {
        TRANSFER_GUARDS.clear(); NODE_BUDGETS.clear(); TOPO_CACHE.clear();
    }

    private static final Map<Level, ItemTransferGuard> TRANSFER_GUARDS = new java.util.WeakHashMap<>();

    /**
     * 0.3.96: есть ли в источнике хоть один извлекаемый предмет. Раньше BFS
     * приёмников гонялся на каждую грань КАЖДОГО тика даже для пустых сундуков и
     * топок — на куче универсальных узлов это тысячи чтений блокстейтов/BE за тик.
     */
    static boolean hasExtractableItem(Container src, Direction face) {
        for (int slot : extractableSlots(src, face)) {
            ItemStack stack = src.getItem(slot);
            if (!stack.isEmpty() && canTake(src, slot, stack, face)) return true;
        }
        return false;
    }

    /** 0.3.96: после этого числа посещённых BFS-позиций узел помечается «горячим». */
    public static final int HOT_NODE_BFS = 128;

    public static int tickExtract(Level level, BlockPos pos, BlockState state) {
        long start = PipeBenchmark.clock();
        PipeBenchmark.add(PipeBenchmark.Metric.ITEM_CALLS, 1);
        try {
            int moved = tickExtractImpl(level, pos, state);
            PipeBenchmark.add(PipeBenchmark.Metric.NODE_ITEMS, moved); return moved;
        } finally { PipeBenchmark.elapsed(PipeBenchmark.Metric.ITEM_NS, start); }
    }
    private static int tickExtractImpl(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !level.hasChunkAt(pos)) return 0;
        if (!(state.getBlock() instanceof PipeCarrier carrier)) return 0;
        if (!carrier.carries(state, T)) return 0;

        // Пропускная у этой точки забора = базовая × коэффициент блока
        // (универсальный узел = 0.9, обычные трубы/узлы = 1.0). Минимум 1.
        double factor = carrier.throughputFactor(state, T);
        int budget = (int) Math.max(1, Math.floor(carrier.throughputLimit(state, T) * factor));
        int perItemCap = Math.max(1, carrier.perItemThroughputLimit(state, T));
        if (budget <= 0) return 0;

        long now = level.getGameTime();
        boolean diagnostics = PipeRoutingDiagnostics.itemEnabled(level);
        if (diagnostics) PipeRoutingDiagnostics.itemEvent(level, pos, null, "tick", "state=" + state);

        // Фаза 1 (0.3.96): собираем грани-источники, где ЕСТЬ что извлекать, и
        // Возврат/циклы проверяются по истории конкретных направлений, не по факту приёма.
        List<Object[]> faces = new ArrayList<>(); // {BlockPos srcPos, Direction srcFace, Container src}
        for (Direction dir : Direction.values()) {
            if (!carrier.opensToward(state, T, dir)) continue;
            if (!carrier.modeFor(state, T).acceptsFromMachine()) continue;

            BlockPos srcPos = pos.relative(dir);
            // Сторона источника, обращённая к трубе (для WorldlyContainer-доступа).
            Direction srcFace = dir.getOpposite();
            Container src = containerAt(level, srcPos);
            if (src == null) {
                if (diagnostics) PipeRoutingDiagnostics.itemEvent(level, pos, srcPos, "source-missing", "loaded=" + level.hasChunkAt(srcPos));
                continue;
            }
            if (!hasExtractableItem(src, srcFace)) {
                if (diagnostics) PipeRoutingDiagnostics.itemEvent(level, pos, srcPos, "source-empty-or-sided-denied", src.getClass().getName());
                continue;
            }
            if (diagnostics) PipeRoutingDiagnostics.itemEvent(level, pos, srcPos, "source-ready", src.getClass().getName() + " slots=" + src.getContainerSize());
            faces.add(new Object[]{srcPos, srcFace, src});
        }
        if (faces.isEmpty()) return 0;

        // Фаза 2 (0.3.96): ОДИН обход сети на узел за тик (раньше — до шести,
        // по грани на каждую). Множество приёмников от грани не зависит: BFS
        // стартует от узла, исключение источника делаем при вставке.
        // 0.3.338: complete route plan from the shared, resumable scheduler.
        List<Sink> sinks = sinksCached(level, pos);
        if (sinks.isEmpty()) {
            if (diagnostics) PipeRoutingDiagnostics.itemEvent(level, pos, null, "no-discovered-sinks", "sources=" + faces.size());
            return 0;
        }

        Map<net.minecraft.world.item.Item, Integer> allowance = NODE_BUDGETS.computeIfAbsent(level, k -> new NodeTickBudget()).at(now, pos);
        for (int used : allowance.values()) budget -= used;
        if (budget <= 0) return 0;
        int moved = 0;
        for (Object[] face : faces) {
            if (budget <= 0) break;
            BlockPos srcPos = (BlockPos) face[0];
            Direction srcFace = (Direction) face[1];
            Container src = (Container) face[2];
            int done = pushItems(level, pos, src, srcFace, sinks, srcPos, budget, perItemCap, now);
            moved += done;
            budget -= done;
        }
        if (diagnostics) PipeRoutingDiagnostics.itemEvent(level, pos, null, "result", "moved=" + moved + " sources=" + faces.size() + " discoveredSinks=" + sinks.size());
        return moved;
    }

    // ─────────────────────────── извлечение ↔ вставка ───────────────────────────

    /**
     * Тянет из источника предметы и раздаёт приёмникам по кругу, пока не исчерпан
     * бюджет. «Умное» поведение: перебираем ВСЕ извлекаемые слоты источника, и
     * если конкретный предмет никуда не влезает — не встаём, а пробуем следующий
     * предмет источника. Так труба не стопорится из-за того, что первым в сундуке
     * лежит предмет, которому нет места в приёмнике (см. баг «камень блокирует
     * уголь»). Возвращает число перемещённых.
     */
    private static int pushItems(Level level, BlockPos pipePos, Container src, Direction srcFace,
                                 List<Sink> sinks, BlockPos excludeSrc, int budget, int perItemCap, long now) {
        // The complete plan is revoked by geometry/chunk/BE edits. Endpoint
        // identity remains a live check; no stale lease authorizes an insertion.
        if (!sameContainerAt(level, excludeSrc, src)) {
            if (PipeRoutingDiagnostics.itemEnabled(level)) PipeRoutingDiagnostics.itemEvent(level, pipePos, excludeSrc, "source-identity-rejected", src.getClass().getName());
            return 0;
        }
        ItemTransferGuard guard = TRANSFER_GUARDS.computeIfAbsent(level, key -> new ItemTransferGuard());
        List<ItemTransferGuard.Owner> sourceOwners = inventoryOwners(level, excludeSrc, src);
        sinks = liveSinks(level, pipePos, sinks);
        // Different halves/wrappers can still be the same source inventory.
        sinks = sinks.stream().filter(sink -> !sameInventory(level, src, sink.container(), sink.containerPos())).toList();
        if (sinks.isEmpty()) {
            if (PipeRoutingDiagnostics.itemEnabled(level)) PipeRoutingDiagnostics.itemEvent(level, pipePos, excludeSrc, "no-live-external-sinks", src.getClass().getName());
            return 0;
        }
        int moved = 0;
        long rotation = level.getGameTime();
        int n = sinks.size();
        // Сколько уже перемещено КАЖДОГО вида за этот тик (для лимита PER_ITEM_TICK_CAP).
        java.util.Map<net.minecraft.world.item.Item, Integer> perItem = NODE_BUDGETS.computeIfAbsent(level, k -> new NodeTickBudget()).at(now, pipePos);

        for (int slot : extractableSlots(src, srcFace)) {
            if (moved >= budget) break;

            // Двигаем из этого слота столько, сколько сможем (в пределах бюджета).
            while (moved < budget) {
                ItemStack cur = src.getItem(slot);
                if (cur.isEmpty()) break;
                if (!canTake(src, slot, cur, srcFace)) break;
                // Лимит на этот вид уже исчерпан за тик — к следующему слоту.
                if (perItem.getOrDefault(cur.getItem(), 0) >= perItemCap) break;

                ItemStack one = cur.copy();
                one.setCount(1);

                boolean placed = false;
                for (int k = 0; k < n; k++) {
                    int idx = (int) Math.floorMod(rotation + moved + k, n);
                    Sink sink = sinks.get(idx);
                    // A receiver is not locked by another input. Only a return/cycle
                    // through this tick's accepted transfer edges is forbidden.
                    if (sink.containerPos().equals(excludeSrc)) continue;
                    List<ItemTransferGuard.Owner> targetOwners = inventoryOwners(level, sink.containerPos(), sink.container());
                    if (!guard.allows(now, sourceOwners, targetOwners)) {
                        if (PipeRoutingDiagnostics.itemEnabled(level)) PipeRoutingDiagnostics.itemEvent(level, pipePos, sink.containerPos(), "return-cycle-or-owner-unavailable", "source=" + excludeSrc);
                        continue;
                    }
                    if (!sink.plan().current() || !sameContainerAt(level, excludeSrc, src)
                        || !sameContainerAt(level, sink.containerPos(), sink.container())) continue;
                    if (insertOne(sink.container, sink.face, one)) {
                        src.removeItem(slot, 1);
                        src.setChanged();
                        guard.record(now, sourceOwners, targetOwners);
                        moved++;
                        perItem.merge(one.getItem(), 1, Integer::sum);
                        placed = true;
                        // Учёт на КАЖДОЙ трубе фактически выбранного пути, а не
                        // только у точки забора. Так транзит через Universal Node
                        // считается реальным item-потоком и для его компаратора.
                        for (BlockPos pipe : sink.path()) {
                            ItemFlowTracker.record(level, pipe, one.getItem(), 1);
                        }
                        break;
                    }
                }
                // Этому предмету некуда — НЕ стопорим трубу, идём к след. слоту.
                if (!placed) break;
            }
        }
        return moved;
    }

    /** Все слоты источника, из которых можно вынимать (с учётом грани), в порядке обхода. */
    static List<Integer> extractableSlots(Container src, Direction face) {
        List<Integer> out = new ArrayList<>();
        if (src instanceof WorldlyContainer wc) {
            for (int slot : wc.getSlotsForFace(face)) out.add(slot);
            return out;
        }
        for (int slot = 0; slot < src.getContainerSize(); slot++) out.add(slot);
        return out;
    }

    /** Можно ли вынуть предмет из слота источника (с учётом грани для sided-инвентаря). */
    static boolean canTake(Container src, int slot, ItemStack stack, Direction face) {
        if (src instanceof WorldlyContainer wc) {
            return wc.canTakeItemThroughFace(slot, stack, face);
        }
        return true;
    }

    /** Кладёт ровно 1 предмет в приёмник (с учётом грани), стакая где можно. Возвращает успех. */
    static boolean insertOne(Container dst, Direction face, ItemStack one) {
        if (dst instanceof WorldlyContainer wc) {
            for (int slot : wc.getSlotsForFace(face)) {
                if (!wc.canPlaceItemThroughFace(slot, one, face)) continue;
                if (tryPlace(wc, slot, one)) return true;
            }
            return false;
        }
        for (int slot = 0; slot < dst.getContainerSize(); slot++) {
            if (!dst.canPlaceItem(slot, one)) continue;
            if (tryPlace(dst, slot, one)) return true;
        }
        return false;
    }

    /** Пытается положить 1 предмет в конкретный слот (стак или пустой). */
    private static boolean tryPlace(Container dst, int slot, ItemStack one) {
        int limit = Math.min(one.getMaxStackSize(),
            com.gonzotech.machines.inventory.SlotCapacity.limit(dst, slot));
        if (limit <= 0) return false;
        ItemStack cur = dst.getItem(slot);
        if (cur.isEmpty()) {
            ItemStack put = one.copy();
            put.setCount(1);
            dst.setItem(slot, put);
            dst.setChanged();
            return true;
        }
        if (ItemStack.isSameItemSameComponents(cur, one)
            && cur.getCount() < Math.min(cur.getMaxStackSize(), limit)) {
            com.gonzotech.radiation.engine.ExternalRadiation.merge(cur, one, 1);
            cur.grow(1);
            dst.setChanged();
            return true;
        }
        return false;
    }

    // ─────────────────────────── обход сети (BFS) ───────────────────────────

    /** Приёмник и точный маршрут труб, по которому предмет физически был передан. */
    private record Sink(Container container, Direction face, List<BlockPos> path, BlockPos containerPos, PipeNetworkService.Plan plan) {
    }

    static final int TOPO_TTL_TICKS = 40;
    private record CachedSinks(List<Sink> sinks, long builtTick, PipeNetworkService.Plan plan) {}
    private static final Map<Level, Map<Long, CachedSinks>> TOPO_CACHE = new java.util.WeakHashMap<>();

    /** Compatibility/debug invalidation; ordinary edits use the spatial service hook. */
    public static void onWorldEdit(Level level) {
        PipeNetworkService.invalidateAll(level);
        TOPO_CACHE.remove(level);
    }
    private static List<Sink> sinksCached(Level level, BlockPos startPipe) {
        PipeNetworkService.Plan plan = PipeNetworkService.requestItems(level, startPipe, false);
        if (PipeRoutingDiagnostics.itemEnabled(level)) PipeRoutingDiagnostics.itemEvent(level, startPipe, null,
            "cache", plan == null ? "pending jobs=" + PipeNetworkService.pendingJobs() : "ready visited=" + plan.visited());
        if (plan == null) return List.of();
        long now = level.getGameTime();
        Map<Long, CachedSinks> cache = TOPO_CACHE.computeIfAbsent(level, k -> new HashMap<>());
        CachedSinks old = cache.get(startPipe.asLong());
        if (old != null && old.plan() == plan && now - old.builtTick() <= TOPO_TTL_TICKS) return old.sinks();
        if (plan.visited() >= HOT_NODE_BFS) PipeFlowWarnings.mark(level, startPipe, now + PipeFlowWarnings.HOLD_TICKS);
        List<Sink> sinks = new ArrayList<>();
        for (PipeNetworkService.Outlet outlet : plan.outlets()) {
            Container c = containerAt(level, outlet.target());
            if (c != null) sinks.add(new Sink(c, outlet.face(), plan.pathTo(outlet.pipe()), outlet.target(), plan));
        }
        if (!plan.current()) return List.of();
        cache.put(startPipe.asLong(), new CachedSinks(sinks, now, plan));
        // Incremental/idle eviction is performed by the lifecycle hook, never clear-all on size.
        return sinks;
    }

    public static void trimCache(Level level) {
        Map<Long, CachedSinks> cache = TOPO_CACHE.get(level);
        if (cache != null) cache.values().removeIf(c -> !c.plan().current() || level.getGameTime() - c.builtTick() > 200);
    }

    /**
     * 0.3.306: фильтрует кэшированный список приёмников против ЖИВОГО мира и
     * возвращает актуальный (или пустой). При любом отсеве сбрасывает запись
     * кэша стартовой трубы — молча ездить по протухшему маршруту сеть не будет.
     */
    private static List<Sink> liveSinks(Level level, BlockPos startPipe, List<Sink> cached) {
        Map<Long, CachedSinks> owner = TOPO_CACHE.get(level);
        CachedSinks lease = owner == null ? null : owner.get(startPipe.asLong());
        if (lease != null && !lease.plan().current()) return List.of();
        List<Sink> live = new ArrayList<>(cached.size());
        boolean dropped = false;
        for (Sink sink : cached) {
            if (sinkLive(level, sink)) live.add(sink);
            else dropped = true;
        }
        if (dropped) {
            java.util.Map<Long, CachedSinks> perLevel = TOPO_CACHE.get(level);
            if (perLevel != null) perLevel.remove(startPipe.asLong());
        }
        if (lease != null && !lease.plan().current()) return List.of();
        return dropped ? live : cached;
    }

    /** Маршрут цел (трубы на месте и соединены) и приёмник — тот же контейнер там же. */
    private static boolean sinkLive(Level level, Sink sink) {
        if (!sink.plan().current()) return false;
        List<BlockPos> path = sink.path();
        BlockPos lastPipe = path.get(path.size() - 1);
        if (!level.hasChunkAt(lastPipe)) return false;
        BlockState last = level.getBlockState(lastPipe);
        Direction toContainer = dirBetween(lastPipe, sink.containerPos());
        if (!isItemPipe(last) || !opensToward(last, toContainer) || !modeOf(last).deliversToMachine()) return false;
        return sameContainerAt(level, sink.containerPos(), sink.container());
    }

    /** Направление между двумя соседними клетками (маршруты BFS всегда соседние). */
    private static Direction dirBetween(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY(), dz = to.getZ() - from.getZ();
        for (Direction d : Direction.values()) {
            if (d.getStepX() == dx && d.getStepY() == dy && d.getStepZ() == dz) return d;
        }
        throw new IllegalStateException("ItemRouting: не соседние клетки маршрута " + from + "→" + to);
    }

    // ─────────────────────────── мелкие помощники ───────────────────────────

    private static boolean isItemPipe(BlockState state) {
        return state.getBlock() instanceof PipeCarrier c && c.carries(state, T);
    }

    private static boolean opensToward(BlockState state, Direction dir) {
        return state.getBlock() instanceof PipeCarrier c && c.opensToward(state, T, dir);
    }

    private static boolean pipesConnect(BlockState a, BlockState b, Direction dirAtoB) {
        return opensToward(a, dirAtoB) && opensToward(b, dirAtoB.getOpposite());
    }

    private static PipeMode modeOf(BlockState state) {
        return state.getBlock() instanceof PipeCarrier c ? c.modeFor(state, T) : PipeMode.AUTO;
    }

    /**
     * A fresh vanilla compound wrapper is not a new inventory. Both CURRENT
     * physical block containers must be present in both views: coordinates or
     * size alone must never admit a replacement BE or a split/re-paired chest.
     * Other containers retain exact identity (including container entities).
     */
    static boolean sameContainerAt(Level level, BlockPos pos, Container expected) {
        Container live = containerAt(level, pos);
        if (PipeRoutingDiagnostics.itemEnabled(level)) PipeRoutingDiagnostics.itemEvent(level, pos, null, "identity-types",
            "expected=" + expected.getClass().getName() + " live=" + (live == null ? "null" : live.getClass().getName()));
        if (live == null) return false;
        if (live == expected) return true;
        if (!(live instanceof CompoundContainer current) || !(expected instanceof CompoundContainer previous)) return false;
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return false;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.hasChunkAt(other)) return false;
        if (!(level.getBlockEntity(pos) instanceof Container first)
            || !(level.getBlockEntity(other) instanceof Container second) || first == second) return false;
        if (PipeRoutingDiagnostics.itemEnabled(level)) PipeRoutingDiagnostics.itemEvent(level, pos, other, "compound-owners",
            "near=" + first.getClass().getName() + " far=" + second.getClass().getName()
                + " previous=" + previous.contains(first) + "/" + previous.contains(second)
                + " current=" + current.contains(first) + "/" + current.contains(second));
        return previous.contains(first) && previous.contains(second)
            && current.contains(first) && current.contains(second);
    }

    /** Physical alias exclusion, without ordering slots or retaining a new owner cache. */
    static boolean sameInventory(Level level, Container source, Container target, BlockPos targetPos) {
        if (source == target) return true;
        if (source instanceof CompoundContainer compound && compound.contains(target)) return true;
        if (target instanceof CompoundContainer compound && compound.contains(source)) return true;
        if (source instanceof CompoundContainer from && target instanceof CompoundContainer to) {
            if (!level.hasChunkAt(targetPos)) return true; // unavailable is never a transfer target
            return level.getBlockEntity(targetPos) instanceof Container owner
                && from.contains(owner) && to.contains(owner);
        }
        return false;
    }

    /** A checked forward transfer shared with filter routing; no world/container retention. */
    record TransferAttempt(ItemTransferGuard guard, long tick, List<ItemTransferGuard.Owner> source,
                           List<ItemTransferGuard.Owner> target) {
        boolean allowed() { return guard.allows(tick, source, target); }
        void record() { guard.record(tick, source, target); }
    }

    static TransferAttempt transferAttempt(Level level, BlockPos sourcePos, Container source,
                                           BlockPos targetPos, Container target) {
        ItemTransferGuard guard = TRANSFER_GUARDS.computeIfAbsent(level, key -> new ItemTransferGuard());
        return new TransferAttempt(guard, level.getGameTime(), inventoryOwners(level, sourcePos, source),
            inventoryOwners(level, targetPos, target));
    }

    /** History keys are addresses/UUIDs, not retained physical inventories.
     * Live BE identity is checked independently by sameContainerAt. A replaced
     * block at the same address can only receive a conservative same-tick denial.
     */
    private static List<ItemTransferGuard.Owner> inventoryOwners(Level level, BlockPos pos, Container container) {
        if (!level.hasChunkAt(pos)) return List.of();
        if (container instanceof net.minecraft.world.entity.Entity entity) {
            return List.of(ItemTransferGuard.Owner.entity(entity.getUUID()));
        }
        if (!(container instanceof CompoundContainer compound)) return List.of(ItemTransferGuard.Owner.block(pos.asLong()));
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return List.of();
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.hasChunkAt(other)
            || !(level.getBlockEntity(pos) instanceof Container first)
            || !(level.getBlockEntity(other) instanceof Container second)
            || first == second || !compound.contains(first) || !compound.contains(second)) return List.of();
        return List.of(ItemTransferGuard.Owner.block(pos.asLong()), ItemTransferGuard.Owner.block(other.asLong()));
    }

    /** Never let vanilla double-chest lookup read an unloaded companion chunk. */
    static boolean containerLookupLoaded(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        return !(state.getBlock() instanceof ChestBlock)
            || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE
            || level.hasChunkAt(pos.relative(ChestBlock.getConnectedDirection(state)));
    }

    /**
     * Контейнер в позиции {@code pos} (сундук/бочка/машина/двойной сундук/вагонетка).
     * Делегируем ванильной логике воронки — она корректно собирает двойные сундуки
     * и контейнер-сущности. Возвращает {@code null}, если контейнера нет.
     */
    static Container containerAt(Level level, BlockPos pos) {
        if (!containerLookupLoaded(level, pos)) return null;
        return HopperBlockEntity.getContainerAt(level, pos);
    }
}
