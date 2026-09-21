package com.gonzotech.core.fluid.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.fluid.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Client-only gray still/flow texture mapping and tint for molten corium. */
public final class CoriumFluidClient {

    private CoriumFluidClient() {
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "block/fluid/molten_corium_still");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "block/fluid/molten_corium_flow");
            }

            @Override
            public int getTintColor() {
                return 0xFF9A9A9A;
            }
        }, ModFluids.MOLTEN_CORIUM_TYPE.get());
    }
}
