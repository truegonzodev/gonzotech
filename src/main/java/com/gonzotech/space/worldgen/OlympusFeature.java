package com.gonzotech.space.worldgen;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * ГОРА ОЛИМП (Olympus Mons) — колоссальный вулканический конус на Марсе.
 * Появляется в четырёх фиксированных точках: (±20000, ±20000).
 * Радиус основания 320 блоков, вершина у потолка застройки, со слоистой снежной шапкой.
 *
 * <p>Реализация детерминированная и не зависит от сида: высота колонны считается
 * по расстоянию до ближайшего якоря. Фича запускается на каждом чанке шага
 * {@code surface_structures}, но реально что-то пишет только в чанках, попадающих
 * в радиус конуса вокруг якоря (иначе мгновенно выходит). Каждый чанк заполняет
 * СВОЙ участок конуса (только внутри 16×16 чанка).
 */
public class OlympusFeature extends Feature<NoneFeatureConfiguration> {

    /** Абсолютные координаты якорей (углы «квадрата» ±20000). */
    private static final int[][] ANCHORS = {
        {  20000,  20000 },
        {  20000, -20000 },
        { -20000,  20000 },
        { -20000, -20000 },
    };

    /** Радиус основания вулкана: 320 блоков. */
    private static final int RADIUS = 320;
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

        int topBuild = level.getMaxY() - 2; // потолок застройки с запасом под слой снега
        BlockState stone = ModBlocks.MARTIAN_STONE.get().defaultBlockState();
        BlockState rich = ModBlocks.RICH_MARTIAN_STONE.get().defaultBlockState();
        BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState snowLayer = Blocks.SNOW.defaultBlockState();
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
                // Профиль щитового вулкана: 1 в центре → 0 на краю.
                double frac = 1.0 - (dist / RADIUS);
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
                if (peakY < baseY) {
                    peakY = baseY;
                }

                // Шум границы снега для естественного волнистого края шапки
                double snowWarp = valueNoise(noiseSeed ^ 0x9E3779B9L, wx * 0.04, 0, wz * 0.04) * 0.08;
                int snowLine = baseY + (int) Math.round((topBuild - baseY) * (0.68 + snowWarp));

                // ЗАРЫВАНИЕ: заполняем фундамент на 10 блоков вглубь от поверхности,
                // чтобы исключить висячие склоны, дыры от каверн и трещины под горой.
                int foundationBottom = Math.max(level.getMinY() + 2, baseY - 10);
                for (int y = foundationBottom; y < baseY; y++) {
                    pos.set(wx, y, wz);
                    BlockState current = level.getBlockState(pos);
                    if (current.isAir() || current.canBeReplaced() || current.is(Blocks.WATER)) {
                        level.setBlock(pos, stone, 2);
                    }
                }

                // Тело вулкана и снежная шапка
                for (int y = baseY; y <= peakY; y++) {
                    pos.set(wx, y, wz);
                    BlockState put;
                    if (y >= snowLine) {
                        // Верхняя снежная шапка:
                        // На самой вершине и у поверхности шапки — плотный снежный блок
                        if (y >= peakY - 2 || y >= snowLine + 12) {
                            put = snowBlock;
                        } else {
                            put = (random.nextInt(4) == 0) ? stone : snowBlock;
                        }
                    } else {
                        // Скалистое тело вулкана
                        put = (random.nextInt(9) == 0) ? rich : stone;
                    }
                    level.setBlock(pos, put, 2);
                }

                // СНЕЖНАЯ ШАПКА СЛОЯМИ НА ВЕРШИНЕ (поверх блоков peakY):
                if (peakY + 1 < level.getMaxY()) {
                    pos.set(wx, peakY + 1, wz);
                    if (level.getBlockState(pos).isAir()) {
                        if (peakY >= snowLine) {
                            int heightAboveSnow = peakY - snowLine;
                            int layerCount;
                            if (heightAboveSnow >= 10) {
                                layerCount = 6 + random.nextInt(3); // 6..8 слоёв
                            } else if (heightAboveSnow >= 5) {
                                layerCount = 3 + random.nextInt(3); // 3..5 слоёв
                            } else {
                                layerCount = 1 + random.nextInt(3); // 1..3 слоя
                            }
                            layerCount = Math.max(1, Math.min(8, layerCount));
                            level.setBlock(pos, snowLayer.setValue(SnowLayerBlock.LAYERS, layerCount), 2);
                        } else if (peakY >= snowLine - 6 && snowWarp > 0.02) {
                            // Островная легкая присыпка у границы снега
                            int layerCount = 1 + random.nextInt(2);
                            level.setBlock(pos, snowLayer.setValue(SnowLayerBlock.LAYERS, layerCount), 2);
                        }
                    }
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
        double x01 = lerp(c001, c101, u), x11 = lerp(c011, c101, u);
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
