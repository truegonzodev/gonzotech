package com.gonzotech.machines.client;

import com.gonzotech.GonzoTechMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Client-only registration for the custom-alloy item-model tint source. */
public final class AlloyClient {

    private AlloyClient() {
    }

    public static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "alloy_tint"), AlloyTintSource.MAP_CODEC);
    }
}
