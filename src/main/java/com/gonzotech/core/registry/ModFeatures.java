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

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }

    private ModFeatures() {
    }
}