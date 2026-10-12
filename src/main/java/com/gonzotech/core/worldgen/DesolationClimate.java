package com.gonzotech.core.worldgen;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Климат-гейт «Пустоши» (автор, 2026-10-12, вариант 4 — без биома).
 *
 * <p>Инжект биома в оверворлд оказался невозможен ни одним датапак-путём
 * (dimension-оверрайд ломает суперплоский, пресет-реестр принимает только
 * {@code {"preset":...}}, mixin в BiomeSource не попадает в биомную сетку
 * чанка). Поэтому «Пустошь» теперь — не биом, а климатическая зона: те же
 * четыре гиперкуба, что были в dimension-оверрайде, вычисляются НАПРЯМУЮ из
 * density-функций noise-router'а (публичный ванильный API), а фичи
 * (конверсия блоков, стволы, опал/саженцы) гейтятся этой проверкой и
 * навешены на весь оверворлд biome_modifier'ом.
 *
 * <p>Соответствие климат-параметров и density-функций (ваниль):
 * temperature→{@code temperature()}, humidity→{@code vegetation()},
 * continentalness→{@code continents()}, erosion→{@code erosion()},
 * weirdness→{@code ridges()}. Значения — сырые float'ы шума (примерно
 * [-2;2]), те же, что в parameter-point датапаков.
 *
 * <p>Границы зон — непрерывный шум, поэтому «Пустошь» получает органичные
 * гладкие границы (кварт-ячейка), а не чанковые секторы.
 *
 * <p>Fail-safe: любая ошибка вычисления → {@code false} (обычный мир).
 */
public final class DesolationClimate {

    private DesolationClimate() {
    }

    /**
     * Четыре гиперкуба [temp0,temp1, humid0,humid1, cont0,cont1, eros0,eros1,
     * weird0,weird1] — те же диапазоны, что были в удалённом
     * dimension-оверрайде (глубина не участвует: «Пустошь» — колонка).
     */
    private static final float[][] BOXES = {
        {0.15F, 0.50F, -0.35F, -0.05F,  0.20F,  0.50F, 0.15F, 0.45F, -0.15F,  0.25F},
        {0.15F, 0.50F,  0.00F,  0.25F,  0.40F,  0.70F, 0.35F, 0.65F, -0.45F, -0.15F},
        {0.15F, 0.50F, -0.35F, -0.05F, -0.19F, -0.11F, 0.15F, 0.45F, -0.15F,  0.25F},
        {0.15F, 0.50F,  0.00F,  0.25F, -0.19F, -0.11F, 0.35F, 0.65F, -0.45F, -0.15F},
    };

    /**
     * true, если климат в точке попадает в один из гиперкубов «Пустоши».
     * Точка — обычно поверхность колонки (высоты хватает любой близкой к
     * поверхности: температура/влажность меняются с y слабо, а
     * континентальность/эрозия/странность от y не зависят).
     */
    public static boolean isDesolation(RandomState randomState, int x, int y, int z) {
        try {
            NoiseRouter router = randomState.router();
            DensityFunction.FunctionContext ctx =
                new DensityFunction.SinglePointContext(x, y, z);
            double t = router.temperature().compute(ctx);
            double h = router.vegetation().compute(ctx);
            double c = router.continents().compute(ctx);
            double e = router.erosion().compute(ctx);
            double w = router.ridges().compute(ctx);
            for (float[] b : BOXES) {
                if (t >= b[0] && t <= b[1]
                    && h >= b[2] && h <= b[3]
                    && c >= b[4] && c <= b[5]
                    && e >= b[6] && e <= b[7]
                    && w >= b[8] && w <= b[9]) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false; // fail-safe: климат недоступен — обычная местность
        }
    }
}
