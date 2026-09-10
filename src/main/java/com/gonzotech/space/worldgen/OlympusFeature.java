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
    /** Зарывание фундамента в грунт для исключения висячих краев и пустот под горой. */
    private static final int DIG_IN = 8;
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
                // Профиль конуса: 1 в центре → 0 на краю.
                double frac = 1.0 - (dist / RADIUS);
                double shaped = Math.pow(frac, 1.25);
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, wx, wz);
                if (surfaceY <= level.getMinY() + 1) {
                    surfaceY = level.getMinY() + 2;
                }
                double noise = valueNoise(noiseSeed, wx * 0.03, 0, wz * 0.03) * NOISE_AMP;
                int peakY = surfaceY + (int) Math.round(shaped * (topBuild - surfaceY) + noise * frac);
                if (peakY > topBuild) {
                    peakY = topBuild;
                }
                if (peakY < surfaceY) {
                    peakY = surfaceY;
                }

                // --- 1) ТЕЛО ВУЛКАНА (порода + зарывание) ---
                int fillFrom = Math.max(level.getMinY() + 2, surfaceY - DIG_IN);
                for (int y = fillFrom; y <= peakY; y++) {
                    pos.set(wx, y, wz);
                    if (y < surfaceY) {
                        BlockState current = level.getBlockState(pos);
                        if (current.isAir() || current.canBeReplaced() || current.is(Blocks.WATER)) {
                            level.setBlock(pos, stone, 2);
                        }
                    } else {
                        BlockState put = (random.nextInt(9) == 0) ? rich : stone;
                        level.setBlock(pos, put, 2);
                    }
                }

                // --- 2) СНЕГ КАК ТОНКИЙ СЛОЙ ---
                // Линия снега начинается примерно на 70% высоты горы
                int snowLine = surfaceY + (int) Math.round((topBuild - surfaceY) * 0.70);
                if (peakY >= snowLine - 3 && peakY + 1 < level.getMaxY()) {
                    // Двухоктавный value-noise для пятнистого/рваного начала
                    double n1 = valueNoise(noiseSeed ^ 0x9E3779B9L, wx * 0.04, 0, wz * 0.04);
                    double n2 = valueNoise(noiseSeed ^ 0x5F3759DFL, wx * 0.12, 0, wz * 0.12) * 0.5;
                    double snowNoise = (n1 + n2) / 1.5; // [-1..1]
                    double normNoise = (snowNoise + 1.0) * 0.5; // [0..1]

                    // Прогресс от линии снега до вершины
                    double snowT = (double) (peakY - snowLine) / Math.max(1, topBuild - snowLine);
                    snowT = Math.max(0.0, Math.min(1.0, snowT));

                    boolean isPeakTop = (peakY >= topBuild - 5);
                    // Покрытие плавно растет: ~15% у снеговой линии до 100% у пика
                    double coverage = isPeakTop ? 1.0 : (0.15 + 0.85 * snowT);

                    if (normNoise <= coverage || isPeakTop) {
                        int layers;
                        if (isPeakTop) {
                            layers = 8; // 100% + 8 слоев на последних 5 блоках вершины
                        } else {
                            layers = 1 + (int) Math.round(snowT * 7.0 + snowNoise * 1.5);
                            layers = Math.max(1, Math.min(8, layers));
                        }
                        pos.set(wx, peakY + 1, wz);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, snowLayer.setValue(SnowLayerBlock.LAYERS, layers), 2);
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
