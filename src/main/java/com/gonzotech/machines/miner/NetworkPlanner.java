package com.gonzotech.machines.miner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Планировщик дорожек из рабочих блоков (0.3.257, автошахтер).
 *
 * <p>Сеть строится «разрастающейся паутиной»: от Плотника через уже
 * существующие рабочие блоки (переиспользование ветвей) к каждому якорю —
 * источнику колебаний или буру. Якорь считается подключённым, если хоть
 * один рабочий блок сети примыкает к нему гранью. Пути — кратчайший BFS по
 * клеткам, где допустима укладка: воздух строго над сплошной опорой либо
 * газон/снег/траворосль, которые можно заменить дорожкой. Solid-блоки
 * (камень, руда, корпуса машин) не заменяются никогда.</p>
 *
 * <p>План кэшируется один раз по нажатию кнопки: последующие изменения
 * мира на него не влияют — недоступная к моменту укладки клетка просто
 * пропускается.</p>
 */
public final class NetworkPlanner {

    /** Одна клетка очереди постройки; drill=true — вместо дорожки ставится бур. */
    public record Entry(BlockPos pos, boolean drill) {
    }

    /**
     * Готовый план.
     *
     * @param entries     клетки к застройке в порядке «от сети наружу»
     * @param targets     сколько якорей передавалось
     * @param already     сколько якорей было подключено ещё до плана
     * @param unreachable сколько якорей не достижимо (заставлено или вне лимитов)
     */
    public record Plan(List<Entry> entries, int targets, int already, int unreachable) {
    }

    /** Ограничение числа просмотренных клеток на один BFS-запуск. */
    private static final int BFS_NODE_BUDGET = 25_000;

    /**
     * 0.3.290 #11-Y: вертикальная полурамка планировщика = рабочая лента движка
     * (та же полурамка, что и поверхностный скан; WORK_BELT_Y Плотника ссылается
     * сюда — один закон на скан и на планирование).
     */
    public static final int VERTICAL_BAND = 48;

    private NetworkPlanner() {
    }

    /**
     * Клетку дорожки разрешено занимать только воздухом над твёрдой опорой
     * либо заменяемым растением (short_grass, папоротники, снежный слой —
     * ванильный тег {@code minecraft:replaceable}). Твердые блоки — трава
     * (grass_block), камень, бедрок — не заменяются никогда: дорога стоит
     * ПОВЕРХ поверхности, а не врезается в неё.
     */
    public static boolean isBuildable(BlockState state, BlockState below) {
        if (state.is(BlockTags.REPLACEABLE)) return !below.isAir();
        if (!state.isAir()) return false;
        // Воздух над растением относится к клетке растения: строим в его уровне.
        return !below.isAir() && !below.is(BlockTags.REPLACEABLE);
    }

    /**
     * Собрать план: BFS от сети Плотника к каждому якорю по очереди,
     * подключённые ветви сразу пополняют сеть (паутина, а не независимые
     * лучи). Якоря сортируются от ближних к дальним.
     *
     * @param workBlock все рабочие блоки в окрестности (для flood-fill сети)
     */
    public static Plan plan(ServerLevel level, BlockPos origin, Set<BlockPos> workBlock,
                            List<BlockPos> anchors) {
        // (автор 22.10, #12) Вотчина — строго 3×3 чанка вокруг Плотника: ни
        // пересечений, ни взглядов за край. BFS режется рамкой сектора; якорь,
        // оказавшийся вне рамки, — недостижим by design (разметка за краем
        // уже отрезана правами #6/#7, это вторая половина того же закона).
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        int minSectorX = (cx - 1) << 4;
        int maxSectorX = minSectorX + 47;
        int minSectorZ = (cz - 1) << 4;
        int maxSectorZ = minSectorZ + 47;
        Set<BlockPos> network = connectedWorkRegion(level, origin, workBlock);
        // Сам Плотник — корень сети: стройка стартует от него всегда, даже
        // когда вплотную к нему ещё не лежит ни одного рабочего блока.
        network.add(origin);
        int alreadyConnected = 0;
        List<BlockPos> pending = new ArrayList<>();
        for (BlockPos anchor : anchors) {
            if (touching(anchor, network) || anchor.distManhattan(origin) == 1) {
                alreadyConnected++;
            } else {
                pending.add(anchor);
            }
        }
        pending.sort(Comparator.comparingInt(a -> a.distManhattan(origin)));

        // 0.3.290 #11-Y (решение автора): окно больше не расползается за дальними
        // якорями — планировщик живёт в ленте движка. Якорь за пределами ленты
        // (±1 на смежность) недостижим by design: узловой бюджет не сгорает в
        // пустой вертикали, а 3×3 XZ-рамка (#12) остаётся как была.
        int minY = Math.max(origin.getY() - VERTICAL_BAND, level.getMinY());
        int maxY = Math.min(origin.getY() + VERTICAL_BAND, level.getMaxY() - 1);

        List<Entry> entries = new ArrayList<>();
        int unreachable = 0;
        for (BlockPos anchor : pending) {
            if (anchor.getY() < minY - 1 || anchor.getY() > maxY + 1) {
                unreachable++; // 0.3.290 #11-Y: лента выше/ниже — клетки нелегальны
                continue;
            }
            List<BlockPos> path = bfsPathToAnchor(level, network, entries, anchor, minY, maxY,
                minSectorX, maxSectorX, minSectorZ, maxSectorZ);
            if (path == null) {
                unreachable++;
                continue;
            }
            for (BlockPos cell : path) {
                entries.add(new Entry(cell, false));
                // Клетка ещё не поставлена, но уже ветвь будущей паутины.
                network.add(cell);
            }
        }
        return new Plan(entries, anchors.size(), alreadyConnected, unreachable);
    }

    /** Все рабочие блоки, связанные цепочкой граней с Плотником. */
    public static Set<BlockPos> connectedWorkRegion(ServerLevel level, BlockPos origin,
                                                    Set<BlockPos> workBlock) {
        Set<BlockPos> region = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (Direction dir : Direction.values()) {
            BlockPos near = origin.relative(dir);
            if (workBlock.contains(near) && region.add(near)) {
                queue.add(near);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos cell = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos near = cell.relative(dir);
                if (workBlock.contains(near) && region.add(near)) {
                    queue.add(near);
                }
            }
        }
        return region;
    }

    /** Примыкает ли якорь гранью к какому-то блоку из набора. */
    public static boolean touching(BlockPos anchor, Set<BlockPos> cells) {
        for (Direction dir : Direction.values()) {
            if (cells.contains(anchor.relative(dir))) return true;
        }
        return false;
    }

    /**
     * BFS «сеть (+ уже запланированные клетки) → клетка рядом с якорем».
     * Разворот по карте родителей даёт путь от сети к якорю.
     */
    private static List<BlockPos> bfsPathToAnchor(ServerLevel level, Set<BlockPos> network,
                                                  List<Entry> planned, BlockPos anchor,
                                                  int minY, int maxY,
                                                  int minSectorX, int maxSectorX,
                                                  int minSectorZ, int maxSectorZ) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos seed : network) {
            if (visited.add(seed)) queue.add(seed);
        }
        for (Entry entry : planned) {
            if (visited.add(entry.pos())) queue.add(entry.pos());
        }

        int budget = BFS_NODE_BUDGET;
        while (!queue.isEmpty() && budget-- > 0) {
            BlockPos cell = queue.poll();
            for (Direction dir : Direction.values()) {
                BlockPos next = cell.relative(dir);
                if (visited.contains(next)) continue;
                if (next.getY() < minY || next.getY() > maxY) continue;
                // Рамка вотчины (#12): XZ-выход за сектор 3×3 отрезает и обход
                // «в обнимашку» с соседним сектором.
                if (next.getX() < minSectorX || next.getX() > maxSectorX
                    || next.getZ() < minSectorZ || next.getZ() > maxSectorZ) continue;
                if (!isBuildable(level.getBlockState(next), level.getBlockState(next.below()))) continue;
                visited.add(next);
                parent.put(next, cell);
                if (touchingOne(next, anchor)) {
                    return reconstruct(parent, next);
                }
                queue.add(next);
            }
        }
        return null;
    }

    /** Достроили клетку пути вплотную к якорю (клетка — сосед якоря). */
    private static boolean touchingOne(BlockPos cell, BlockPos anchor) {
        return cell.distManhattan(anchor) == 1;
    }

    private static List<BlockPos> reconstruct(Map<BlockPos, BlockPos> parent, BlockPos end) {
        List<BlockPos> path = new ArrayList<>();
        BlockPos cur = end;
        while (cur != null && parent.containsKey(cur)) {
            path.add(cur);
            cur = parent.get(cur);
        }
        Collections.reverse(path);
        return path;
    }
}
