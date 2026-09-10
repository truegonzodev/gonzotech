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
 * МАРС — КЛАСТЕРЫ КАМЕННЫХ СТОЛБОВ («выветренные останцы», реф. Мань-Пупу-Нёр):
 * группа из нескольких вертикальных столбов марсианского камня, присыпанных
 * сверху марсианским песком. Кластеры редкие (общая пустынность сохраняется).
 *
 * <p>Каждый столб: высота 15..40 блоков и радиус 6..20 — НЕЗАВИСИМО друг от
 * друга (бывают и тонкие высокие, и толстые низкие). Форма — слегка неровный
 * цилиндр с шумовой эрозией краёв и лёгким искривлением/сужением кверху. На
 * вершине — «шапка» из марсианского песка.
 *
 * <p>Столбы растут от поверхности (MOTION_BLOCKING) вверх. Все записи в
 * безопасной зоне 3×3 чанка вокруг origin.
 */
public class MarsPillarFeature extends Feature<NoneFeatureConfiguration> {

    public MarsPillarFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

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

        // Кластер: 2..5 столбов, сгруппированных вокруг центра чанка.
        int pillars = 2 + random.nextInt(4);
        boolean placedAny = false;
        for (int i = 0; i < pillars; i++) {
            // Радиус столба ограничиваем так, чтобы вся группа влезла в safe-зону.
            int radius = 6 + random.nextInt(15); // 6..20
            radius = Math.min(radius, 10);        // но не шире ~10 (запас под кластер)
            int height = 15 + random.nextInt(26); // 15..40

            // Центр столба со сдвигом от центра чанка (кластеризация).
            int cx = chunkMinX + 8 + (int) Math.round((random.nextDouble() * 2 - 1) * 5);
            int cz = chunkMinZ + 8 + (int) Math.round((random.nextDouble() * 2 - 1) * 5);
            int baseY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
            if (baseY <= level.getMinY() + 1) {
                continue;
            }
            growPillar(level, random, cx, baseY, cz, radius, height);
            placedAny = true;
        }
        return placedAny;
    }

    private boolean inSafe(int x, int z) {
        return x >= safeMinX && x <= safeMaxX && z >= safeMinZ && z <= safeMaxZ;
    }

    /**
     * Один столб: неровный цилиндр с шумовой эрозией края, лёгким искривлением
     * оси и сужением кверху, с песчаной шапкой сверху.
     */
    private void growPillar(WorldGenLevel level, RandomSource random,
                            int cx, int baseY, int cz, int radius, int height) {
        long noiseSeed = random.nextLong();
        double bendScale = 0.05 + random.nextDouble() * 0.04;
        double bendAmp = 1.5 + random.nextDouble() * 2.5;

        BlockState stone = ModBlocks.MARTIAN_STONE.get().defaultBlockState();
        BlockState rich = ModBlocks.RICH_MARTIAN_STONE.get().defaultBlockState();
        BlockState sandTop;
        // «Песок» сверху: если есть отдельный блок марсианского песка — можно
        // подменить; пока используем марсианский грунт как «присыпку».
        sandTop = ModBlocks.MARTIAN_DIRT.get().defaultBlockState();

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int topY = baseY + height;
        for (int dy = 0; dy < height; dy++) {
            int y = baseY + dy;
            double t = (double) dy / height; // 0 низ … 1 верх
            // Сужение кверху: радиус падает до ~55% на вершине.
            double taper = 1.0 - 0.45 * t;
            double rAt = radius * taper;
            // Искривление оси (плавный дрейф центра).
            int ox = (int) Math.round(valueNoise(noiseSeed, 50, y * bendScale, 0) * bendAmp);
            int oz = (int) Math.round(valueNoise(noiseSeed, 0, y * bendScale, 50) * bendAmp);
            int rCeil = (int) Math.ceil(rAt) + 1;
            for (int dx = -rCeil; dx <= rCeil; dx++) {
                for (int dz = -rCeil; dz <= rCeil; dz++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    // Шумовая эрозия края → «выветренная» поверхность.
                    double wob = valueNoise(noiseSeed, dx, y * 0.4, dz) * (radius * 0.18);
                    if (dist > rAt + wob) {
                        continue;
                    }
                    int wx = cx + ox + dx, wz = cz + oz + dz;
                    if (!inSafe(wx, wz)) {
                        continue;
                    }
                    if (y <= level.getMinY() + 1 || y >= level.getMaxY() - 1) {
                        continue;
                    }
                    pos.set(wx, y, wz);
                    if (!isReplaceable(level.getBlockState(pos))) {
                        continue;
                    }
                    // Верхние 1-2 слоя столба — песчаная шапка.
                    BlockState put;
                    if (y >= topY - 1 - random.nextInt(2)) {
                        put = sandTop;
                    } else {
                        put = random.nextInt(8) == 0 ? rich : stone;
                    }
                    level.setBlock(pos, put, 2);
                }
            }
        }
    }

    private boolean isReplaceable(BlockState st) {
        return st.isAir()
            || st.is(Blocks.WATER)
            || st.is(ModBlocks.MARTIAN_STONE.get())
            || st.is(ModBlocks.MARTIAN_DIRT.get())
            || st.canBeReplaced();
    }

    // ---- гладкий 3D value-noise (как в MeteorFeature) ----

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
