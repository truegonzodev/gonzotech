package com.gonzotech.space.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Пред-финальная проходка генерации Луны: вырезает «кратеры» — сплющенные по
 * высоте полусферы. Диаметр 5..50 блоков, глубина ~40% радиуса (сплющенность),
 * по краю приподнятый вал (rim) из того же поверхностного блока.
 *
 * <p>Работает по чанку: с шансом на чанк выбирает 0..2 центра, для каждого берёт
 * высоту рельефа (heightmap) и вырезает воздух внутри эллипсоида, а тонкий
 * ободок по границе слегка поднимает. Заменяются только «мягкие» поверхностные
 * блоки (лунный грунт/песок/порода) — бедрок не трогаем.
 *
 * <p>Регистрируется как обычная {@link Feature}; добавляется в биом Луны через
 * neoforge biome_modifier на позднем шаге генерации (после того как рельеф и
 * поверхность уже проставлены).
 */
public class CraterFeature extends Feature<NoneFeatureConfiguration> {

    /** Средняя доля чанков, где вообще появляется кратер (−36% к прежним 0.55). */
    private static final float CHUNK_CHANCE = 0.35F;
    private static final int MIN_DIAMETER = 5;
    private static final int MAX_DIAMETER = 50;
    /** Во сколько раз кратер площе полусферы (глубина = радиус * этот коэффициент). */
    private static final float FLATTEN = 0.4F;

    public CraterFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        if (random.nextFloat() > CHUNK_CHANCE) {
            return false;
        }

        int craters = 1 + random.nextInt(2); // 1..2
        for (int i = 0; i < craters; i++) {
            int cx = origin.getX() + random.nextInt(16);
            int cz = origin.getZ() + random.nextInt(16);
            int radius = (MIN_DIAMETER + random.nextInt(MAX_DIAMETER - MIN_DIAMETER + 1)) / 2;
            if (radius < 2) {
                radius = 2;
            }
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
            carve(level, cx, surfaceY, cz, radius);
        }
        return true;
    }

    private void carve(WorldGenLevel level, int cx, int cy, int cz, int radius) {
        int depth = Math.max(2, Math.round(radius * FLATTEN));
        BlockState rimState = surfaceBlock(level, cx, cy, cz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int minY = level.getMinY();
        int maxY = level.getMaxY();

        // ФИКС «сеточки»: НЕ читаем heightmap по каждой колонке (у соседних чанков
        // он ещё не финализирован на шаге top_layer_modification, оттого кратеры
        // «прилипали» к разной высоте и на швах чанков возникали круглые тёмные
        // артефакты). Вместо этого берём ОДНУ опорную высоту центра кратера и
        // вырезаем чашу относительно неё; фактическую вершину каждой колонки ищем
        // не по heightmap, а сканируя реальные блоки в узком окне у cy.
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double horiz = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (horiz > radius) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;

                // Реальная вершина колонки — первый непустой блок сверху вниз в
                // окне [cy+4 .. cy-depth-4]. Только реально сгенерированные блоки.
                int colTop = findLocalTop(level, pos, x, z, cy + 4, cy - depth - 4);
                if (colTop == Integer.MIN_VALUE) {
                    continue; // в этой колонке нечего резать
                }

                // Профиль чаши: глубина максимальна в центре, 0 на границе.
                double t = horiz / radius;                 // 0..1
                double bowl = (1.0 - t * t);               // параболическая чаша
                int dig = (int) Math.round(bowl * depth);

                // Вырезаем воздух сверху вниз на dig блоков от вершины колонки.
                for (int k = 0; k < dig; k++) {
                    int y = colTop - k;
                    if (y <= minY + 1 || y >= maxY) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState here = level.getBlockState(pos);
                    if (here.isAir() || here.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }

                // Приподнятый вал на самой границе кольца (t в [0.88..1.0]).
                if (t >= 0.88 && rimState != null) {
                    int rimY = colTop + 1;
                    if (rimY < maxY) {
                        pos.set(x, rimY, z);
                        if (level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, rimState, 2);
                        }
                    }
                }
            }
        }
    }

    /**
     * Ищет вершину колонки, сканируя реальные блоки сверху вниз в окне
     * [{@code fromY}..{@code toY}]. Возвращает Y первого непустого не-бедрок
     * блока или {@link Integer#MIN_VALUE}, если ничего не найдено. В отличие от
     * heightmap не зависит от порядка генерации соседних чанков.
     */
    private int findLocalTop(WorldGenLevel level, BlockPos.MutableBlockPos pos,
                             int x, int z, int fromY, int toY) {
        int hi = Math.min(fromY, level.getMaxY() - 1);
        int lo = Math.max(toY, level.getMinY());
        for (int y = hi; y >= lo; y--) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** Верхний непустой блок в колонке центра — им же строим вал. */
    private BlockState surfaceBlock(WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy >= -3; dy--) {
            pos.set(x, y + dy, z);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !state.is(Blocks.BEDROCK)) {
                return state;
            }
        }
        return null;
    }
}
