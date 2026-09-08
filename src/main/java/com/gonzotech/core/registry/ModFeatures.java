package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.worldgen.MineralReplacementFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModFeatures {

    public static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, GonzoTechMod.MOD_ID);

    /** Пост-обработка чанка: кальцит→calcium, глина→aluminum, камень у dripstone→zinc. */
    public static final DeferredHolder<Feature<?>, MineralReplacementFeature> MINERAL_REPLACEMENT =
        FEATURES.register("mineral_replacement", MineralReplacementFeature::new);

    /** Фаза 4 — кратеры Луны (сплющенные полусферы с валом). */
    public static final DeferredHolder<Feature<?>, com.gonzotech.space.worldgen.CraterFeature> CRATER =
        FEATURES.register("crater",
            () -> new com.gonzotech.space.worldgen.CraterFeature(
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    /** Фаза 4 — финальное лёгкое сглаживание рельефа Луны (после кратеров). */
    public static final DeferredHolder<Feature<?>, com.gonzotech.space.worldgen.LunarSmoothingFeature> LUNAR_SMOOTHING =
        FEATURES.register("lunar_smoothing",
            () -> new com.gonzotech.space.worldgen.LunarSmoothingFeature(
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    /** Фаза 4 — подводные ледяные горы/сталактиты + плавающие глыбы Европы. */
    public static final DeferredHolder<Feature<?>, com.gonzotech.space.worldgen.EuropaIceFeature> EUROPA_ICE =
        FEATURES.register("europa_ice",
            () -> new com.gonzotech.space.worldgen.EuropaIceFeature(
                net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC));

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }

    private ModFeatures() {
    }
}