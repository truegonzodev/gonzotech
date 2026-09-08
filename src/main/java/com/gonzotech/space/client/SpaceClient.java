package com.gonzotech.space.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.space.SpaceDimensions;
import com.gonzotech.space.client.CelestialBody.Motion;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

import java.util.List;

/**
 * Клиентская привязка скайбоксов космических измерений (Фаза 4, Часть 2).
 *
 * <p>Для каждого мира задаётся:
 * <ul>
 *   <li>цвет фона-вакуума (ARGB);</li>
 *   <li>множитель тумана биома (чтобы горизонт сливался с фоном);</li>
 *   <li>список небесных тел с СОБСТВЕННЫМИ текстурами и траекториями.</li>
 * </ul>
 *
 * <p>Траектории намеренно не строго «восток→запад»: у тел разный азимут
 * ({@code axisYaw}) и наклон плоскости ({@code axisTilt}), что даёт косые
 * касательные дуги. Каждый id ({@code gonzotech:*_sky}) совпадает с полем
 * {@code "effects"} соответствующего {@code dimension_type}.
 */
public final class SpaceClient {

    private SpaceClient() {
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(
            GonzoTechMod.MOD_ID, "textures/environment/" + path + ".png");
    }

    // ---- ЛУНА: серо-чёрное небо; медленное солнце + неподвижная Земля на СЗ ----
    private static SpaceSkyEffects moon() {
        List<CelestialBody> bodies = List.of(
            // Солнце: цикл в 20 раз медленнее обычного, лёгкий наклон дуги.
            CelestialBody.disc(tex("moon/sun"), 28F, Motion.SUN,
                /*cycleDays*/ 20F, /*yaw*/ -90F, /*tilt*/ 12F, /*phase*/ 0F),
            // Земля: висит неподвижно на северо-западе, высоко над горизонтом.
            CelestialBody.disc(tex("moon/earth"), 40F, Motion.FIXED,
                /*cycleDays*/ 0F, /*yaw*/ -45F, /*tilt*/ 0F, /*phase(height)*/ -55F)
        );
        // Небо серо-чёрное (не чисто чёрное) — сливается с тёмным туманом 0x141414.
        return new SpaceSkyEffects(0.06F, 0xFF16161C, bodies);
    }

    // ---- МАРС: тусклый оранж с голубым отливом; обычный цикл, солнце меньше ----
    private static SpaceSkyEffects mars() {
        List<CelestialBody> bodies = List.of(
            // Солнце: обычный суточный цикл, диск заметно меньше (Марс дальше).
            CelestialBody.disc(tex("mars/sun"), 18F, Motion.SUN,
                1F, -90F, 8F, 0F),
            // Земля — голубая точка, плывёт ночью по косой касательной.
            CelestialBody.disc(tex("mars/earth"), 4F, Motion.ORBIT,
                1F, -70F, 28F, 150F),
            // Луна — белая точка рядом с Землёй, чуть иной наклон/фаза.
            CelestialBody.disc(tex("mars/moon"), 3F, Motion.ORBIT,
                1F, -70F, 34F, 162F)
        );
        // Небо тускло-оранжевое с дымкой — сливается с рыжим туманом 0x6f3e24.
        return new SpaceSkyEffects(0.22F, 0xFF7A4A2E, bodies);
    }

    // ---- ЕВРОПА: голубое небо; сутки ×3, солнце-точка, огромный Юпитер ----
    private static SpaceSkyEffects europa() {
        List<CelestialBody> bodies = List.of(
            // Огромный неподвижный Юпитер — доминирует над горизонтом.
            CelestialBody.disc(tex("europa/jupiter"), 70F, Motion.FIXED,
                0F, 20F, 0F, -35F),
            // Солнце — далёкая яркая точка, суточный цикл втрое быстрее.
            CelestialBody.disc(tex("europa/sun"), 5F, Motion.SUN,
                0.333F, -90F, 6F, 0F),
            // Мелкие спутники — быстрые касательные кольца.
            CelestialBody.disc(tex("europa/satellite"), 3F, Motion.ORBIT,
                0.333F, -60F, 40F, 40F),
            CelestialBody.disc(tex("europa/satellite"), 2.5F, Motion.ORBIT,
                0.333F, -50F, 46F, 200F),
            // Земля и Марс — 2 точки, медленнее спутников, у закатной дуги.
            CelestialBody.disc(tex("europa/earth"), 2.5F, Motion.ORBIT,
                1F, -80F, 20F, 140F),
            CelestialBody.disc(tex("europa/mars"), 2.5F, Motion.ORBIT,
                1F, -80F, 24F, 152F)
        );
        // Небо голубое (юзер: «работает») — совпадает с голубым туманом 0xc1d5fa.
        return new SpaceSkyEffects(0.03F, 0xFFB4CCF2, bodies);
    }

    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(SpaceDimensions.MOON_SKY, moon());
        event.register(SpaceDimensions.MARS_SKY, mars());
        event.register(SpaceDimensions.EUROPA_SKY, europa());
    }
}
