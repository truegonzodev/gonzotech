package com.gonzotech.space;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Реестр «локаций» космического пласта (Фаза 4).
 *
 * <p>Здесь только КЛЮЧИ измерений ({@link ResourceKey}) и идентификаторы
 * скайбоксов ({@link net.minecraft.client.renderer.DimensionSpecialEffects}).
 * Сами измерения/типы/ноиз-настройки описаны data-паком в
 * {@code data/gonzotech/dimension[_type]} и {@code worldgen/*} — движок грузит
 * их сам; Java-код лишь ссылается на них по id.
 */
public final class SpaceDimensions {

    private SpaceDimensions() {
    }

    /** id → ключ измерения. Порядок сохранён (LinkedHashMap) для команды tp. */
    public static final Map<String, ResourceKey<Level>> DIMENSIONS = new LinkedHashMap<>();

    private static ResourceKey<Level> dim(String path) {
        ResourceKey<Level> key = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, path));
        DIMENSIONS.put(path, key);
        return key;
    }

    // --- Группа 1: worldgen + skybox -------------------------------------
    public static final ResourceKey<Level> MOON = dim("moon");
    public static final ResourceKey<Level> MARS = dim("mars");
    public static final ResourceKey<Level> EUROPA = dim("europa");

    // --- Группа 2: пустые орбиты/космос (только скайбокс + редкие глыбы) ---
    /** Орбита Солнца: огромное статичное солнце у горизонта, Земля по кругу. */
    public static final ResourceKey<Level> SOLAR_ORBIT = dim("solar_orbit");
    /** Орбита Альфа Центавра: как орбита Солнца, но без Земли. */
    public static final ResourceKey<Level> ALPHA_CENTAURI_ORBIT = dim("alpha_centauri_orbit");
    /** Открытый космос: звёзды + 3-4 больших квада (галактики), медленный дрейф. */
    public static final ResourceKey<Level> DEEP_SPACE = dim("deep_space");

    // --- Группа 3: миры чёрных дыр ---------------------------------------
    public static final ResourceKey<Level> BLACKHOLE_YX989_K2 = dim("blackhole_yx989_k2");
    public static final ResourceKey<Level> BLACKHOLE_ZANGLER_11 = dim("blackhole_zangler_11");

    // --- Идентификаторы скайбоксов (dimension_type -> "effects") ----------
    public static final ResourceLocation MOON_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "moon_sky");
    public static final ResourceLocation MARS_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "mars_sky");
    public static final ResourceLocation EUROPA_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "europa_sky");
    public static final ResourceLocation SOLAR_ORBIT_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "solar_orbit_sky");
    public static final ResourceLocation ALPHA_CENTAURI_ORBIT_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "alpha_centauri_orbit_sky");
    public static final ResourceLocation DEEP_SPACE_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "deep_space_sky");
    public static final ResourceLocation BLACKHOLE_YX989_K2_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "blackhole_yx989_k2_sky");
    public static final ResourceLocation BLACKHOLE_ZANGLER_11_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "blackhole_zangler_11_sky");
}
