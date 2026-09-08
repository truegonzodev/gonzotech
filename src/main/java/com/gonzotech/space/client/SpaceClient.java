package com.gonzotech.space.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.space.SpaceDimensions;
import com.gonzotech.space.client.CelestialBody.Motion;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;

import java.util.List;

/**
 * Клиентская привязка скайбоксов космических измерений (Фаза 4, переработка).
 *
 * <p>Для каждого мира задаётся палитра неба (зенит/горизонт день+ночь + закатный
 * оттенок) и список небесных тел с СОБСТВЕННЫМИ текстурами и траекториями.
 * Солнца рисуются аддитивно ({@link CelestialBody#sun}), планеты обычным
 * наложением ({@link CelestialBody#planet}).
 *
 * <p>Смена цвета неба день/ночь — во всех трёх мирах (по требованию), но с
 * характером: Марс — светлое оранж-голубое днём → закат → тёмное; Луна —
 * тёмно-синее → лёгкий багрянец на закате → почти чёрное; Европа — почти чёрное
 * день/ночь с едва заметным багрянцем/фиолетом на терминаторе.
 */
public final class SpaceClient {

    private SpaceClient() {
    }

    private static ResourceLocation tex(String path) {
        return ResourceLocation.fromNamespaceAndPath(
            GonzoTechMod.MOD_ID, "textures/environment/" + path + ".png");
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
            bodies);
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
            bodies);
    }

    // ---- ЕВРОПА: почти чёрное день/ночь, едва багрянец/фиолет на терминаторе ----
    private static SpaceSkyEffects europa() {
        List<CelestialBody> bodies = List.of(
            CelestialBody.planet(tex("europa/jupiter"), 70F, Motion.FIXED,
                0F, 20F, 0F, -35F),
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
            bodies);
    }

    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(SpaceDimensions.MOON_SKY, moon());
        event.register(SpaceDimensions.MARS_SKY, mars());
        event.register(SpaceDimensions.EUROPA_SKY, europa());
    }
}
