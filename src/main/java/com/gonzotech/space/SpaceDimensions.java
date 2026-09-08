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
 *
 * <p><b>Группа 1</b> — настоящие измерения с генерацией мира и скайбоксом:
 * Луна, Марс, Европа. Остальные группы (пустые орбиты, орбиты чёрной дыры)
 * добавляются следующими итерациями и регистрируются здесь же.
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

    // --- Идентификаторы скайбоксов (dimension_type -> "effects") ----------
    public static final ResourceLocation MOON_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "moon_sky");
    public static final ResourceLocation MARS_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "mars_sky");
    public static final ResourceLocation EUROPA_SKY =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "europa_sky");
}
