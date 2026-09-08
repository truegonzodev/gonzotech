package com.gonzotech.space.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/**
 * Скайбокс космических измерений (Группа 1: Луна/Марс/Европа).
 *
 * <p>Реализация-плейсхолдер: используем {@link SkyType#END} — тёмное звёздное
 * небо без цикла солнца/луны и без облаков. Это даёт «космический» вид без
 * единой строки кастомного OpenGL, поэтому гарантированно компилируется и не
 * ломает клиент (в отличие от переопределения {@code renderSky}). Настоящие
 * текстурные скайбоксы (звёзды/планеты/Солнце) — следующая итерация.
 *
 * <p>Цвет тумана берётся из биома (см. {@code worldgen/biome/*.json}) — поэтому
 * у каждой планеты свой оттенок горизонта. Каждому измерению соответствует свой
 * экземпляр с собственным {@code fogFactor}, чтобы дымка отличалась.
 */
public class SpaceSkyEffects extends DimensionSpecialEffects {

    private final float fogFactor;

    /**
     * @param fogFactor множитель яркости тумана (0..1): 1.0 — как в биоме,
     *                  меньше — темнее к чёрному вакууму.
     */
    public SpaceSkyEffects(float fogFactor) {
        // cloudLevel=NaN (нет облаков), hasGround=false (не рисуем «пол» неба),
        // SkyType.END (звёздный вакуум без солнца/луны), forceBrightLightmap=false,
        // constantAmbientLight=true (свет не зависит от времени суток).
        super(Float.NaN, false, DimensionSpecialEffects.SkyType.END, false, true);
        this.fogFactor = fogFactor;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 biomeFogColor, float daylight) {
        // Игнорируем дневной цикл (в космосе его нет) и приглушаем туман к вакууму.
        return biomeFogColor.scale(fogFactor);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }
}
