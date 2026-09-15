package com.gonzotech.core.fluid.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.fluid.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** Client-only gray still/flow texture mapping for corium. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CoriumFluidClient {

    private CoriumFluidClient() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "block/fluid/corium_still");
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "block/fluid/corium_flow");
            }

            @Override
            public int getTintColor() {
                return 0xFF9A9A9A;
            }
        }, ModFluids.CORIUM_TYPE.get());
    }
}
