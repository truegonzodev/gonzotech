package com.gonzotech.radiation;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Проверка «закрытого контура» вокруг источника радиации (автор 21.09):
 * коробка из экранирующего материала (вольфрам, свинец, борный бетон…) должна
 * защищать чанк. Трассировать лучи от каждого блока НЕ будем (дорого и не
 * нужно): вместо этого раз в ~20 с (сканы редкие) из точки источника делается
 * ограниченный BFS-залив полости по «открытым» клеткам:
 * <ul>
 *   <li>заливка вырвалась на простор (&gt;RANGE клеток или за пределы мира) —
 *       контура нет, фактор = 1.0 (фонит как обычно);</li>
 *   <li>заливка уперлась в оболочку целиком — контур есть; фактор =
 *       СРЕДНЕЕ арифметическое факторов материалов стенок ({@link RadMaterials}).
 *       Сплошной вольфрам → 0.003; вольфрам с земляным полом → щель в полу
 *       честно поднимает утечку («земля ×1.0», защита слабеет).</li>
 * </ul>
 * Стоимость: до {@link #VISIT_CAP} getBlockState на один горячий источник,
 * не чаще раза в {@link #CACHE_TTL_TICKS} тиков (кэш по позиции).
 * <b>Руки игрока не экранируются никогда</b> (источник в инвентаре бьёт по
 * носителю напрямую) — экран работает для мира: блоки и сундуки.
 */
public final class Containment {

    /** Клеток заливки, после которой считаем полость «открытой». */
    private static final int VISIT_CAP = 2048;
    /** Манхэттенский радиус от источника: выход за него = «контура нет». */
    private static final int RANGE = 32;
    /** Перепроверка конкретной позиции не чаще раза в 20 с. */
    private static final long CACHE_TTL_TICKS = 400;
    /** Потолок кэша (антиутечка): при переполнении выкидываем просроченные. */
    private static final int CACHE_MAX = 4096;

    private record Entry(double factor, long tick) {
    }

    private static final Map<Long, Entry> CACHE = new HashMap<>();

    private Containment() {
    }

    /**
     * Фактор утечки источника в точке {@code source} (0.003–1.0):
     * 1.0 = контура нет; меньше — контур экранирует (на столько гасится вклад в чанк).
     */
    public static double factor(ServerLevel level, BlockPos source) {
        long key = source.asLong();
        long now = level.getServer().getTickCount();
        Entry cached = CACHE.get(key);
        if (cached != null && now - cached.tick < CACHE_TTL_TICKS) {
            return cached.factor;
        }
        double f = probe(level, source);
        putCached(key, new Entry(f, now));
        return f;
    }

    /** Принудительное забывание точки (место/слом блока рядом — контур пересчитают). */
    public static void invalidate(BlockPos pos) {
        CACHE.remove(pos.asLong());
    }

    private static void putCached(long key, Entry e) {
        if (CACHE.size() >= CACHE_MAX) { // переполнение — сносим произвольную половину (позиции мало живут и без кэша)
            Iterator<Map.Entry<Long, Entry>> it = CACHE.entrySet().iterator();
            while (it.hasNext() && CACHE.size() > CACHE_MAX / 2) {
                it.next();
                it.remove();
            }
        }
        CACHE.put(key, e);
    }

    /** Собственно BFS-залив полости и усреднение стенок. */
    private static double probe(ServerLevel level, BlockPos source) {
        int sx = source.getX(), sy = source.getY(), sz = source.getZ();
        int minY = level.getMinY(), maxY = level.getMaxY();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet(VISIT_CAP * 2);
        double contactSum = 0.0;
        int contacts = 0;

        queue.add(source);
        visited.add(source.asLong());
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            if (!cur.equals(source)) { // сам источник — не стена себе (солидный радио-блок тоже заливаем изнутри)
                double wall = RadMaterials.blockFactor(level.getBlockState(cur));
                var state = level.getBlockState(cur);
                if (wall < 1.0) {            // экранирующий материал — стенка даже если «стекло»
                    contactSum += wall;
                    contacts++;
                    continue;
                }
                if (!(state.isAir() || !state.canOcclude())) {
                    contactSum += 1.0;   // обычный солидный блок: герметизирует, но не гасит
                    contacts++;
                    continue;
                }
            }
            if (visited.size() >= VISIT_CAP) {
                return 1.0; // заливка плоская/огромная → контура нет
            }
            int cx = cur.getX(), cy = cur.getY(), cz = cur.getZ();
            for (int d = 0; d < 6; d++) {
                int nx = cx + INT_OFFSETS[d][0], ny = cy + INT_OFFSETS[d][1], nz = cz + INT_OFFSETS[d][2];
                if (Math.abs(nx - sx) > RANGE || Math.abs(nz - sz) > RANGE
                        || ny < minY || ny >= maxY || Math.abs(ny - sy) > RANGE) {
                    return 1.0; // вырвались на простор
                }
                mp.set(nx, ny, nz);
                if (visited.add(mp.asLong())) {
                    queue.add(new BlockPos(nx, ny, nz));
                }
            }
        }
        if (contacts == 0) {
            return 1.0; // полость из одного блока без стенок — не случается, подстраховка
        }
        double avg = contactSum / contacts;
        return Math.max(0.001, Math.min(1.0, avg));
    }

    private static final int[][] INT_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
}
