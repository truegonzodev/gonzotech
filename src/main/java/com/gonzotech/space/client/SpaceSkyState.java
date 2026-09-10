package com.gonzotech.space.client;

import com.gonzotech.space.SunState;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиентское состояние скайбокса и мегаструктур (сферы и кольца Дайсона).
 *
 * <p>Хранит:
 * <ul>
 *   <li>Глобальное состояние Солнца ({@link #sunState})</li>
 *   <li>Сферу Дайсона для Альфы Центавра ({@link #alphaCentauriDyson})</li>
 *   <li>Кольцо Дайсона вокруг Чёрной Дыры Yx989-k2 ({@link #yx989Dyson})</li>
 *   <li>Кольцо Дайсона вокруг Чёрной Дыры Zangler-11 ({@link #zanglerDyson})</li>
 * </ul>
 */
public final class SpaceSkyState {

    private SpaceSkyState() {
    }

    /** Устаревший глобальный флаг сферы Дайсона (для совместимости). */
    public static volatile boolean dysonSphere = false;

    /** Устаревший флаг сферы Дайсона солнца (для совместимости). */
    public static volatile boolean sunDyson = false;

    /** Текущее состояние Солнца (Оверворлд и Солнечная система). */
    public static volatile SunState sunState = SunState.DEFAULT;

    /** true = рисовать звезду Альфа Центавра со сферой Дайсона. */
    public static volatile boolean alphaCentauriDyson = false;

    /** true = рендерить кольцо Дайсона вокруг Чёрной Дыры yx989_k2. */
    public static volatile boolean yx989Dyson = false;

    /** true = рендерить кольцо Дайсона вокруг Чёрной Дыры zangler_11. */
    public static volatile boolean zanglerDyson = false;

    // Ресурсы солнца для Оверворлда
    public static final ResourceLocation TEX_OVERWORLD_SUN =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/environment/overworld/sun.png");
    public static final ResourceLocation TEX_OVERWORLD_SUN_DYSON =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/environment/overworld/sun_dyson.png");
    public static final ResourceLocation TEX_OVERWORLD_SUN_GONE =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/environment/overworld/sun_gone.png");
    public static final ResourceLocation TEX_OVERWORLD_SUN_BLACKHOLE =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/environment/overworld/sun_blackhole.png");
    public static final ResourceLocation TEX_OVERWORLD_SUN_BLACKHOLE_DYSON =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/environment/overworld/sun_blackhole_dyson.png");

    // Текстурный атлас Кольца Дайсона
    public static final ResourceLocation TEX_DYSON_RING =
        ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/environment/dyson_ring.png");

    /**
     * Получить актуальную текстуру солнца для Оверворлда в зависимости от sunState.
     */
    public static ResourceLocation getOverworldSunTexture(ResourceLocation vanillaSun) {
        return switch (sunState) {
            case DEFAULT -> TEX_OVERWORLD_SUN;
            case DYSON -> TEX_OVERWORLD_SUN_DYSON;
            case GONE -> TEX_OVERWORLD_SUN_GONE;
            case BLACKHOLE -> TEX_OVERWORLD_SUN_BLACKHOLE;
            case BLACKHOLE_DYSON -> TEX_OVERWORLD_SUN_BLACKHOLE_DYSON;
        };
    }
}
