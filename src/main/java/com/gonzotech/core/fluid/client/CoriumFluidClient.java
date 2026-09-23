package com.gonzotech.core.fluid.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.fluid.ModFluids;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;

/**
 * Клиентская регистрация текстур, моделей и шейдерных эффектов для жидкостей мода Gonzo Tech:
 * <ul>
 *   <li>{@link ItemBlockRenderTypes#setRenderLayer} — слой TRANSLUCENT для полупрозрачных жидкостей (этанол, дистиллят, сусло, кислота);</li>
 *   <li>Подводный шейдер/оверлей камеры (underwater.png);</li>
 *   <li>Цветная дымка и туман при нахождении игрока внутри жидкости через {@link IClientFluidTypeExtensions}.</li>
 * </ul>
 */
public final class CoriumFluidClient {

    private static final ResourceLocation UNDERWATER_OVERLAY =
            ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

    private CoriumFluidClient() {
    }

    /**
     * Регистрация слоёв полупрозрачности в FMLClientSetupEvent.
     */
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Полупрозрачные жидкости (TRANSLUCENT)
            ItemBlockRenderTypes.setRenderLayer(ModFluids.ETHANOL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_ETHANOL.get(), RenderType.translucent());

            ItemBlockRenderTypes.setRenderLayer(ModFluids.SULFURIC_ACID.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_SULFURIC_ACID.get(), RenderType.translucent());

            ItemBlockRenderTypes.setRenderLayer(ModFluids.DISTILLATE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_DISTILLATE.get(), RenderType.translucent());

            ItemBlockRenderTypes.setRenderLayer(ModFluids.WORT.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_WORT.get(), RenderType.translucent());
        });
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // 1. Расплавленный кориум
        registerFluid(event, ModFluids.MOLTEN_CORIUM_TYPE.get(), "molten_corium", 0xFFFFFFFF,
                0.25F, 0.22F, 0.20F, -2.0F, 6.0F);

        // 2. Этанол / Ректификат (бирюзовая дымка #8affe9)
        registerFluid(event, ModFluids.ETHANOL_TYPE.get(), "ethanol", 0xFFFFFFFF,
                0.54F, 1.0F, 0.91F, -8.0F, 24.0F);

        // 3. Формальдегид (ядовито-сиреневая дымка #8374a6)
        registerFluid(event, ModFluids.FORMALDEHYDE_TYPE.get(), "formaldehyde", 0xFFFFFFFF,
                0.51F, 0.45F, 0.65F, -2.0F, 8.0F);

        // 4. Серная кислота (салатово-жёлтая кислотная дымка #b4e58e)
        registerFluid(event, ModFluids.SULFURIC_ACID_TYPE.get(), "sulfuric_acid", 0xFFFFFFFF,
                0.71F, 0.90F, 0.56F, -4.0F, 12.0F);

        // 5. Дистиллят (синяя чистая дымка #8bd3fc)
        registerFluid(event, ModFluids.DISTILLATE_TYPE.get(), "distillate", 0xFFFFFFFF,
                0.55F, 0.83F, 0.99F, -8.0F, 24.0F);

        // 6. Брага (тёмно-коричневая плотная бродящая жижа #b84a28)
        registerFluid(event, ModFluids.MASH_TYPE.get(), "mash", 0xFFFFFFFF,
                0.72F, 0.29F, 0.16F, -2.0F, 6.0F);

        // 7. Сусло (янтарно-золотая тёплая дымка #ffd582)
        registerFluid(event, ModFluids.WORT_TYPE.get(), "wort", 0xFFFFFFFF,
                1.0F, 0.84F, 0.51F, -6.0F, 18.0F);
    }

    private static void registerFluid(RegisterClientExtensionsEvent event,
                                      net.neoforged.neoforge.fluids.FluidType type,
                                      String textureName,
                                      int tintColor,
                                      float fogR, float fogG, float fogB,
                                      float fogStart, float fogEnd) {
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

            @Override
            public @Nullable ResourceLocation getRenderOverlayTexture(Minecraft mc) {
                return UNDERWATER_OVERLAY;
            }

            @Override
            public Vector4f modifyFogColor(Camera camera, float partialTick, ClientLevel level,
                                           int renderDistance, float darkenWorldAmount, Vector4f fluidFogColor) {
                return new Vector4f(fogR, fogG, fogB, 1.0F);
            }

            @Override
            public FogParameters modifyFogRender(Camera camera, FogRenderer.FogMode mode,
                                                 float renderDistance, float partialTick, FogParameters fogParameters) {
                return new FogParameters(fogStart, fogEnd, FogShape.SPHERE, fogR, fogG, fogB, 1.0F);
            }
        }, type);
    }
}
