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
                /*cycleDays*/ 20F, /*yaw*/ -90F, /*tilt*/ 12F, /*phase*/ 0F),
            CelestialBody.planet(tex("moon/earth"), 40F, Motion.FIXED,
                0F, -45F, 0F, -55F)
        );
        return new SpaceSkyEffects(
            0.06F,
            /*zenithDay */ 0xFF10131F, /*zenithNight*/ 0xFF040406,
            /*horizonDay*/ 0xFF1A2038, /*horizonNight*/ 0xFF07070C,
            /*sunset    */ 0xB0432038, // приглушённый багрянец
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
            /*zenithDay */ 0xFF6E7EA6, /*zenithNight*/ 0xFF0B0A12, // днём голубоватый верх
            /*horizonDay*/ 0xFFC77A44, /*horizonNight*/ 0xFF241019, // днём оранжевая дымка
            /*sunset    */ 0xC0D25A2A, // насыщенный оранж-закат
            bodies);
    }

    // ---- ЕВРОПА: почти чёрное день/ночь, едва багрянец/фиолет на терминаторе ----
    private static SpaceSkyEffects europa() {
        List<CelestialBody> bodies = List.of(
            CelestialBody.planet(tex("europa/jupiter"), 70F, Motion.FIXED,
                0F, 20F, 0F, -35F),
            CelestialBody.sun(tex("europa/sun"), 5F, Motion.SUN,
                0.333F, -90F, 6F, 0F),
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
            /*zenithDay */ 0xFF0A1430, /*zenithNight*/ 0xFF03060F, // тёмно-синий верх (не чёрный)
            /*horizonDay*/ 0xFF14284F, /*horizonNight*/ 0xFF060A18, // чуть светлее синий у горизонта
            /*sunset    */ 0x60392046, // едва заметный багрянец/фиолет
            bodies);
    }

    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(SpaceDimensions.MOON_SKY, moon());
        event.register(SpaceDimensions.MARS_SKY, mars());
        event.register(SpaceDimensions.EUROPA_SKY, europa());
    }
}
