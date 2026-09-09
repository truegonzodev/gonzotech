package com.gonzotech.space.worldgen;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Фаза 4 (группа 2) — СУПЕРРЕДКИЕ ПАРЯЩИЕ ГЛЫБЫ метеорной породы в пустых
 * космических измерениях (орбиты Солнца/Альфы Центавра, открытый космос).
 *
 * <p>Каждая глыба — кусок ПРИЧУДЛИВОЙ СГЛАЖЕННОЙ формы (metaball-объединение
 * нескольких долей + шумовая шероховатость краёв, как у ледяных глыб Европы),
 * диаметром от 5 до 50 блоков. Тело — {@link ModBlocks#METEOR}; с ОДНОЙ
 * случайной стороны глыба покрыта коркой {@link ModBlocks#SUPERDENSE_ICE}
 * (полусфера-«шапка» по выбранной оси).
 *
 * <p>Частота задаётся placed_feature (RarityFilter): орбиты ~1 на 8×8 чанков,
 * открытый космос ~1 на 6×6. Feature при срабатывании ставит ровно ОДНУ глыбу
 * (центр — origin чанка), поэтому все записи попадают в безопасную 3×3-зону
 * вокруг чанка (крупные r≤25 + запас 2 = 27 < 48/2).
 *
 * <p>Пишем только в ВОЗДУХ (миры пустые), высоту центра берём в середине
 * колонны мира.
 */
public class MeteorFeature extends Feature<NoneFeatureConfiguration> {

    public MeteorFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Границы безопасной зоны записи (3×3 чанка вокруг генерируемого чанка). */
    private int safeMinX, safeMaxX, safeMinZ, safeMaxZ;

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        int chunkMinX = (origin.getX() >> 4) << 4;
        int chunkMinZ = (origin.getZ() >> 4) << 4;
        safeMinX = chunkMinX - 16;
        safeMaxX = chunkMinX + 31;
        safeMinZ = chunkMinZ - 16;
        safeMaxZ = chunkMinZ + 31;

        // Радиус глыбы: распределение как у глыб Европы (в основном
        // мелкие/средние, изредка огромные).
        int desiredR;
        float roll = random.nextFloat();
        if (roll < 0.55F) {
            desiredR = 3 + random.nextInt(4);   // мелкие r3..6
        } else if (roll < 0.88F) {
            desiredR = 7 + random.nextInt(7);    // средние r7..13
        } else {
            desiredR = 14 + random.nextInt(9);   // огромные r14..22
        }

        // ЭЛЛИПСОИДНАЯ ДЕВИАЦИЯ: тянем глыбу вдоль СЛУЧАЙНОГО 3D-направления
        // (не по оси) с коэффициентом k=1.0..1.9 → получаются и «шары», и
        // вытянутые тела. Направление — равномерно по сфере.
        double sx = random.nextGaussian(), sy = random.nextGaussian(), sz = random.nextGaussian();
        double slen = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (slen < 1e-6) { sx = 1; sy = 0; sz = 0; slen = 1; }
        sx /= slen; sy /= slen; sz /= slen;
        double stretch = 1.0 + random.nextDouble() * 0.9; // 1.0..1.9

        // КЛАМП ПОД БЕЗОПАСНУЮ ЗОНУ: вся глыба (с учётом лопастей ~1.45·R,
        // растяжения и шумовой кромки) должна поместиться в 3×3-чанковую зону
        // записи (иначе дальние блоки молча отбрасываются → плоские срезы/грани).
        // Центрируем в центре чанка; доступный полурадиус ≈ 21 блок.
        final double AVAIL_HALF = 21.0;
        final double BOUND = 1.45; // множитель габарита от maxR (лопасти + warp)
        int maxAllowed = (int) Math.floor(AVAIL_HALF / (BOUND * stretch));
        int maxR = Math.max(2, Math.min(desiredR, maxAllowed));

        // Центр — РОВНО в центре чанка (симметричный запас до краёв зоны).
        int cx = chunkMinX + 8;
        int cz = chunkMinZ + 8;
        // Высота центра — в средней части мира с запасом от краёв.
        int margin = (int) Math.ceil(maxR * BOUND * stretch) + 2;
        int floor = level.getMinY() + 4 + margin;
        int ceil = level.getMaxY() - 4 - margin;
        if (ceil <= floor + 4) {
            return false;
        }
        int cy = floor + random.nextInt(ceil - floor);

        chaoticBlob(level, random, cx, cy, cz, maxR, sx, sy, sz, stretch);
        return true;
    }

    /** true, если запись в (x,z) не выходит за безопасную 3×3-зону чанков. */
    private boolean inSafe(int x, int z) {
        return x >= safeMinX && x <= safeMaxX && z >= safeMinZ && z <= safeMaxZ;
    }

    /**
     * Глыба причудливой ОРГАНИЧНОЙ формы (metaball «meatballs» + плавный
     * 3D value-noise, искажающий поверхность). НЕ эллипсоид и НЕ «рифлёная»
     * ступенчатая — гладко-бугристая, как настоящий астероид. С одной
     * случайной стороны — корка сверхплотного льда (внешняя оболочка по оси).
     *
     * <p>Форма задаётся полем метаболов {@code field(p)=Σ r_i²/dist²} (поверхность
     * при {@code field≈1}, доли сливаются гладко) + низкочастотный трилинейный
     * шум, который плавно «дышит» порогом → лопасти и впадины без ступенек.
     */
    private void chaoticBlob(WorldGenLevel level, RandomSource random,
                             int cx, int cy, int cz, int maxR,
                             double ax, double ay, double az, double stretch) {
        int lobes = 2 + random.nextInt(4); // 2..5 слитных долей
        double[] lx = new double[lobes], ly = new double[lobes], lz = new double[lobes], lr = new double[lobes];
        for (int i = 0; i < lobes; i++) {
            double spread = 0.42; // доли близко к центру → единое тело, а не гроздь
            lx[i] = (random.nextDouble() * 2 - 1) * maxR * spread;
            ly[i] = (random.nextDouble() * 2 - 1) * maxR * spread; // без вертикального сплющивания
            lz[i] = (random.nextDouble() * 2 - 1) * maxR * spread;
            lr[i] = maxR * (0.55 + random.nextDouble() * 0.30); // радиус доли 0.55..0.85·R
        }
        long noiseSeed = random.nextLong();
        double noiseScale = 0.14 + random.nextDouble() * 0.06; // низкая частота = крупные бугры
        double warpAmp = 0.30 + random.nextDouble() * 0.20;    // сила искажения поверхности

        // Ледяная корка с ОДНОЙ случайной стороны: ось (0=x,1=y,2=z) и знак.
        int iceAxis = random.nextInt(3);
        int iceSign = random.nextBoolean() ? 1 : -1;

        BlockState meteor = ModBlocks.METEOR.get().defaultBlockState();
        BlockState ice = ModBlocks.SUPERDENSE_ICE.get().defaultBlockState();

        // Габарит с учётом растяжения по оси (ax,ay,az): вдоль оси тело в
        // stretch раз длиннее, поэтому расширяем область сканирования.
        int R = (int) Math.ceil((maxR + 3) * stretch);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    // Поле метаболов в АНИЗОТРОПНОМ пространстве: координату вдоль
                    // оси (ax,ay,az) сжимаем в 1/stretch раз → сфера превращается
                    // в эллипсоид, вытянутый в СЛУЧАЙНОМ направлении.
                    double field = 0.0;
                    for (int i = 0; i < lobes; i++) {
                        double px = dx - lx[i], py = dy - ly[i], pz = dz - lz[i];
                        // проекция на ось растяжения
                        double along = px * ax + py * ay + pz * az;
                        // компонента вдоль оси сжимается, поперечные — как есть
                        double cxx = px - along * ax + (along / stretch) * ax;
                        double cyy = py - along * ay + (along / stretch) * ay;
                        double czz = pz - along * az + (along / stretch) * az;
                        double d2 = cxx * cxx + cyy * cyy + czz * czz + 1.0;
                        field += (lr[i] * lr[i]) / d2;
                    }
                    // Плавный шум искажает порог поверхности → органичные лопасти.
                    double warp = valueNoise(noiseSeed,
                        dx * noiseScale, dy * noiseScale, dz * noiseScale); // -1..1
                    double threshold = 1.0 - warp * warpAmp;
                    if (field < threshold) {
                        continue;
                    }

                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (!inSafe(x, z)) {
                        continue;
                    }
                    if (y <= level.getMinY() + 2 || y >= level.getMaxY() - 1) {
                        continue;
                    }
                    // Корка: тонкая ВНЕШНЯЯ оболочка (field близко к порогу) с
                    // выбранной стороны глыбы.
                    boolean crust = false;
                    if (field < threshold * 1.8) { // только приповерхностный слой
                        double comp = (iceAxis == 0) ? dx : (iceAxis == 1) ? dy : dz;
                        if (Math.signum(comp) == iceSign && Math.abs(comp) > maxR * 0.12) {
                            crust = true;
                        }
                    }
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, crust ? ice : meteor, 2);
                    }
                }
            }
        }
    }

    /**
     * Гладкий 3D value-noise в [-1,1]: хеш в целочисленных узлах решётки +
     * трилинейная интерполяция со smoothstep-сглаживанием. Даёт плавные крупные
     * бугры/впадины (в отличие от попиксельного хеша, который давал «рябь»).
     */
    private double valueNoise(long seed, double x, double y, double z) {
        int xi = fastFloor(x), yi = fastFloor(y), zi = fastFloor(z);
        double xf = x - xi, yf = y - yi, zf = z - zi;
        double u = fade(xf), v = fade(yf), w = fade(zf);
        double c000 = hash01(seed, xi, yi, zi);
        double c100 = hash01(seed, xi + 1, yi, zi);
        double c010 = hash01(seed, xi, yi + 1, zi);
        double c110 = hash01(seed, xi + 1, yi + 1, zi);
        double c001 = hash01(seed, xi, yi, zi + 1);
        double c101 = hash01(seed, xi + 1, yi, zi + 1);
        double c011 = hash01(seed, xi, yi + 1, zi + 1);
        double c111 = hash01(seed, xi + 1, yi + 1, zi + 1);
        double x00 = lerp(c000, c100, u), x10 = lerp(c010, c110, u);
        double x01 = lerp(c001, c101, u), x11 = lerp(c011, c111, u);
        double y0 = lerp(x00, x10, v), y1 = lerp(x01, x11, v);
        return lerp(y0, y1, w) * 2.0 - 1.0; // 0..1 → -1..1
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10); // smootherstep
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Детерминированный хеш-шум в [0,1) от (seed, x, y, z). */
    private double hash01(long seed, int x, int y, int z) {
        long h = seed;
        h = h * 6364136223846793005L + (x * 341873128712L);
        h = h * 6364136223846793005L + (y * 132897987541L);
        h = h * 6364136223846793005L + (z * 1274126177L);
        h ^= (h >>> 29);
        return ((h >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) 0x20000000000000L;
    }
}
