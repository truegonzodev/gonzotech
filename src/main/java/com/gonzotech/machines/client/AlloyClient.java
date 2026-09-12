package com.gonzotech.machines.client;

import com.gonzotech.GonzoTechMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;

/** Client-only registrations for the dynamic custom-alloy special model. */
public final class AlloyClient {

    private AlloyClient() {
    }

    public static void onRegisterSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "custom_alloy"),
            AlloySpecialRenderer.Unbaked.MAP_CODEC
        );
    }

    public static void onAddClientReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "alloy_dynamic_textures"),
            new ResourceManagerReloadListener() {
                @Override
                public void onResourceManagerReload(ResourceManager resourceManager) {
                    AlloyDynamicTextureCache.onResourceManagerReload(resourceManager);
                }
            }
        );
    }
}
