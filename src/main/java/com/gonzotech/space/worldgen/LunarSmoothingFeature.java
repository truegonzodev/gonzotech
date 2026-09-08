package com.gonzotech.space.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Финальный пост-проход генерации Луны (по фидбэку): лёгкое СГЛАЖИВАНИЕ рельефа
 * поверх уже сгенерированной поверхности и кратеров.
 *
 * <p>Работает как box-blur высот: для каждой колонки внутри чанка (без 2-блочной
 * кромки, чтобы не лезть в несгенерированные соседние чанки) берётся средняя
 * высота 4 соседей; если колонка сильно выше среднего — срезаем верхний блок в
 * воздух, если сильно ниже — досыпаем поверхностный блок сверху. Порог 2 блока —
 * трогаем только резкие пики/ямы, не убивая общий рельеф. Проход повторяется
 * {@link #PASSES} раз (эффект «1-2 лёгких сглаживания»).
 */
public class LunarSmoothingFeature extends Feature<NoneFeatureConfiguration> {

    /** Сколько проходов сглаживания. */
    private static final int PASSES = 3;
    /**
     * Разница высот (в блоках), выше которой колонка считается резким пиком.
     * =2: трогаем ТОЛЬКО реально острые выступы (края чаш кратеров после снятия
     * вала, ступеньки шума), НЕ съедая пологие холмы рельефа. Раньше =1 при 4
     * проходах слишком уплощало Луну в «плоский лист».
     */
    private static final int THRESHOLD = 2;

    public LunarSmoothingFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        int x0 = origin.getX();
        int z0 = origin.getZ();

        for (int pass = 0; pass < PASSES; pass++) {
            // Внутренняя область чанка: 2..13 (без кромки).
            for (int dx = 2; dx < 14; dx++) {
                for (int dz = 2; dz < 14; dz++) {
                    smoothColumn(level, x0 + dx, z0 + dz);
                }
            }
        }
        return true;
    }

    private void smoothColumn(WorldGenLevel level, int x, int z) {
        int h = topSolid(level, x, z);
        if (h == Integer.MIN_VALUE) {
            return;
        }
        int n = topSolid(level, x, z - 1);
        int s = topSolid(level, x, z + 1);
        int e = topSolid(level, x + 1, z);
        int w = topSolid(level, x - 1, z);
        if (n == Integer.MIN_VALUE || s == Integer.MIN_VALUE
            || e == Integer.MIN_VALUE || w == Integer.MIN_VALUE) {
            return;
        }
        int avg = Math.round((n + s + e + w) / 4.0F);

        // ВАЖНО (по фидбэку): сглаживаем ТОЛЬКО срезая резкие пики. НЕ засыпаем
        // впадины — иначе сглаживание «затапливало» чаши кратеров и убивало их.
        // Так холмистый рельеф Луны остаётся, кратеры целы, а шумные острые
        // выступы (грязь по краям кратеров/пикселизация шума) сглаживаются.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cut = h - avg;
        if (cut >= THRESHOLD) {
            // Срезаем сверху столько блоков, на сколько пик выше среднего (но не
            // ниже уровня соседей), максимум пара блоков за проход.
            int remove = Math.min(cut, 2);
            for (int k = 0; k < remove; k++) {
                pos.set(x, h - k, z);
                BlockState here = level.getBlockState(pos);
                if (here.isAir() || here.is(Blocks.BEDROCK)) {
                    break;
                }
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** Y верхнего непустого не-бедрок блока в колонке (или MIN_VALUE). */
    private int topSolid(WorldGenLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int top = level.getMaxY();
        int bottom = level.getMinY();
        for (int y = top - 1; y >= bottom; y--) {
            pos.set(x, y, z);
            BlockState st = level.getBlockState(pos);
            if (!st.isAir() && !st.is(Blocks.BEDROCK)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }
}
