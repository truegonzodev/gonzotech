package com.gonzotech.space.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.space.SpaceDimensions;
import com.gonzotech.space.client.CelestialBody.Motion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

import java.util.List;

/**
 * Клиентская привязка скайбоксов космических измерений (Фаза 4, переработка).
 *
 * <p>Для каждого мира задаётся палитра неба (зенит/горизонт день+ночь + закатный
 * оттенок) и список небесных тел с СОБСТВЕННЫМИ текстурами и траекториями.
 * Солнца рисуются аддитивно ({@link CelestialBody#sun}), планеты обычным
 * наложением ({@link CelestialBody#planet}).
 */
public final class SpaceClient {

    private SpaceClient() {
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(
            GonzoTechMod.MOD_ID, "textures/environment/" + path + ".png");
    }

    // ---- ОВЕРВОРЛД: динамическое солнце по глобальному SunState ----
    private static SpaceSkyEffects overworld() {
        List<CelestialBody> bodies = List.of(
            CelestialBody.sun(tex("overworld/sun"), 30F, Motion.SUN,
                1F, -90F, 0F, 0F),
            CelestialBody.planet(tex("mars/moon"), 20F, Motion.SUN,
                1F, -90F, 0F, 180F)
        );
        return new SpaceSkyEffects(
            0.0F,
            /*zenithDay */ 0xFF78A7FF, /*zenithNight*/ 0xFF050510,
            /*horizonDay*/ 0xFFC0D8FF, /*horizonNight*/ 0xFF0A0A18,
            /*sunset    */ 0xDCFFA64A,
            bodies,
            /*daylightScale*/ 1.00F,
            /*starNight*/ 0.80F, /*starDay*/ 0.00F);
    }

    // ---- ЛУНА: тёмно-синее→багрянец(закат)→почти чёрное; Земля висит на СЗ ----
    private static SpaceSkyEffects moon() {
        List<CelestialBody> bodies = List.of(
            CelestialBody.sun(tex("moon/sun"), 22F, Motion.SUN,
                /*cycleDays*/ 60F, /*yaw*/ -90F, /*tilt*/ 12F, /*phase*/ 0F),
            CelestialBody.planet(tex("moon/earth"), 40F, Motion.FIXED,
                0F, -45F, 0F, -55F)
        );
        return new SpaceSkyEffects(
            0.06F,
            /*zenithDay */ 0xFF050507, /*zenithNight*/ 0xFF010101,
            /*horizonDay*/ 0xFF0A0A0E, /*horizonNight*/ 0xFF020203,
            /*sunset    */ 0x70432038, // очень слабый багрянец
            bodies,
            /*daylightScale*/ 0.30F, // день −70%: даже в зените сумрачно
            /*starNight*/ 0.80F, /*starDay*/ 0.70F); // звёзды приглушены (80% ночью)
    }

    // ---- МАРС: светлое оранж-голубое днём → закат → тёмное; солнце меньше ----
    private static SpaceSkyEffects mars() {
        List<CelestialBody> bodies = List.of(
            CelestialBody.sun(tex("mars/sun"), 16F, Motion.SUN,
                1F, -90F, 8F, 0F),
            CelestialBody.planet(tex("mars/earth"), 4F, Motion.ORBIT,
                1F, -70F, 28F, 150F),
            CelestialBody.planet(tex("mars/moon"), 3F, Motion.ORBIT,
                1F, -70F, 34F, 162F)
        );
        return new SpaceSkyEffects(
            0.22F,
            /*zenithDay */ 0xFF6E7C8A, /*zenithNight*/ 0xFF0A0806, // днём: голубовато-серый зенит
            /*horizonDay*/ 0xFFC26A2E, /*horizonNight*/ 0xFF180A04, // днём: оранжевая пылевая дымка у горизонта
            /*sunset    */ 0x90B0521C, // насыщенный оранж-закат
            bodies,
            /*daylightScale*/ 1.00F, // освещение НЕ трогаем (ванильное)
            /*starNight*/ 0.60F, /*starDay*/ 0.00F); // звёзды ТОЛЬКО ночью (атмосфера засвечивает днём)
    }

    // ---- ЕВРОПА: почти чёрное день/ночь, едва багрянец/фиолет на терминаторе ----
    private static SpaceSkyEffects europa() {
        List<CelestialBody> bodies = List.of(
            CelestialBody.planet(tex("europa/jupiter"), 70F, Motion.FIXED,
                0F, 20F, 0F, -72F),
            CelestialBody.sun(tex("europa/sun"), 5F, Motion.SUN,
                /*cycleDays*/ 3.5F, -90F, 6F, 0F),
            CelestialBody.planet(tex("europa/satellite"), 3F, Motion.ORBIT,
                0.333F, -60F, 40F, 40F),
            CelestialBody.planet(tex("europa/satellite"), 2.5F, Motion.ORBIT,
                0.333F, -50F, 46F, 200F),
            CelestialBody.planet(tex("europa/earth"), 2.5F, Motion.ORBIT,
                1F, -80F, 20F, 140F),
            CelestialBody.planet(tex("europa/mars"), 2.5F, Motion.ORBIT,
                1F, -80F, 24F, 152F)
        );
        return new SpaceSkyEffects(
            0.03F,
            /*zenithDay */ 0xFF020306, /*zenithNight*/ 0xFF000001, // почти чёрный с намёком на синь
            /*horizonDay*/ 0xFF04060C, /*horizonNight*/ 0xFF010102, // тёмно-синий у горизонта
            /*sunset    */ 0x50392046, // едва заметный фиолет
            bodies,
            /*daylightScale*/ 0.12F, // день −88%: почти всегда полумрак
            /*starNight*/ 0.88F, /*starDay*/ 0.85F); // звёзды 85-88%
    }

    // ---- ОРБИТА СОЛНЦА: чёрное небо, огромное статичное солнце у горизонта ----
    private static SpaceSkyEffects solarOrbit() {
        List<CelestialBody> bodies = List.of(
            new CelestialBody(tex("solar_orbit/earth"), 8F,
                Motion.HORIZON_ORBIT, /*cycleDays*/ 8F, /*yaw n/a*/ 0F,
                /*altitude*/ 6F, /*startAzimuth*/ 200F, 0xFFFFFFFF,
                CelestialBody.Blend.NORMAL),
            CelestialBody.sun(tex("solar_orbit/sun"), 49F, Motion.FIXED,
                1F, /*yaw=запад*/ 90F, 0F, /*phase=горизонт*/ 90F)
        );
        return new SpaceSkyEffects(
            0.10F,
            /*zenithDay */ 0xFF010101, /*zenithNight*/ 0xFF010101, // чёрное всегда
            /*horizonDay*/ 0xFF140A04, /*horizonNight*/ 0xFF140A04, // тёмный тёплый у горизонта
            /*sunset    */ 0x00000000,
            bodies,
            /*daylightScale*/ 1.00F,
            /*starNight*/ 0.88F, /*starDay*/ 0.88F,
            /*fixedDaylight*/ 0.40F);
    }

    // ---- ОРБИТА АЛЬФА ЦЕНТАВРА: то же, но БЕЗ Земли ----
    private static SpaceSkyEffects alphaCentauriOrbit() {
        List<CelestialBody> bodies = List.of(
            CelestialBody.sun(tex("alpha_centauri_orbit/sun"), 49F, Motion.FIXED,
                1F, /*yaw=запад*/ 90F, 0F, /*phase=горизонт*/ 90F)
        );
        return new SpaceSkyEffects(
            0.10F,
            /*zenithDay */ 0xFF010101, /*zenithNight*/ 0xFF010101,
            /*horizonDay*/ 0xFF120A06, /*horizonNight*/ 0xFF120A06,
            /*sunset    */ 0x00000000,
            bodies,
            /*daylightScale*/ 1.00F,
            /*starNight*/ 0.88F, /*starDay*/ 0.88F,
            /*fixedDaylight*/ 0.40F);
    }

    // ---- ОТКРЫТЫЙ КОСМОС И МИРЫ ЧЁРНЫХ ДЫР: звёзды + галактики ----
    private static SpaceSkyEffects deepSpace() {
        List<CelestialBody> bodies = List.of(
            new CelestialBody(tex("deep_space/galaxy1"), 40F, Motion.SUN,
                60F, /*yaw*/ 10F, /*tilt*/ 25F, /*phase*/ 0F,
                0xFFFFFFFF, CelestialBody.Blend.ADDITIVE),
            new CelestialBody(tex("deep_space/galaxy2"), 34F, Motion.SUN,
                75F, /*yaw*/ 140F, /*tilt*/ -35F, /*phase*/ 120F,
                0xFFFFFFFF, CelestialBody.Blend.ADDITIVE),
            new CelestialBody(tex("deep_space/galaxy3"), 46F, Motion.SUN,
                90F, /*yaw*/ -70F, /*tilt*/ 55F, /*phase*/ 210F,
                0xFFFFFFFF, CelestialBody.Blend.ADDITIVE),
            new CelestialBody(tex("deep_space/galaxy4"), 30F, Motion.SUN,
                70F, /*yaw*/ 220F, /*tilt*/ 15F, /*phase*/ 300F,
                0xFFFFFFFF, CelestialBody.Blend.ADDITIVE)
        );
        return new SpaceSkyEffects(
            0.06F,
            /*zenithDay */ 0xFF000001, /*zenithNight*/ 0xFF000001, // чернота
            /*horizonDay*/ 0xFF010102, /*horizonNight*/ 0xFF010102,
            /*sunset    */ 0x00000000,
            bodies,
            /*daylightScale*/ 1.00F,
            /*starNight*/ 0.90F, /*starDay*/ 0.90F,
            /*fixedDaylight*/ 0.35F);
    }

    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(Level.OVERWORLD.location(), overworld());
        event.register(SpaceDimensions.MOON_SKY, moon());
        event.register(SpaceDimensions.MARS_SKY, mars());
        event.register(SpaceDimensions.EUROPA_SKY, europa());
        event.register(SpaceDimensions.SOLAR_ORBIT_SKY, solarOrbit());
        event.register(SpaceDimensions.ALPHA_CENTAURI_ORBIT_SKY, alphaCentauriOrbit());
        event.register(SpaceDimensions.DEEP_SPACE_SKY, deepSpace());
        event.register(SpaceDimensions.BLACKHOLE_YX989_K2_SKY, deepSpace());
        event.register(SpaceDimensions.BLACKHOLE_ZANGLER_11_SKY, deepSpace());
    }
}
