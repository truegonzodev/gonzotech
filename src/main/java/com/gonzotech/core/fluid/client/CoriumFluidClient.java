package com.gonzotech.core.fluid.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.fluid.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Клиентская регистрация текстур и оттенков для жидкостей мода
 * (кориум, этанол/ректификат, формальдегид).
 * Предотвращает NPE в FluidSpriteCache.getFluidSprites при тесселяции жидкости в мире.
 */
public final class CoriumFluidClient {

    private static final ResourceLocation WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow");

    private CoriumFluidClient() {
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // 1. Расплавленный кориум (серый радиоактивный расплав)
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

        // 2. Этанол / Ректификат (прозрачный бирюзово-голубой спирт #8affe9)
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WATER_FLOW;
            }

            @Override
            public int getTintColor() {
                return 0xFF8AFFE9;
            }
        }, ModFluids.ETHANOL_TYPE.get());

        // 3. Формальдегид (ядовито-сиреневая едкая жидкость #8374a6)
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WATER_FLOW;
            }

            @Override
            public int getTintColor() {
                return 0xFF8374A6;
            }
        }, ModFluids.FORMALDEHYDE_TYPE.get());
    }
}
