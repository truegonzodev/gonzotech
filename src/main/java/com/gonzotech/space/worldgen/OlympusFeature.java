package com.gonzotech.space.worldgen;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * ГОРА ОЛИМП — гигантский вулканический конус, ВСЕГДА появляющийся в КАЖДОМ мире
 * (где размещена эта фича) в четырёх фиксированных точках:
 * (±20000, ±20000). Радиус основания 256 блоков, вершина у потолка застройки,
 * со снежной шапкой сверху.
 *
 * <p>Реализация детерминированная и НЕ зависит от сида: высота колонны считается
 * по расстоянию до ближайшего якоря. Фича запускается на каждом чанке шага
 * {@code raw_generation}, но реально что-то пишет только в чанках, попадающих в
 * радиус конуса вокруг якоря (иначе мгновенно выходит). Каждый чанк заполняет
 * СВОЙ участок конуса (пишем только внутри текущего чанка 16×16 → нет записей в
 * дальние чанки).
 */
public class OlympusFeature extends Feature<NoneFeatureConfiguration> {

    /** Абсолютные координаты якорей (углы «квадрата» ±20000). */
    private static final int[][] ANCHORS = {
        {  20000,  20000 },
        {  20000, -20000 },
        { -20000,  20000 },
        { -20000, -20000 },
    };

    private static final int RADIUS = 256;
    /** Небольшой шум высоты конуса, чтобы склон не был идеально гладким. */
    private static final double NOISE_AMP = 6.0;

    public OlympusFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int chunkMinX = (origin.getX() >> 4) << 4;
        int chunkMinZ = (origin.getZ() >> 4) << 4;

        // Найти якорь, чей конус пересекает этот чанк (иначе выходим сразу).
        int[] anchor = null;
        for (int[] a : ANCHORS) {
            // ближайшая точка чанка к якорю
            int nx = clamp(a[0], chunkMinX, chunkMinX + 15);
            int nz = clamp(a[1], chunkMinZ, chunkMinZ + 15);
            long dx = nx - a[0], dz = nz - a[1];
            if (dx * dx + dz * dz <= (long) RADIUS * RADIUS) {
                anchor = a;
                break;
            }
        }
        if (anchor == null) {
            return false;
        }

        int topBuild = level.getMaxY() - 1; // потолок застройки мира
        BlockState stone = ModBlocks.MARTIAN_STONE.get().defaultBlockState();
        BlockState rich = ModBlocks.RICH_MARTIAN_STONE.get().defaultBlockState();
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        RandomSource random = context.random();
        long noiseSeed = 0x0157A11L ^ ((long) anchor[0] * 73428767L + anchor[1] * 912931L);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = chunkMinX + lx;
                int wz = chunkMinZ + lz;
                double ddx = wx - anchor[0];
                double ddz = wz - anchor[1];
                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                if (dist > RADIUS) {
                    continue;
                }
                // Профиль конуса: 1 в центре → 0 на краю, слегка нелинейно
                // (немного «вулканический» — крутее у вершины).
                double frac = 1.0 - dist / RADIUS;
                double shaped = Math.pow(frac, 1.25);
                int baseY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz);
                if (baseY <= level.getMinY() + 1) {
                    baseY = level.getMinY() + 2;
                }
                double noise = valueNoise(noiseSeed, wx * 0.03, 0, wz * 0.03) * NOISE_AMP;
                int peakY = baseY + (int) Math.round(shaped * (topBuild - baseY) + noise * frac);
                if (peakY > topBuild) {
                    peakY = topBuild;
                }
                if (peakY <= baseY) {
                    continue;
                }
                // Снежная шапка выше порога высоты (верхняя часть конуса).
                int snowLine = baseY + (int) ((topBuild - baseY) * 0.72);
                for (int y = baseY; y <= peakY; y++) {
                    pos.set(wx, y, wz);
                    if (!level.getBlockState(pos).isAir()
                        && !level.getBlockState(pos).canBeReplaced()
                        && y < baseY + 2) {
                        // не трогаем исходную поверхность у самой земли лишний раз
                    }
                    BlockState put;
                    if (y >= peakY - 2 && y >= snowLine) {
                        put = snow;
                    } else {
                        put = random.nextInt(9) == 0 ? rich : stone;
                    }
                    level.setBlock(pos, put, 2);
                }
                placed = true;
            }
        }
        return placed;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---- гладкий 3D value-noise ----

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
        return lerp(y0, y1, w) * 2.0 - 1.0;
    }

    private static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double hash01(long seed, int x, int y, int z) {
        long h = seed;
        h = h * 6364136223846793005L + (x * 341873128712L);
        h = h * 6364136223846793005L + (y * 132897987541L);
        h = h * 6364136223846793005L + (z * 1274126177L);
        h ^= (h >>> 29);
        return ((h >>> 11) & 0x1FFFFFFFFFFFFFL) / (double) 0x20000000000000L;
    }
}
