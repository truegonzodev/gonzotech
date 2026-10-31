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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
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
    /**
     * 0.3.96: щит пинг-понга — позиция контейнера → тик последней ВСТАВКИ.
     * Из контейнера, в который в этом же тике что-то вставили, не тянем: два
     * соседних узла иначе гоняют один предмет туда-обратно (топка A → топка B →
     * топка A) с setChanged-штормом и бесконечно грязными чанками — это и были
     * «лаг-вейвы» автора на куче узлов. Чистится при записи (правило статики).
     * 0.3.299 (N14): таблица разделена по измерениям — asLong() позиции в
     * Незере и обычном мире сталкиваются, и щит на зеркальных координатах
     * глушил забор там, где конфликта нет.
     */
    private static final java.util.Map<Level, java.util.Map<Long, Long>> LAST_INSERTED =
        new java.util.WeakHashMap<>();

    private static void noteInserted(Level level, long now, BlockPos containerPos) {
        java.util.Map<Long, Long> perDim =
            LAST_INSERTED.computeIfAbsent(level, k -> new java.util.HashMap<>());
        if (perDim.size() > 256) {
            long oldest = now - 4L;
            perDim.values().removeIf(t -> t < oldest);
        }
        perDim.put(containerPos.asLong(), now);
    }

    private static boolean insertedThisTick(Level level, long now, BlockPos containerPos) {
        java.util.Map<Long, Long> perDim = LAST_INSERTED.get(level);
        Long at = perDim == null ? null : perDim.get(containerPos.asLong());
        return at != null && at == now;
    }

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

        // Фаза 1 (0.3.96): собираем грани-источники, где ЕСТЬ что извлекать, и
        // отсеиваем пинг-понг (вставлено в этот же тик — не тянем обратно).
        List<Object[]> faces = new ArrayList<>(); // {BlockPos srcPos, Direction srcFace, Container src}
        for (Direction dir : Direction.values()) {
            if (!carrier.opensToward(state, T, dir)) continue;
            if (!carrier.modeFor(state, T).acceptsFromMachine()) continue;

            BlockPos srcPos = pos.relative(dir);
            // Сторона источника, обращённая к трубе (для WorldlyContainer-доступа).
            Direction srcFace = dir.getOpposite();
            if (insertedThisTick(level, now, srcPos)) continue;
            Container src = containerAt(level, srcPos);
            if (src == null) continue;
            if (!hasExtractableItem(src, srcFace)) continue;
            faces.add(new Object[]{srcPos, srcFace, src});
        }
        if (faces.isEmpty()) return 0;

        // Фаза 2 (0.3.96): ОДИН обход сети на узел за тик (раньше — до шести,
        // по грани на каждую). Множество приёмников от грани не зависит: BFS
        // стартует от узла, исключение источника делаем при вставке.
        // 0.3.299 (N13): результат кэшируется на узел (инвалидация — общий
        // штамп геометрии из LevelChunk-миксина), с бюджетом узлов на тик.
        List<Sink> sinks = sinksCached(level, pos);
        if (sinks.isEmpty()) return 0;

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
        // 0.3.306 (автор 26.10, подтверждено в игре: «передают ещё 1–5 тиков»):
        // даже свежий по штампу/TTL кэш не доказывает живость сущности, поэтому
        // перед вставкой ВСЕ приёмники проверяются повторным обходом: цель —
        // тот же контейнер на своём месте, каждая труба маршрута цела и
        // соединена. Что не прошло проверку — не используется; если от списка
        // что-то отброшено, запись кэша выбрасывается (следующий тик
        // пересоберёт по бюджету). Перенос при этом просто задерживается —
        // теряться предметам не за чем.
        // A captured source must still be the same live physical inventory.
        if (insertedThisTick(level, now, excludeSrc)) return 0;
        if (!sameContainerAt(level, excludeSrc, src)) return 0;
        sinks = liveSinks(level, pipePos, sinks);
        // Different halves/wrappers can still be the same source inventory.
        sinks = sinks.stream().filter(sink -> !sameInventory(level, src, sink.container(), sink.containerPos())).toList();
        if (sinks.isEmpty()) return 0;
        int moved = 0;
        long rotation = level.getGameTime();
        int n = sinks.size();
        // Сколько уже перемещено КАЖДОГО вида за этот тик (для лимита PER_ITEM_TICK_CAP).
        java.util.Map<net.minecraft.world.item.Item, Integer> perItem = new java.util.HashMap<>();

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
                    // 0.3.96: не льём обратно в источник этой грани (как раньше,
                    // только источник теперь один на весь BFS) и не вставляем в
                    // контейнер-источник этой же сети в этом же тике (щит циклов).
                    if (sink.containerPos().equals(excludeSrc)) continue;
                    if (insertedThisTick(level, now, sink.containerPos())) continue;
                    if (insertOne(sink.container, sink.face, one)) {
                        src.removeItem(slot, 1);
                        src.setChanged();
                        noteInserted(level, now, sink.containerPos());
                        noteCompanionInserted(level, now, sink.containerPos(), sink.container());
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
    private record Sink(Container container, Direction face, List<BlockPos> path, BlockPos containerPos) {
    }

    // ───────────────────── кэш топологии (0.3.299 N13) ─────────────────────
    // collectSinks бегал по каждой открытой трубе каждый тик: куча труб —
    // тысячи getBlockState на ровном месте. Кэш приёмников стартовой трубы
    // считается свежим только при совпадении штампа геометрии И непросроченном
    // TOPO_TTL_TICKS. Контейнер-сущность может приехать без block edit; один
    // неизменный штамп не должен навсегда удерживать даже пустой результат.
    // TOPO_BUDGET_PER_TICK — лимит новых BFS-узлов на игровой тик: перерасход
    // берёт прошлый результат трубы или отдаляет пересчёт на следующий тик.

    static final int TOPO_TTL_TICKS = 40;
    static final int TOPO_BUDGET_PER_TICK = 2048;

    private record CachedSinks(List<Sink> sinks, long builtTick, long version) {}

    private static final java.util.Map<Level, long[]> TOPO_VERSION =
        new java.util.WeakHashMap<>();
    private static final java.util.Map<Level, java.util.Map<Long, CachedSinks>> TOPO_CACHE =
        new java.util.WeakHashMap<>();
    private static long topoBudgetTick = Long.MIN_VALUE;
    private static int topoBudget;

    /** Миксин зовёт на КАЖДОМ setBlockState: версии расходятся — кэш протух. */
    public static void onWorldEdit(Level level) {
        if (level.isClientSide()) return;
        TOPO_VERSION.computeIfAbsent(level, k -> new long[1])[0]++;
    }

    private static long topoVersion(Level level) {
        long[] v = TOPO_VERSION.get(level);
        return v == null ? 0L : v[0];
    }

    private static List<Sink> sinksCached(Level level, BlockPos startPipe) {
        if (level.isClientSide()) return collectSinks(level, startPipe, new int[1], Integer.MAX_VALUE);
        long now = level.getGameTime();
        if (topoBudgetTick != now) {
            topoBudgetTick = now;
            topoBudget = TOPO_BUDGET_PER_TICK;
        }
        java.util.Map<Long, CachedSinks> perLevel = TOPO_CACHE.get(level);
        CachedSinks hit = perLevel == null ? null : perLevel.get(startPipe.asLong());
        long version = topoVersion(level);
        if (hit != null
            && (hit.version() == version && now - hit.builtTick() <= TOPO_TTL_TICKS)) {
            return hit.sinks();
        }
        if (topoBudget <= 0) return hit != null ? hit.sinks() : List.of();
        int[] visited = new int[1];
        List<Sink> sinks = collectSinks(level, startPipe, visited, topoBudget);
        if (visited[0] == 2) topoBudget = 0; else topoBudget -= visited[0]; // 0.3.306
        if (perLevel == null) {
            perLevel = new java.util.HashMap<>();
            TOPO_CACHE.put(level, perLevel);
        }
        if (perLevel.size() > 8192) perLevel.clear(); // трубы ломают — кэш не раздуваем
        // 0.3.306: усечённый бюджетом обход в кэш не кладём — иначе тик позже
        // по нему ездили бы как по полному (недоисследованные приёмники пропали б).
        if (visited[0] != 2) perLevel.put(startPipe.asLong(), new CachedSinks(sinks, now, version));
        return sinks;
    }

    /**
     * Собирает контейнеры-приёмники, достижимые по связной цепи предметных труб от
     * {@code startPipe}. Источник {@code srcPos} исключаем из приёмников. Правила
     * соединения — как в {@link PipeRouting}: труба↔труба по открытым навстречу
     * граням; труба→контейнер, если грань открыта к нему и режим = ОТДАЧА/АВТО.
     */
    private static List<Sink> collectSinks(Level level, BlockPos startPipe, int[] visitedOut, int maxNodes) {
        List<Sink> sinks = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Map<Long, BlockPos> parents = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPipe);
        visited.add(startPipe);
        parents.put(startPipe.asLong(), null);

        while (!queue.isEmpty()) {
            // 0.3.306: лимит обхода ВНУТРИ BFS (авторская правка бюджета):
            // visitedOut[0]=2 помечает усечение — такой результат не кэшируется,
            // поиск продолжится в следующих тиках. Первый большой обход больше
            // не способен увязнуть сервером: узлы считаются по-настоящему.
            if (visited.size() > maxNodes) { visitedOut[0] = 2; break; }
            BlockPos pipe = queue.poll();
            if (!level.hasChunkAt(pipe)) continue;
            BlockState pstate = level.getBlockState(pipe);
            if (!isItemPipe(pstate)) continue;
            PipeMode mode = modeOf(pstate);

            for (Direction dir : Direction.values()) {
                BlockPos npos = pipe.relative(dir);
                if (!level.hasChunkAt(npos)) continue;
                BlockState nstate = level.getBlockState(npos);

                if (isItemPipe(nstate)) {
                    if (!pipesConnect(pstate, nstate, dir)) continue;
                    if (visited.add(npos)) {
                        parents.put(npos.asLong(), pipe);
                        queue.add(npos);
                    }
                    continue;
                }
                // Контейнер за трубой — приёмник, если труба открыта к нему и отдаёт.
                if (!opensToward(pstate, dir)) continue;
                if (!mode.deliversToMachine()) continue;
                Container c = containerAt(level, npos);
                if (c != null) sinks.add(new Sink(c, dir.getOpposite(), pathTo(pipe, parents), npos));
            }
        }
        // 0.3.96 (автор: предупреждение о дорогом роутинге): огромная сеть узлов
        // подсвечивается частицей gonzo:hot_pipe даже без дорожек PipeRouting.
        if (visited.size() >= HOT_NODE_BFS) {
            PipeFlowWarnings.mark(level, startPipe,
                level.getGameTime() + PipeFlowWarnings.HOLD_TICKS);
        }
        visitedOut[0] = visited.size();
        return sinks;
    }

    /** Восстанавливает выбранный BFS-маршрут от стартовой трубы до {@code end}. */
    private static List<BlockPos> pathTo(BlockPos end, Map<Long, BlockPos> parents) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos at = end; at != null; at = parents.get(at.asLong())) {
            path.add(at);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * 0.3.306: фильтрует кэшированный список приёмников против ЖИВОГО мира и
     * возвращает актуальный (или пустой). При любом отсеве сбрасывает запись
     * кэша стартовой трубы — молча ездить по протухшему маршруту сеть не будет.
     */
    private static List<Sink> liveSinks(Level level, BlockPos startPipe, List<Sink> cached) {
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
        return dropped ? live : cached;
    }

    /** Маршрут цел (трубы на месте и соединены) и приёмник — тот же контейнер там же. */
    private static boolean sinkLive(Level level, Sink sink) {
        List<BlockPos> path = sink.path();
        for (int i = 0; i + 1 < path.size(); i++) {
            if (!level.hasChunkAt(path.get(i)) || !level.hasChunkAt(path.get(i + 1))) return false;
            BlockState a = level.getBlockState(path.get(i));
            BlockState b = level.getBlockState(path.get(i + 1));
            if (!isItemPipe(a) || !pipesConnect(a, b, dirBetween(path.get(i), path.get(i + 1)))) return false;
        }
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

    /** Preserve the existing same-tick anti-ping-pong shield through either half. */
    private static void noteCompanionInserted(Level level, long now, BlockPos pos, Container target) {
        if (!(target instanceof CompoundContainer compound) || !level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return;
        BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
        if (level.hasChunkAt(other) && level.getBlockEntity(other) instanceof Container owner && compound.contains(owner)) {
            noteInserted(level, now, other);
        }
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
