package com.gonzotech.machines.network;

import com.gonzotech.machines.block.entity.ItemFilterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

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
 * Мгновенная маршрутизация предметов через ФИЛЬТР (и связанный ОТСЕИВАТЕЛЬ).
 * <p>
 * Модель — та же, что у {@link ItemRouting}: ничего не буферизуется. Каждый тик
 * Фильтр:
 * <ol>
 *   <li>тянет предметы из ПРИЛЕГАЮЩИХ контейнеров (любая грань с сундуком/машиной
 *       — источник; грани с трубами/отсеивателем источниками не считаются);</li>
 *   <li>предмет, <b>совпавший</b> с образцами Фильтра, гонит по его ВЫХОДНОЙ сети
 *       предметных труб (BFS от Фильтра), равномерно раскидывая по приёмникам;</li>
 *   <li>предмет, <b>не совпавший</b>, отдаёт в сеть прилегающего ОТСЕИВАТЕЛЯ (BFS
 *       от него — отдельная цепь, НЕ назад в Фильтр), либо удаляет прямо в нём,
 *       если Отсеиватель запитан redstone;</li>
 *   <li>если девать предмет некуда — он НЕ извлекается (обратное давление), то
 *       есть «несовпавшее без отсеивателя остаётся в механизме».</li>
 * </ol>
 * Пустой набор образцов = Фильтр «выключен», совпадает всё (весь поток идёт в
 * выходную сеть Фильтра).
 */
public final class ItemFilterRouting {

    private ItemFilterRouting() {
    }

    private static final PipeType T = PipeType.ITEM;

    /** Ключ бэкдора: если образец несёт этот тег, у предмета он должен совпасть. */
    public static final String ALLOY_TAG = "gonzotech_alloy";

    public static void tick(Level level, BlockPos pos, BlockState state, ItemFilterBlockEntity be) {
        if (level.isClientSide()) return;

        // Приёмники «прошедшего» — выходная сеть Фильтра (item-трубы от Фильтра).
        // Приёмники «отсеянного» — сеть Отсеивателя, если он приткнут к грани.
        List<ItemRouting_Sink> pass = new ArrayList<>();
        List<ItemRouting_Sink> reject = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        List<BlockPos> sourcePositions = new ArrayList<>();
        List<ScavengerEndpoint> scavengers = new ArrayList<>();

        for (Direction dir : Direction.values()) {
            BlockPos npos = pos.relative(dir);
            BlockState nstate = level.getBlockState(npos);
            if (nstate.getBlock() instanceof ItemScavengerBlock scavenger) {
                scavengers.add(new ScavengerEndpoint(npos, scavenger.itemThroughputLimit(),
                    scavenger.perItemThroughputLimit(), scavenger.isPowered(level, npos)));
                continue;
            }
            if (isItemPipe(nstate)) continue; // трубы — часть выходной сети, не источник
            // Прилегающий контейнер (сундук/машина) — источник. Грань источника,
            // обращённая к Фильтру, = dir.getOpposite() (для sided-доступа).
            Container c = HopperBlockEntity.getContainerAt(level, npos);
            if (c != null) {
                sources.add(new Source(npos, dir.getOpposite()));
                sourcePositions.add(npos);
            }
        }

        // Выходная сеть Фильтра: BFS по item-трубам, начиная с самого Фильтра.
        collectSinksFromCarrier(level, pos, sourcePositions, pass);

        // Powered scavenger — это мусорка. Он намеренно имеет приоритет над
        // подключённой к нему сетью: reject-предметы удаляются сразу в блоке.
        // При нескольких смежных Отсеивателях первый в порядке Direction служит
        // визуальной точкой поглощения, а быстрый endpoint задаёт общий лимит.
        ScavengerEndpoint poweredEndpoint = null;
        for (ScavengerEndpoint scavenger : scavengers) {
            if (poweredEndpoint == null && scavenger.powered()) {
                poweredEndpoint = scavenger;
            }
        }
        BlockPos poweredScavenger = poweredEndpoint == null ? null : poweredEndpoint.pos();
        int rejectLimit = 0;
        int rejectPerItemCap = 0;
        if (poweredEndpoint != null) {
            // Powered endpoint owns the deletion branch, including its own tier limit.
            rejectLimit = poweredEndpoint.throughputLimit();
            rejectPerItemCap = poweredEndpoint.perItemThroughputLimit();
        } else {
            // Без redstone Отсеиватель сохраняет старую роль корня второй сети.
            for (ScavengerEndpoint scavenger : scavengers) {
                rejectLimit = Math.max(rejectLimit, scavenger.throughputLimit());
                rejectPerItemCap = Math.max(rejectPerItemCap, scavenger.perItemThroughputLimit());
                collectSinksFromCarrier(level, scavenger.pos(), sourcePositions, reject);
            }
        }

        boolean bufferHasItems = !be.isEmpty();
        if (sources.isEmpty() && !bufferHasItems) return;

        int passLimit = state.getBlock() instanceof ItemFilterBlock filter
            ? filter.itemThroughputLimit()
            : (int) T.maxThroughput();
        int passPerItemCap = state.getBlock() instanceof ItemFilterBlock filter
            ? filter.perItemThroughputLimit()
            : ItemRouting.PER_ITEM_TICK_CAP;

        // Совпавшее и отсеиваемое имеют собственные branch-limits. Поэтому
        // Scavenger II действительно разрешает 10/2 reject-поток, не делая
        // Filter I быстрее. Общий лимит забора остаётся максимальным лимитом
        // одной подключённой части, а не суммой двух веток: Filter II +
        // Scavenger II по-прежнему обрабатывают максимум 10 предметов за тик.
        // Если у ветки нет места назначения, её budget = 0 и предмет остаётся
        // в источнике как обратное давление.
        int activePassLimit = pass.isEmpty() ? 0 : passLimit;
        int activePassPerItemCap = pass.isEmpty() ? 0 : passPerItemCap;
        int activeRejectLimit = (poweredScavenger == null && reject.isEmpty()) ? 0 : rejectLimit;
        int activeRejectPerItemCap = activeRejectLimit == 0 ? 0 : rejectPerItemCap;
        ChannelBudget passBudget = new ChannelBudget(activePassLimit, activePassPerItemCap);
        ChannelBudget rejectBudget = new ChannelBudget(activeRejectLimit, activeRejectPerItemCap);
        ChannelBudget totalBudget = new ChannelBudget(Math.max(activePassLimit, activeRejectLimit),
            Math.max(activePassPerItemCap, activeRejectPerItemCap));
        if (totalBudget.exhausted()) return;

        long rotation = level.getGameTime();
        int moved = 0;

        // (a) Транзитный буфер Фильтра — предметы, пришедшие в него по трубам.
        // Тянем их без canTake-проверки (буфер её запрещает для внешних, но сам
        // Фильтр свой буфер раздавать обязан).
        moved = drainContainer(level, pos, be, null, be, pass, reject, poweredScavenger,
            passBudget, rejectBudget, totalBudget, moved, rotation, true);

        // (b) Прилегающие контейнеры-источники (режим «воронка»).
        for (Source source : sources) {
            if (totalBudget.exhausted()) break;
            Container src = HopperBlockEntity.getContainerAt(level, source.pos());
            if (src == null) continue;
            moved = drainContainer(level, pos, be, source.face(), src, pass, reject, poweredScavenger,
                passBudget, rejectBudget, totalBudget, moved, rotation, false);
        }
    }

    /**
     * Вытягивает предметы из одного контейнера {@code src} и направляет их в
     * pass/reject-ветку. Бюджеты веток независимы: это позволяет Отсеивателю II
     * иметь собственные 10/2 item limits. {@code ignoreCanTake=true} — для
     * собственного буфера Фильтра, который сам Фильтр имеет право раздавать.
     *
     * @return обновлённая последовательность перемещений за тик (для round-robin)
     */
    private static int drainContainer(Level level, BlockPos pos, ItemFilterBlockEntity be,
                                      Direction srcFace, Container src,
                                      List<ItemRouting_Sink> pass, List<ItemRouting_Sink> reject,
                                      BlockPos poweredScavenger,
                                      ChannelBudget passBudget, ChannelBudget rejectBudget,
                                      ChannelBudget totalBudget, int moved, long rotation, boolean ignoreCanTake) {
        for (int slot : ItemRouting.extractableSlots(src, srcFace)) {
            if (totalBudget.exhausted()) break;
            while (!totalBudget.exhausted()) {
                ItemStack cur = src.getItem(slot);
                if (cur.isEmpty()) break;
                if (!ignoreCanTake && !ItemRouting.canTake(src, slot, cur, srcFace)) break;

                boolean matched = matches(be, cur);
                ChannelBudget budget = matched ? passBudget : rejectBudget;
                if (!budget.canMove(cur.getItem()) || !totalBudget.canMove(cur.getItem())) break;

                ItemStack one = cur.copy();
                one.setCount(1);

                if (!matched && poweredScavenger != null) {
                    // Redstone-powered scavenger burns reject items at the direct
                    // filter/scavenger connection, even when a pipe is attached.
                    src.removeItem(slot, 1);
                    src.setChanged();
                    budget.record(one.getItem());
                    totalBudget.record(one.getItem());
                    moved++;
                    ItemFlowTracker.record(level, pos, one.getItem(), 1);
                    ItemFlowTracker.record(level, poweredScavenger, one.getItem(), 1);
                    continue;
                }

                List<ItemRouting_Sink> targets = matched ? pass : reject;
                boolean placed = false;
                int n = targets.size();
                for (int k = 0; k < n; k++) {
                    int idx = (int) Math.floorMod(rotation + moved + k, n);
                    ItemRouting_Sink sink = targets.get(idx);
                    if (ItemRouting.insertOne(sink.container(), sink.face(), one)) {
                        src.removeItem(slot, 1);
                        src.setChanged();
                        budget.record(one.getItem());
                        totalBudget.record(one.getItem());
                        moved++;
                        placed = true;
                        ItemFlowTracker.record(level, pos, one.getItem(), 1);
                        // Пишем каждый реально выбранный сегмент, чтобы
                        // Universal Node в транзитной ветке видел поток Items.
                        for (BlockPos pipe : sink.path()) {
                            ItemFlowTracker.record(level, pipe, one.getItem(), 1);
                        }
                        break;
                    }
                }
                if (!placed) break; // некуда — идём к следующему слоту (не стопорим)
            }
        }
        return moved;
    }

    /** Отдельный limiter pass/reject-ветки, включая cap одного точного Item. */
    private static final class ChannelBudget {
        private int remaining;
        private final int perItemCap;
        private final Map<net.minecraft.world.item.Item, Integer> perItem = new HashMap<>();

        ChannelBudget(int total, int perItemCap) {
            this.remaining = Math.max(0, total);
            this.perItemCap = Math.max(0, perItemCap);
        }

        boolean exhausted() {
            return remaining <= 0;
        }

        boolean canMove(net.minecraft.world.item.Item item) {
            return !exhausted() && perItem.getOrDefault(item, 0) < perItemCap;
        }

        void record(net.minecraft.world.item.Item item) {
            remaining--;
            perItem.merge(item, 1, Integer::sum);
        }
    }

    // ─────────────────────────── совпадение с образцами ───────────────────────────

    /**
     * Совпадает ли {@code stack} хотя бы с одним образцом Фильтра. Пустой набор
     * образцов = совпадает всё. Правило совпадения: <b>тот же предмет</b> (тип
     * item), игнорируя прочность и зачарования. Бэкдор: если у образца задан тег
     * сплава ({@link #ALLOY_TAG} в custom_data), у предмета он должен быть равен.
     */
    public static boolean matches(ItemFilterBlockEntity be, ItemStack stack) {
        if (be.isFilterEmpty()) return true;
        for (int i = 0; i < be.filterSize(); i++) {
            ItemStack tmpl = be.getFilter(i);
            if (tmpl.isEmpty()) continue;
            if (matchesTemplate(tmpl, stack)) return true;
        }
        return false;
    }

    private static boolean matchesTemplate(ItemStack template, ItemStack stack) {
        // Базовое правило: один и тот же предмет (тип), без учёта durability/enchant.
        if (!ItemStack.isSameItem(template, stack)) return false;

        // Бэкдор для будущих сплавов: если образец несёт тег сплава — сверяем его.
        String alloy = alloyTag(template);
        if (alloy != null) {
            return alloy.equals(alloyTag(stack));
        }
        return true;
    }

    /** Значение бэкдор-тега сплава из custom_data предмета, либо {@code null}. */
    private static String alloyTag(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        var tag = data.copyTag();
        return tag.contains(ALLOY_TAG) ? tag.getString(ALLOY_TAG) : null;
    }

    // ─────────────────────────── обход сети (BFS) ───────────────────────────

    /** Приёмник и точный маршрут item-труб до него. */
    private record ItemRouting_Sink(Container container, Direction face, List<BlockPos> path) {
    }

    /** Источник: позиция контейнера + его грань, обращённая к Фильтру. */
    private record Source(BlockPos pos, Direction face) {
    }

    /** Прилегающий Отсеиватель и его лимиты собственной reject-ветки. */
    private record ScavengerEndpoint(BlockPos pos, int throughputLimit,
                                     int perItemThroughputLimit, boolean powered) {
    }

    /**
     * BFS по связной цепи предметных труб, начиная с блока-носителя в {@code root}
     * (Фильтр или Отсеиватель — оба открыты во все стороны для типа ITEM).
     * Собирает контейнеры-приёмники за трубами. Позиции {@code exclude}
     * (источники) в приёмники не попадают.
     */
    private static void collectSinksFromCarrier(Level level, BlockPos root,
                                                List<BlockPos> exclude, List<ItemRouting_Sink> out) {
        Set<BlockPos> visited = new HashSet<>();
        Map<Long, BlockPos> parents = new HashMap<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        // Стартуем с прилегающих к корню труб.
        BlockState rootState = level.getBlockState(root);
        boolean rootIsCarrier = rootState.getBlock() instanceof PipeCarrier c && c.carries(rootState, T);
        visited.add(root);

        for (Direction dir : Direction.values()) {
            BlockPos npos = root.relative(dir);
            BlockState nstate = level.getBlockState(npos);
            if (isItemPipe(nstate)) {
                // Труба должна быть открыта навстречу корню; корень-носитель — во все стороны.
                if (!opensToward(nstate, dir.getOpposite())) continue;
                if (rootIsCarrier && !opensToward(rootState, dir)) continue;
                if (visited.add(npos)) {
                    // Корень — фильтр/отсеиватель, а не сегмент трубы на пути.
                    parents.put(npos.asLong(), null);
                    queue.add(npos);
                }
            } else if (rootIsCarrier) {
                // Корень (напр. если бы был носителем) может отдавать прямо в контейнер.
                if (exclude.contains(npos)) continue;
                if (!opensToward(rootState, dir)) continue;
                Container cc = HopperBlockEntity.getContainerAt(level, npos);
                if (cc != null) out.add(new ItemRouting_Sink(cc, dir.getOpposite(), List.of()));
            }
        }

        while (!queue.isEmpty()) {
            BlockPos pipe = queue.poll();
            BlockState pstate = level.getBlockState(pipe);
            if (!isItemPipe(pstate)) continue;
            PipeMode mode = modeOf(pstate);

            for (Direction dir : Direction.values()) {
                BlockPos npos = pipe.relative(dir);
                BlockState nstate = level.getBlockState(npos);

                if (isItemPipe(nstate)) {
                    if (!pipesConnect(pstate, nstate, dir)) continue;
                    if (visited.add(npos)) {
                        parents.put(npos.asLong(), pipe);
                        queue.add(npos);
                    }
                    continue;
                }
                if (exclude.contains(npos)) continue;
                if (visited.contains(npos)) continue; // корень/уже учтён
                if (!opensToward(pstate, dir)) continue;
                if (!mode.deliversToMachine()) continue;
                Container c = HopperBlockEntity.getContainerAt(level, npos);
                if (c != null) out.add(new ItemRouting_Sink(c, dir.getOpposite(), pathTo(pipe, parents)));
            }
        }
    }

    /** Восстанавливает выбранный BFS-маршрут от первой трубы после корня. */
    private static List<BlockPos> pathTo(BlockPos end, Map<Long, BlockPos> parents) {
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos at = end; at != null; at = parents.get(at.asLong())) {
            path.add(at);
        }
        Collections.reverse(path);
        return path;
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
}
