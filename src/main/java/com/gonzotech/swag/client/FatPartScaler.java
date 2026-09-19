package com.gonzotech.swag.client;

import net.minecraft.client.model.geom.ModelPart;

/**
 * «Жирение» части модели по двум осям из трёх: ширина (local X) и высота
 * (local Z — торс кота/волка повёрнут на 90° по X, поэтому мировая высота
 * это локальный Z; локальный Y = длина и НЕ масштабируется, автор 19.09:
 * «чтобы НЕ росло по длине, а по остальным двум осям росло»).
 *
 * <p>Масштаб умножаем, а не присваиваем: у baby-моделей в initialPose «запечён»
 * коэффициент 0.5 ({@code BabyModelTransform}), абсолютное присваивание
 * {@code xScale = fatness} сносило его — отсюда «у baby котят взрослое тело».</p>
 *
 * <p>Плюс компенсация пивота: куб масштабируется от опорной точки части, и если
 * центр куба не в ней, торс «уезжает» (у кота — вниз, «тело съехало»).
 * Сдвиг пивота на (f−1)·center·baseScale держит ЦЕНТР куба на месте:
 * жир растёт симметрично вверх (спина) и вниз (пузо).</p>
 *
 * <p>NB: класс живёт ВНЕ пакета {@code com.gonzotech.mixin.*} — классы из
 * mixin-пакета нельзя дёргать из миксинов (IllegalClassLoadError).</p>
 */
public final class FatPartScaler {

    /** Рост по высоте на 30% слабее роста по ширине (автор 20.09: ширину не трогаем). */
    private static final float HEIGHT_GROWTH = 0.7F;

    private FatPartScaler() {
    }

    /**
     * @param part         часть модели (вызывать после vanilla setupAnim —
     *                     масштабы уже сброшены в initialPose, включая baby-×0.5)
     * @param fatness      множитель жирности (1.0 = без изменений)
     * @param xCenterLocal X центра куба в локальных координатах части
     * @param zCenterLocal Z центра куба в локальных координатах части
     */
    public static void fatten(ModelPart part, float fatness, float xCenterLocal, float zCenterLocal) {
        if (fatness == 1.0F) {
            return;
        }
        float heightF = 1.0F + (fatness - 1.0F) * HEIGHT_GROWTH;
        float baseX = part.xScale;
        float baseZ = part.zScale;
        part.xScale *= fatness;
        part.zScale *= heightF;
        // компенсация уезда центра куба (только для частей с xRot ≈ 90°)
        part.x -= (fatness - 1.0F) * xCenterLocal * baseX;
        part.y += (heightF - 1.0F) * zCenterLocal * baseZ;
    }
}
