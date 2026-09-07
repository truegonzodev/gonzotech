package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
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
     * Один проход извлечения для трубы/узла в {@code pos}: по всем граням, где
     * труба открыта, режим позволяет забор из машины/контейнера и рядом есть
     * контейнер-источник — вынимаем до общего лимита {@link PipeType#maxThroughput()}
     * предметов за тик и раскидываем их по сети.
     *
     * @return сколько предметов суммарно перемещено за этот тик
     */
    public static int tickExtract(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return 0;
        if (!(state.getBlock() instanceof PipeCarrier carrier)) return 0;
        if (!carrier.carries(state, T)) return 0;

        int budget = (int) T.maxThroughput();
        if (budget <= 0) return 0;

        int moved = 0;
        for (Direction dir : Direction.values()) {
            if (budget <= 0) break;
            if (!carrier.opensToward(state, T, dir)) continue;
            if (!carrier.modeFor(state, T).acceptsFromMachine()) continue;

            BlockPos srcPos = pos.relative(dir);
            // Сторона источника, обращённая к трубе (для WorldlyContainer-доступа).
            Direction srcFace = dir.getOpposite();
            Container src = containerAt(level, srcPos);
            if (src == null) continue;

            // Приёмники собираем один раз на грань (сеть та же на весь тик).
            List<Sink> sinks = collectSinks(level, pos, srcPos);
            if (sinks.isEmpty()) continue;

            int done = pushItems(level, pos, src, srcFace, sinks, budget);
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
                                 List<Sink> sinks, int budget) {
        int moved = 0;
        long rotation = level.getGameTime();
        int n = sinks.size();

        for (int slot : extractableSlots(src, srcFace)) {
            if (moved >= budget) break;

            // Двигаем из этого слота столько, сколько сможем (в пределах бюджета).
            while (moved < budget) {
                ItemStack cur = src.getItem(slot);
                if (cur.isEmpty()) break;
                if (!canTake(src, slot, cur, srcFace)) break;

                ItemStack one = cur.copy();
                one.setCount(1);

                boolean placed = false;
                for (int k = 0; k < n; k++) {
                    int idx = (int) Math.floorMod(rotation + moved + k, n);
                    Sink sink = sinks.get(idx);
                    if (insertOne(sink.container, sink.face, one)) {
                        src.removeItem(slot, 1);
                        src.setChanged();
                        moved++;
                        placed = true;
                        // Учёт потока для HUD ключа (предмет → шт/тик).
                        ItemFlowTracker.record(level, pipePos, one.getItem(), 1);
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
    private static List<Integer> extractableSlots(Container src, Direction face) {
        List<Integer> out = new ArrayList<>();
        if (src instanceof WorldlyContainer wc) {
            for (int slot : wc.getSlotsForFace(face)) out.add(slot);
            return out;
        }
        for (int slot = 0; slot < src.getContainerSize(); slot++) out.add(slot);
        return out;
    }

    /** Можно ли вынуть предмет из слота источника (с учётом грани для sided-инвентаря). */
    private static boolean canTake(Container src, int slot, ItemStack stack, Direction face) {
        if (src instanceof WorldlyContainer wc) {
            return wc.canTakeItemThroughFace(slot, stack, face);
        }
        return true;
    }

    /** Кладёт ровно 1 предмет в приёмник (с учётом грани), стакая где можно. Возвращает успех. */
    private static boolean insertOne(Container dst, Direction face, ItemStack one) {
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
        ItemStack cur = dst.getItem(slot);
        if (cur.isEmpty()) {
            ItemStack put = one.copy();
            put.setCount(1);
            dst.setItem(slot, put);
            dst.setChanged();
            return true;
        }
        if (ItemStack.isSameItemSameComponents(cur, one)
            && cur.getCount() < Math.min(cur.getMaxStackSize(), dst.getMaxStackSize())) {
            cur.grow(1);
            dst.setChanged();
            return true;
        }
        return false;
    }

    // ─────────────────────────── обход сети (BFS) ───────────────────────────

    /** Приёмник: контейнер и сторона, которой к нему прилегает труба. */
    private record Sink(Container container, Direction face) {
    }

    /**
     * Собирает контейнеры-приёмники, достижимые по связной цепи предметных труб от
     * {@code startPipe}. Источник {@code srcPos} исключаем из приёмников. Правила
     * соединения — как в {@link PipeRouting}: труба↔труба по открытым навстречу
     * граням; труба→контейнер, если грань открыта к нему и режим = ОТДАЧА/АВТО.
     */
    private static List<Sink> collectSinks(Level level, BlockPos startPipe, BlockPos srcPos) {
        List<Sink> sinks = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPipe);
        visited.add(startPipe);

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
                // Контейнер за трубой — приёмник, если труба открыта к нему и отдаёт.
                if (npos.equals(srcPos)) continue;               // не льём обратно в источник
                if (!opensToward(pstate, dir)) continue;
                if (!mode.deliversToMachine()) continue;
                Container c = containerAt(level, npos);
                if (c != null) sinks.add(new Sink(c, dir.getOpposite()));
            }
        }
        return sinks;
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
     * Контейнер в позиции {@code pos} (сундук/бочка/машина/двойной сундук/вагонетка).
     * Делегируем ванильной логике воронки — она корректно собирает двойные сундуки
     * и контейнер-сущности. Возвращает {@code null}, если контейнера нет.
     */
    private static Container containerAt(Level level, BlockPos pos) {
        return HopperBlockEntity.getContainerAt(level, pos);
    }
}
