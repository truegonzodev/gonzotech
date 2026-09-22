package com.gonzotech.radiation;

import com.gonzotech.GonzoTechMod;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Проверка «закрытого контура» вокруг источника радиации.
 *
 * <p>История: 21.09 контур считался как СРЕДНЕЕ по клеткам оболочки; 22.09 автор уточнил три
 * правила, и они здесь реализованы (Containment 2.0):</p>
 * <ol>
 *   <li><b>Перемешка материалов — по площади.</b> Стена «в перемешку» (вольфрам / свинец /
 *       бариевый бетон) ведёт себя как средневзвешенная доля прохождения: каждая клетка оболочки
 *       входит в среднее со своим фактором из {@link RadMaterials}. Тонкая деталь: у свинца,
 *       бария, бетона, стекла и т.п. свой фактор; обычный камень/земля — 1.0 (герметизируют,
 *       но не экранируют).</li>
 *   <li><b>Слои перемножаются (толщина).</b> Автор: «стена толщиной два блока эффективнее».
 *       Для каждой клетки оболочки по нормали НАРУЖУ (направление, по которому заливка
 *       пришла из полости) считаем произведение факторов следующих за ней клеток — до
 *       {@link #MAX_LAYERS}. Вольфрам 1 блок — 0.003; два — 9·10⁻⁶; бункер в бункере —
 *       произведение слоёв тоже (внутренний слой уже сам содержит внешние).</li>
 *   <li><b>Заслонки (двери, гермозатворы) в расчёте НЕ участвуют.</b> Автор: «дверь просто
 *       выполняет условие закрытого контура, а не среднее между 0.02 и 1.0». То есть закрытая
 *       дверь замыкает полость (иначе — дырка, контура нет), но в среднее оболочки не входит
 *       вообще: она не тянет фактор вверх и не отнимает площадь у экранирующих стен.
 *       Заслонка = блок из тега {@link #CONTOUR_SEAL}; если у неё есть булево состояние
 *       {@code open}, считать надо только закрытую (открытая = дырка). Антиэксплойт: если
 *       заслонок в оболочке больше {@link #SEAL_SHARE_PERCENT} %, они считаются обычными
 *       стенами (иначе «стена из дверей» + один блок вольфрама давала бы бесплатный контур).</li>
 * </ol>
 *
 * <p>Плюс порог {@link #ZERO_THRESHOLD}: фактор ниже 10⁻³ — это ровно «ноль» (заражение чанка
 * не растёт, дозиметр ничего не видит) — автор 22.09: «цифра округляется до нуля». При этом
 * признак «полость замкнута» ({@link Result#enclosed()}) хранится отдельно: он нужен чистой
 * комнате и герметичным отсекам даже без радиации.</p>
 *
 * <p>Стоимость: до {@link #VISIT_CAP} getBlockState на заливку плюс ≤ {@link #MAX_LAYERS} на
 * каждую клетку оболочки, не чаще раза в {@link #CACHE_TTL_TICKS} тиков (кэш по позиции).
 * <b>Руки игрока не экранируются никогда</b> (источник в инвентаре бьёт по носителю напрямую) —
 * экран работает для мира: блоки и контейнеры.</p>
 */
public final class Containment {

    /**
     * Блоки-заслонки: закрывают контур, но сами не экранируют (автор 22.09 — тяжёлая дверь).
     * Пока тег пуст (тяжёлой двери ещё нет); когда появится — она кладётся в
     * {@code data/gonzotech/tags/block/contour_seal.json} и обязана иметь булево состояние
     * {@code open} (closed = заслонка).
     */
    public static final TagKey<Block> CONTOUR_SEAL = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "contour_seal"));

    /** Клеток заливки, после которой считаем полость «открытой». */
    private static final int VISIT_CAP = 2048;
    /** Манхэттенский радиус от источника: выход за него = «контура нет». */
    private static final int RANGE = 32;
    /** Перепроверка конкретной позиции не чаще раза в 20 с. */
    private static final long CACHE_TTL_TICKS = 400;
    /** Потолок кэша (антиутечка): при переполнении выкидываем произвольную половину. */
    private static final int CACHE_MAX = 4096;
    /** Толщина стенки: сколько клеток по нормали наружу перемножаем. */
    private static final int MAX_LAYERS = 8;
    /** Фактор ниже этого — «ноль» (автор 22.09). */
    private static final double ZERO_THRESHOLD = 1.0E-3;
    /** Доля заслонок в оболочке (%), после которой они считаются обычными стенами. */
    private static final int SEAL_SHARE_PERCENT = 50;

    /** Итог разбора полости: замкнута ли она и с каким фактором утечки (0.0 — «ноль»). */
    public record Result(boolean enclosed, double factor) {
    }

    private record Entry(Result result, long tick) {
    }

    private static final Map<Long, Entry> CACHE = new HashMap<>();

    private Containment() {
    }

    /**
     * Полный итог для точки источника: замкнута ли полость и фактор утечки.
     * {@code enclosed=false} → фактор 1.0 (контура нет, фонит как обычно).
     */
    public static Result result(ServerLevel level, BlockPos source) {
        long key = source.asLong();
        long now = level.getServer().getTickCount();
        Entry cached = CACHE.get(key);
        if (cached != null && now - cached.tick < CACHE_TTL_TICKS) {
            return cached.result;
        }
        Result fresh = probe(level, source);
        putCached(key, new Entry(fresh, now));
        return fresh;
    }

    /**
     * Фактор утечки источника в точке {@code source} (0.0–1.0):
     * 1.0 = контура нет; меньше — контур экранирует (на столько гасится вклад в чанк);
     * 0.0 = «округлено до нуля» (фактор ниже {@link #ZERO_THRESHOLD}).
     */
    public static double factor(ServerLevel level, BlockPos source) {
        return result(level, source).factor();
    }

    /** Замкнут ли контур вокруг точки (задел под чистую комнату и герметичные отсеки). */
    public static boolean isEnclosed(ServerLevel level, BlockPos source) {
        return result(level, source).enclosed();
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

    /** Собственно BFS-залив полости, классификация оболочки и средневзвешенный фактор. */
    private static Result probe(ServerLevel level, BlockPos source) {
        int sx = source.getX(), sy = source.getY(), sz = source.getZ();
        int minY = level.getMinY(), maxY = level.getMaxY();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet(VISIT_CAP * 2);
        Long2ByteOpenHashMap cameFrom = new Long2ByteOpenHashMap(VISIT_CAP * 2);
        double sum = 0.0;      // сумма факторов клеток оболочки (для среднего)
        int counted = 0;       // клеток оболочки в среднем
        int seals = 0;         // заслонок (двери): замыкают, но в среднее не входят

        queue.add(source);
        visited.add(source.asLong());
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            if (!cur.equals(source)) { // сам источник — не стена себе (солидный радио-блок тоже заливаем изнутри)
                BlockState state = level.getBlockState(cur);
                if (isSeal(state)) {
                    double shield = RadMaterials.blockFactor(state);
                    if (shield >= 1.0) {
                        seals++;   // чистый гермозатвор: контур замыкает и в среднем не участвует (автор 22.09)
                        continue;
                    }
                    // Заслонка ИЗ ЭКРАНИРУЮЩЕГО МАТЕРИАЛА (ванильная железная дверь 0.5, свинцовая 0.02):
                    // контур замыкает, но входит в среднее своим фактором — автор 22.09: «гадит контур».
                    int[] sealDir = INT_OFFSETS[cameFrom.get(cur.asLong()) & 0xFF];
                    sum += depth(level, cur, sealDir[0], sealDir[1], sealDir[2]);
                    counted++;
                    continue;
                }
                if (isWall(state)) {
                    int[] d = INT_OFFSETS[cameFrom.get(cur.asLong()) & 0xFF];
                    sum += depth(level, cur, d[0], d[1], d[2]); // толщина: слои наружу перемножаются
                    counted++;
                    continue;
                }
            }
            if (visited.size() >= VISIT_CAP) {
                return new Result(false, 1.0); // заливка плоская/огромная → контура нет
            }
            int cx = cur.getX(), cy = cur.getY(), cz = cur.getZ();
            for (int d = 0; d < 6; d++) {
                int nx = cx + INT_OFFSETS[d][0], ny = cy + INT_OFFSETS[d][1], nz = cz + INT_OFFSETS[d][2];
                if (Math.abs(nx - sx) > RANGE || Math.abs(nz - sz) > RANGE
                        || ny < minY || ny >= maxY || Math.abs(ny - sy) > RANGE) {
                    return new Result(false, 1.0); // вырвались на простор
                }
                mp.set(nx, ny, nz);
                if (visited.add(mp.asLong())) {
                    queue.add(new BlockPos(nx, ny, nz));
                    cameFrom.put(mp.asLong(), (byte) d); // направление «из полости в стену» = нормаль наружу
                }
            }
        }
        if (counted == 0 && seals == 0) {
            return new Result(false, 1.0); // полость из одного блока без стенок — не случается, подстраховка
        }
        if (seals * 100 > (counted + seals) * SEAL_SHARE_PERCENT) {
            // «Стена из дверей»: заслонок больше половины оболочки — считаем их обычными стенами.
            sum += seals; // ×1.0 каждая: закрывают, но не экранируют
            counted += seals;
            seals = 0;
        }
        if (counted == 0) {
            return new Result(true, 1.0); // оболочка из одних заслонок — контур замкнут, но защиты нет
        }
        double avg = sum / counted;
        if (avg < ZERO_THRESHOLD) {
            return new Result(true, 0.0); // автор 22.09: «цифра округлена до нуля»
        }
        return new Result(true, Math.min(1.0, avg));
    }

    /**
     * Клетка закрывает полость? Экранирующий материал ({@link RadMaterials} &lt; 1.0) —
     * всегда стенка, даже «стекло» (вольфрамовое/свинцовое стекло в таблице есть).
     */
    private static boolean isWall(BlockState state) {
        if (RadMaterials.blockFactor(state) < 1.0) {
            return true;
        }
        return !state.isAir() && state.canOcclude(); // обычный солидный блок: герметизирует, но не гасит
    }

    /**
     * Заслонка ли это (дверь/гермозатвор из {@link #CONTOUR_SEAL}) и закрыта ли она.
     * Закрытость ищем по булеву состоянию {@code open} — так работает и ванильная дверь,
     * и будущая тяжёлая (у неё состояние названо так же), и не нужно правок кода при
     * добавлении новых заслонок: достаточно тега.
     */
    private static boolean isSeal(BlockState state) {
        if (!state.is(CONTOUR_SEAL)) {
            return false;
        }
        for (Property<?> property : state.getProperties()) {
            if (property instanceof BooleanProperty open && "open".equals(property.getName())) {
                return !state.getValue(open);
            }
        }
        return true; // у блока нет состояния «открыто» — считаем всегда закрытой
    }

    /**
     * Произведение факторов клеток по нормали наружу, начиная с самой клетки оболочки.
     * Обычные солидные клетки (камень, земля) дают ×1.0, то есть просто не мешают слоям
     * за ними: «вольфрам под каменной облицовкой» защиту всё равно даёт.
     */
    private static double depth(ServerLevel level, BlockPos cell, int dx, int dy, int dz) {
        double product = 1.0;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos(cell.getX(), cell.getY(), cell.getZ());
        for (int layer = 0; layer < MAX_LAYERS; layer++) {
            BlockState state = level.getBlockState(mp);
            if (layer > 0 && state.isAir()) {
                break; // за стенкой пустота — слоёв больше нет
            }
            double wall = RadMaterials.blockFactor(state);
            if (layer > 0 && wall >= 1.0 && !state.canOcclude()) {
                break; // неполный/прозрачный блок без экрана — стенка кончилась
            }
            product *= wall;
            if (product < ZERO_THRESHOLD) {
                return 0.0; // дальше считать нечего: уже «ноль»
            }
            mp.move(dx, dy, dz);
        }
        return product;
    }

    private static final int[][] INT_OFFSETS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
}
