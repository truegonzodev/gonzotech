package com.gonzotech.space.client;

import net.minecraft.resources.ResourceLocation;

/**
 * Описание одного небесного тела на скайбоксе космического измерения.
 *
 * <p>У каждого тела СВОЯ текстура ({@link #texture}) — намеренно не
 * переиспользуем и не масштабируем одну картинку, чтобы сохранить ванильную
 * «пиксельную плотность» (см. требование пользователя). Видимый размер задаётся
 * {@link #size} (полудлина квадрата в «небесных» единицах, ванильное солнце ~30).
 *
 * <p>Движение задаётся {@link Motion}:
 * <ul>
 *   <li>{@link Motion#SUN} — восход/закат по дуге; {@link #cycleDays} = сколько
 *       МАЙНКРАФТ-суток длится один цикл (Луна 20, Марс 1, Европа 3).</li>
 *   <li>{@link Motion#FIXED} — неподвижно висит; азимут = {@link #axisYaw},
 *       высота над горизонтом = {@link #axisTilt} (Земля над Луной, Юпитер над
 *       Европой).</li>
 *   <li>{@link Motion#ORBIT} — плывёт по наклонному кольцу (касательные
 *       траектории спутников / Земли-Марса-Луны); {@link #cycleDays} — период,
 *       {@link #axisTilt} — наклон кольца, {@link #phaseDeg} — сдвиг фазы.</li>
 * </ul>
 */
public record CelestialBody(
    ResourceLocation texture,
    float size,
    Motion motion,
    float cycleDays,
    float axisYaw,
    float axisTilt,
    float phaseDeg,
    int argb
) {

    public enum Motion {
        /** Восход/закат по дуге, цикл в {@code cycleDays} майнкрафт-суток. */
        SUN,
        /** Неподвижно: {@code axisYaw}=азимут, {@code axisTilt}=высота. */
        FIXED,
        /** Плывёт по наклонному кольцу (спутники / точки-планеты). */
        ORBIT
    }

    /** Тело-«диск» (Солнце/планета) с полной непрозрачностью и заданным размером. */
    public static CelestialBody disc(ResourceLocation tex, float size, Motion motion,
                                     float cycleDays, float yaw, float tilt, float phase) {
        return new CelestialBody(tex, size, motion, cycleDays, yaw, tilt, phase, 0xFFFFFFFF);
    }
}
