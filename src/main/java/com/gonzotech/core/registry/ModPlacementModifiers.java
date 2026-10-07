package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.worldgen.ExpectedCountPlacement;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Custom data-driven placement modifiers used by world generation. */
public final class ModPlacementModifiers {

    private static final DeferredRegister<PlacementModifierType<?>> TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, GonzoTechMod.MOD_ID);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ExpectedCountPlacement>>
            EXPECTED_COUNT = TYPES.register("expected_count", () -> () -> ExpectedCountPlacement.CODEC);

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    private ModPlacementModifiers() {
    }
}
