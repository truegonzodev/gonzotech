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
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
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
 *       от него — отдельная цепь, НЕ назад в Фильтр);</li>
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

        int budget = (int) T.maxThroughput();
        if (budget <= 0) return;

        // Приёмники «прошедшего» — выходная сеть Фильтра (item-трубы от Фильтра).
        // Приёмники «отсеянного» — сеть Отсеивателя, если он приткнут к грани.
        List<ItemRouting_Sink> pass = new ArrayList<>();
        List<ItemRouting_Sink> reject = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        List<BlockPos> sourcePositions = new ArrayList<>();

        Set<BlockPos> scavengers = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos npos = pos.relative(dir);
            BlockState nstate = level.getBlockState(npos);
            if (ItemScavengerBlock.isScavenger(nstate)) {
                scavengers.add(npos);
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

        // Сеть Отсеивателя(ей): BFS от каждого отсеивателя (его собственная цепь).
        for (BlockPos sc : scavengers) {
            collectSinksFromCarrier(level, sc, sourcePositions, reject);
        }

        boolean bufferHasItems = !be.isEmpty();
        if (sources.isEmpty() && !bufferHasItems) return;
        if (pass.isEmpty() && reject.isEmpty()) return;

        long rotation = level.getGameTime();
        int moved = 0;
        // Лимит на КАЖДЫЙ вид за тик (как у труб): 1 шт/т, суммарно budget=5 шт/т.
        java.util.Map<net.minecraft.world.item.Item, Integer> perItem = new java.util.HashMap<>();

        // (a) Транзитный буфер Фильтра — предметы, пришедшие ВРЕЗКОЙ по трубам.
        // Тянем их без canTake-проверки (буфер её запрещает для внешних, но сам
        // Фильтр свой буфер раздавать обязан).
        moved = drainContainer(level, pos, be, null, be, pass, reject, budget, moved, rotation, perItem, true);

        // (b) Прилегающие контейнеры-источники (режим «воронка»).
        for (Source source : sources) {
            if (moved >= budget) break;
            Container src = HopperBlockEntity.getContainerAt(level, source.pos());
            if (src == null) continue;
            moved = drainContainer(level, pos, be, source.face(), src,
                pass, reject, budget, moved, rotation, perItem, false);
        }
    }

    /**
     * Вытягивает предметы из одного контейнера {@code src} и раздаёт их по
     * сетям (совпавшее → {@code pass}, иначе → {@code reject}), уважая общий
     * бюджет и лимит на вид. {@code ignoreCanTake=true} — для собственного буфера
     * Фильтра (там внешняя выемка запрещена, но сам Фильтр обязан раздавать).
     *
     * @return обновлённое суммарное число перемещённых за тик
     */
    private static int drainContainer(Level level, BlockPos pos, ItemFilterBlockEntity be,
                                      Direction srcFace, Container src,
                                      List<ItemRouting_Sink> pass, List<ItemRouting_Sink> reject,
                                      int budget, int moved, long rotation,
                                      java.util.Map<net.minecraft.world.item.Item, Integer> perItem,
                                      boolean ignoreCanTake) {
        for (int slot : ItemRouting.extractableSlots(src, srcFace)) {
            if (moved >= budget) break;
            while (moved < budget) {
                ItemStack cur = src.getItem(slot);
                if (cur.isEmpty()) break;
                if (!ignoreCanTake && !ItemRouting.canTake(src, slot, cur, srcFace)) break;
                if (perItem.getOrDefault(cur.getItem(), 0) >= ItemRouting.PER_ITEM_TICK_CAP) break;

                boolean matched = matches(be, cur);
                List<ItemRouting_Sink> targets = matched ? pass : reject;

                ItemStack one = cur.copy();
                one.setCount(1);

                boolean placed = false;
                int n = targets.size();
                for (int k = 0; k < n; k++) {
                    int idx = (int) Math.floorMod(rotation + moved + k, n);
                    ItemRouting_Sink s = targets.get(idx);
                    if (ItemRouting.insertOne(s.container(), s.face(), one)) {
                        src.removeItem(slot, 1);
                        src.setChanged();
                        moved++;
                        perItem.merge(one.getItem(), 1, Integer::sum);
                        placed = true;
                        ItemFlowTracker.record(level, pos, one.getItem(), 1);
                        break;
                    }
                }
                if (!placed) break; // некуда — идём к следующему слоту (не стопорим)
            }
        }
        return moved;
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

    /** Приёмник: контейнер + сторона, которой к нему прилегает труба. */
    private record ItemRouting_Sink(Container container, Direction face) {
    }

    /** Источник: позиция контейнера + его грань, обращённая к Фильтру. */
    private record Source(BlockPos pos, Direction face) {
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
                if (visited.add(npos)) queue.add(npos);
            } else if (rootIsCarrier) {
                // Корень (напр. если бы был носителем) может отдавать прямо в контейнер.
                if (exclude.contains(npos)) continue;
                if (!opensToward(rootState, dir)) continue;
                Container cc = HopperBlockEntity.getContainerAt(level, npos);
                if (cc != null) out.add(new ItemRouting_Sink(cc, dir.getOpposite()));
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
                    if (visited.add(npos)) queue.add(npos);
                    continue;
                }
                if (exclude.contains(npos)) continue;
                if (visited.contains(npos)) continue; // корень/уже учтён
                if (!opensToward(pstate, dir)) continue;
                if (!mode.deliversToMachine()) continue;
                Container c = HopperBlockEntity.getContainerAt(level, npos);
                if (c != null) out.add(new ItemRouting_Sink(c, dir.getOpposite()));
            }
        }
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
