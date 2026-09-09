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
        // мелкие/средние, изредка огромные). Диаметр 5..50 → r ~2..25.
        int maxR;
        float roll = random.nextFloat();
        if (roll < 0.55F) {
            maxR = 2 + random.nextInt(4);   // мелкие r2..5 (d 5..11)
        } else if (roll < 0.88F) {
            maxR = 6 + random.nextInt(7);   // средние r6..12 (d 12..25)
        } else {
            maxR = 13 + random.nextInt(13); // огромные r13..25 (d 26..50)
        }

        // Центр — около середины чанка (чтобы safe-зона не срезала крупные).
        int cx = chunkMinX + 4 + random.nextInt(8);
        int cz = chunkMinZ + 4 + random.nextInt(8);
        // Высота центра — в средней части мира с запасом maxR от краёв.
        int floor = level.getMinY() + 8 + maxR;
        int ceil = level.getMaxY() - 8 - maxR;
        if (ceil <= floor + 4) {
            return false;
        }
        int cy = floor + random.nextInt(ceil - floor);

        chaoticBlob(level, random, cx, cy, cz, maxR);
        return true;
    }

    /** true, если запись в (x,z) не выходит за безопасную 3×3-зону чанков. */
    private boolean inSafe(int x, int z) {
        return x >= safeMinX && x <= safeMaxX && z >= safeMinZ && z <= safeMaxZ;
    }

    /**
     * Глыба причудливой формы (не эллипсоид): metaball-объединение долей +
     * шумовая шероховатость краёв. С одной случайной стороны — корка
     * сверхплотного льда (полусфера по выбранной оси).
     */
    private void chaoticBlob(WorldGenLevel level, RandomSource random,
                             int cx, int cy, int cz, int maxR) {
        int lobes = 3 + random.nextInt(6); // 3..8 долей
        int[] lx = new int[lobes], ly = new int[lobes], lz = new int[lobes];
        double[] lr = new double[lobes];
        for (int i = 0; i < lobes; i++) {
            double spread = 0.55;
            lx[i] = (int) Math.round((random.nextDouble() * 2 - 1) * maxR * spread);
            ly[i] = (int) Math.round((random.nextDouble() * 2 - 1) * maxR * spread * 0.8);
            lz[i] = (int) Math.round((random.nextDouble() * 2 - 1) * maxR * spread);
            lr[i] = maxR * (0.35 + random.nextDouble() * 0.45); // доля 0.35..0.8·R
        }
        boolean porous = maxR >= 8 && random.nextInt(3) == 0;
        long poreSeed = random.nextLong();
        long edgeSeed = random.nextLong();

        // Ледяная корка с ОДНОЙ случайной стороны: ось (0=x,1=y,2=z) и знак.
        int iceAxis = random.nextInt(3);
        int iceSign = random.nextBoolean() ? 1 : -1;
        // Толщина корки — доля радиуса (глубина «шапки» от края к центру).
        double iceDepth = maxR * (0.28 + random.nextDouble() * 0.17); // 0.28..0.45·R

        BlockState meteor = ModBlocks.METEOR.get().defaultBlockState();
        BlockState ice = ModBlocks.SUPERDENSE_ICE.get().defaultBlockState();

        int R = maxR + 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                for (int dz = -R; dz <= R; dz++) {
                    // metaball-union: воксель внутри, если попал хотя бы в одну долю.
                    boolean inside = false;
                    double nearest = 9.9;
                    for (int i = 0; i < lobes; i++) {
                        double ddx = dx - lx[i], ddy = dy - ly[i], ddz = dz - lz[i];
                        double n = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) / lr[i];
                        if (n <= 1.0) {
                            inside = true;
                        }
                        nearest = Math.min(nearest, n);
                    }
                    if (!inside) {
                        continue;
                    }
                    // Рваная кромка → «причудливая» поверхность.
                    if (nearest > 0.80 && hash01(edgeSeed, dx, dy, dz) < 0.45) {
                        continue;
                    }
                    if (porous && nearest < 0.65 && pore(poreSeed, dx, dy, dz)) {
                        continue;
                    }
                    int x = cx + dx, y = cy + dy, z = cz + dz;
                    if (!inSafe(x, z)) {
                        continue;
                    }
                    if (y <= level.getMinY() + 2 || y >= level.getMaxY() - 1) {
                        continue;
                    }
                    // Корка льда: на выбранной стороне глыбы в пределах iceDepth
                    // от края (nearest близко к 1) И по нужному знаку оси.
                    boolean crust = false;
                    if (nearest > (1.0 - iceDepth / Math.max(1.0, maxR))) {
                        int comp = (iceAxis == 0) ? dx : (iceAxis == 1) ? dy : dz;
                        if (Math.signum(comp) == iceSign && Math.abs(comp) > maxR * 0.15) {
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

    /** Детерминированная «пористость»: ~30% вокселей внутри становятся пустотами. */
    private boolean pore(long seed, int dx, int dy, int dz) {
        long h = seed;
        h = h * 6364136223846793005L + (dx * 341873128712L);
        h = h * 6364136223846793005L + (dy * 132897987541L);
        h = h * 6364136223846793005L + (dz * 1274126177L);
        return ((h >>> 33) % 10) < 3;
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
