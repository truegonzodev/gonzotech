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
 *
 * <p>{@link Blend} управляет наложением спрайта на небо:
 * <ul>
 *   <li>{@link Blend#ADDITIVE} — как ванильное солнце: задействован только канал
 *       яркости, спрайт «светится» поверх неба (солнца всех миров).</li>
 *   <li>{@link Blend#NORMAL} — обычное alpha-наложение: спрайт как есть (Юпитер,
 *       Земля, планеты-точки).</li>
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
    int argb,
    Blend blend
) {

    public enum Motion {
        /** Восход/закат по дуге, цикл в {@code cycleDays} майнкрафт-суток. */
        SUN,
        /** Неподвижно: {@code axisYaw}=азимут, {@code axisTilt}=высота. */
        FIXED,
        /** Плывёт по наклонному кольцу (спутники / точки-планеты). */
        ORBIT,
        /**
         * ГОРИЗОНТАЛЬНЫЙ круг по азимуту: тело движется вокруг горизонта
         * (С→З→Ю→В) на фиксированной высоте. {@code axisTilt} — высота над
         * горизонтом в градусах (0 = ровно по горизонту), {@code cycleDays} —
         * период оборота, {@code phaseDeg} — стартовый азимут. Для Земли на
         * орбите Солнца (обходит горизонт и заходит за солнце на западе).
         */
        HORIZON_ORBIT
    }

    public enum Blend {
        /** Аддитивное наложение (ванильное солнце): светится поверх неба. */
        ADDITIVE,
        /** Обычное alpha-наложение: спрайт как есть. */
        NORMAL
    }

    /** Солнце: аддитивный диск (яркость поверх неба). */
    public static CelestialBody sun(ResourceLocation tex, float size, Motion motion,
                                    float cycleDays, float yaw, float tilt, float phase) {
        return new CelestialBody(tex, size, motion, cycleDays, yaw, tilt, phase,
            0xFFFFFFFF, Blend.ADDITIVE);
    }

    /** Планета/спутник: обычное alpha-наложение (спрайт как есть). */
    public static CelestialBody planet(ResourceLocation tex, float size, Motion motion,
                                       float cycleDays, float yaw, float tilt, float phase) {
        return new CelestialBody(tex, size, motion, cycleDays, yaw, tilt, phase,
            0xFFFFFFFF, Blend.NORMAL);
    }
}
