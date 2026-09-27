package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.entity.AltVillagerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Реестр сущностей мода. Пока один моб — «альт-житель».
 *
 * <p>Габариты, eye height, tracking range и категория MISC — точь-в-точь как у
 * ванильного вилладжера ({@code EntityType.VILLAGER}): 0.6×1.95, глаза 1.62.</p>
 */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
        DeferredRegister.create(Registries.ENTITY_TYPE, GonzoTechMod.MOD_ID);

    /** Копия вилладжера без АИ; спавн только яйцом призыва. */
    public static final DeferredHolder<EntityType<?>, EntityType<AltVillagerEntity>> ALT =
        ENTITIES.register("alt", () -> EntityType.Builder.of(AltVillagerEntity::new, MobCategory.MISC)
            .sized(0.6F, 1.95F)
            .eyeHeight(1.62F)
            .clientTrackingRange(10)
            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "alt"))));

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
    }

    private ModEntities() {
    }
}
