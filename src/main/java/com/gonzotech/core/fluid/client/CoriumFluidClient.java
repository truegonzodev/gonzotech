package com.gonzotech.core.fluid.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.fluid.ModFluids;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector4f;

/**
 * Клиентская регистрация текстур, моделей и шейдерных эффектов для жидкостей мода Gonzo Tech:
 * <ul>
 *   <li>{@link ItemBlockRenderTypes#setRenderLayer} — слой TRANSLUCENT для полупрозрачных жидкостей (этанол, дистиллят, сусло, кислота, формальдегид);</li>
 *   <li>Экранный оверлей погружения — спрайтовый, на 1 %/тик,
 *       в FluidScreenOverlays (0.3.283), не ванильной текстурой;</li>
 *   <li>Цветная дымка и туман при нахождении игрока внутри жидкости через {@link IClientFluidTypeExtensions}.</li>
 * </ul>
 */
public final class CoriumFluidClient {

    // Базовые прозрачности:
    // Вода: ~80% (0xCC)
    // Формальдегид: 95% (0xF2)
    // Брага: 100% (0xFF)
    // Сусло: x1.1 от воды = 88% (0xE0)
    // Этанол, Кислота, Дистиллят: как у воды (80%, 0xCC)
    private static final int TINT_WATER_BASE = 0xCCFFFFFF;
    private static final int TINT_FORMALDEHYDE = 0xF2FFFFFF;
    private static final int TINT_MASH = 0xFFFFFFFF;
    private static final int TINT_WORT = 0xE0FFFFFF;
    private static final int TINT_CORIUM = 0xFFFFFFFF;

    /** Потолок плотности тумана: 0→93 % вместо 0→100 % (автор 24.10). */
    private static final float FOG_ALPHA_MAX = 0.93F;

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

            // Формальдегид имеет 95% непрозрачности (5% полупрозрачности) -> TRANSLUCENT
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FORMALDEHYDE.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_FORMALDEHYDE.get(), RenderType.translucent());
        });
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        // 1. Расплавленный кориум (100% непрозрачность, густая плотная раскалённая среда).
        // 0.3.283 (автор): туман +30 % гуще (end 1.5→1.15), начало ещё ближе
        // (start 0.0→-2.0 — муть накрывает с самой поверхности, приём как у ванильной
        // лавы), цвет — 50 % хода к #911410 (0.25,0.22,0.20 → 0.41,0.15,0.13).
        registerFluid(event, ModFluids.MOLTEN_CORIUM_TYPE.get(), "molten_corium", TINT_CORIUM,
                0.41F, 0.15F, 0.13F, -2.0F, 1.15F);

        // 2. Этанол / Ректификат (прозрачность как у воды 80%, бирюзовая дымка #8affe9, в 3-4 раза плотнее)
        registerFluid(event, ModFluids.ETHANOL_TYPE.get(), "ethanol", TINT_WATER_BASE,
                0.54F, 1.0F, 0.91F, -2.0F, 6.0F);

        // 3. Формальдегид (непрозрачность 95%, ядовито-сиреневая плотная дымка #8374a6)
        registerFluid(event, ModFluids.FORMALDEHYDE_TYPE.get(), "formaldehyde", TINT_FORMALDEHYDE,
                0.51F, 0.45F, 0.65F, 0.0F, 2.0F);

        // 4. Серная кислота (прозрачность как у воды 80%, салатово-жёлтая кислотная дымка #b4e58e)
        // 0.3.283 (автор): радиус видимости +50 % — градиент 0→93 % растянут с
        // ~3 блоков на 6 («за 5-7 блоков»).
        registerFluid(event, ModFluids.SULFURIC_ACID_TYPE.get(), "sulfuric_acid", TINT_WATER_BASE,
                0.71F, 0.90F, 0.56F, -1.0F, 6.0F);

        // 5. Дистиллят (прозрачность как у воды 80%, синяя чистая дымка #8bd3fc)
        registerFluid(event, ModFluids.DISTILLATE_TYPE.get(), "distillate", TINT_WATER_BASE,
                0.55F, 0.83F, 0.99F, -2.0F, 6.0F);

        // 6. Брага (100% непрозрачность, тёмно-коричневая плотная бродящая жижа #b84a28)
        registerFluid(event, ModFluids.MASH_TYPE.get(), "mash", TINT_MASH,
                0.72F, 0.29F, 0.16F, 0.0F, 1.5F);

        // 7. Сусло (непрозрачность x1.1 от воды = 88%, янтарно-золотая дымка #ffd582)
        registerFluid(event, ModFluids.WORT_TYPE.get(), "wort", TINT_WORT,
                1.0F, 0.84F, 0.51F, -1.0F, 5.0F);
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

            // 0.3.283 (автор): собственный спрайтовый оверлей каждой жидкости
            // (FluidScreenOverlays, 0→100 %, ramp 1 %/тик) вытеснил чужой
            // ванильный оверлей погружения — двойной пелены больше нет.
            // Хук расширения оверлея здесь не переопределён: дефолт == null.

            @Override
            public Vector4f modifyFogColor(Camera camera, float partialTick, ClientLevel level,
                                           int renderDistance, float darkenWorldAmount, Vector4f fluidFogColor) {
                return new Vector4f(fogR, fogG, fogB, 1.0F);
            }

            @Override
            public FogParameters modifyFogRender(Camera camera, FogRenderer.FogMode mode,
                                                 float renderDistance, float partialTick, FogParameters fogParameters) {
                // 0.3.280 (автор): жидкостной туман живёт ТОЛЬКО в земном слое
                // (FOG_TERRAIN). Наш прежний безгейтовый возврат подменял геометрию
                // и неба (FOG_SKY): сфера far=1.5..6, наложенная на скай-слой, и
                // давала «рентген» (в просветах видно небо; сторонние туман-киллеры
                // сбивали весь pattern, путая наш underwater-fog с terrain-fog).
                // Цвет (modifyFogColor) остаётся на оба слоя — так же ведёт себя
                // ванильная вода. Плотность — 0→93 % (автор: не 100 %), радиусы
                // (near/far) не трогаем.
                if (mode != FogRenderer.FogMode.FOG_TERRAIN) {
                    return fogParameters;
                }
                return new FogParameters(fogStart, fogEnd, FogShape.SPHERE, fogR, fogG, fogB, FOG_ALPHA_MAX);
            }
        }, type);
    }
}
