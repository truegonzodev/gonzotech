package com.gonzotech.core.fluid.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.fluid.ModFluids;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Клиентская регистрация текстур и оттенков для жидкостей мода Gonzo Tech.
 * Текстуры лежат в assets/gonzotech/textures/block/fluid/ в формате 16x320 (still) и 32x512 (flow).
 * 32-bit ARGB в PNG напрямую определяет прозрачность и цвет каждого пикселя в мире.
 */
public final class CoriumFluidClient {

    private CoriumFluidClient() {
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // 1. Расплавленный кориум
        registerFluid(event, ModFluids.MOLTEN_CORIUM_TYPE.get(), "molten_corium", 0xFFFFFFFF);

        // 2. Этанол / Ректификат (water_type, semitransparent)
        registerFluid(event, ModFluids.ETHANOL_TYPE.get(), "ethanol", 0xFFFFFFFF);

        // 3. Формальдегид (lava_type, nottransparent)
        registerFluid(event, ModFluids.FORMALDEHYDE_TYPE.get(), "formaldehyde", 0xFFFFFFFF);

        // 4. Серная кислота (water_type, semitransparent)
        registerFluid(event, ModFluids.SULFURIC_ACID_TYPE.get(), "sulfuric_acid", 0xFFFFFFFF);

        // 5. Дистиллят (water_type, semitransparent)
        registerFluid(event, ModFluids.DISTILLATE_TYPE.get(), "distillate", 0xFFFFFFFF);

        // 6. Брага (waterlava_type, nottransparent)
        registerFluid(event, ModFluids.MASH_TYPE.get(), "mash", 0xFFFFFFFF);

        // 7. Сусло (water_type, semitransparent)
        registerFluid(event, ModFluids.WORT_TYPE.get(), "wort", 0xFFFFFFFF);
    }

    private static void registerFluid(RegisterClientExtensionsEvent event,
                                      net.neoforged.neoforge.fluids.FluidType type,
                                      String textureName,
                                      int tintColor) {
        ResourceLocation still = ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "block/fluid/" + textureName + "_still");
        ResourceLocation flow = ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "block/fluid/" + textureName + "_flow");

        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return still;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return flow;
            }

            @Override
            public int getTintColor() {
                return tintColor;
            }
        }, type);
    }
}
