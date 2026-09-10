package com.gonzotech.mixin.client;

import com.gonzotech.space.client.SpaceSkyState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Подмена текстуры солнца в Оверворлде по текущему состоянию {@link SpaceSkyState#sunState}.
 *
 * <p>Перехватывает чтение статического поля {@link LevelRenderer#SUN_LOCATION}
 * во всех методах рендера неба ({@code renderSky}, {@code renderSun} и др.) и возвращает
 * актуальную текстуру в зависимости от глобального состояния Солнца.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSunMixin {

    private static final ResourceLocation VANILLA_SUN =
        ResourceLocation.withDefaultNamespace("textures/environment/sun.png");

    @Redirect(
        method = "*",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;SUN_LOCATION:Lnet/minecraft/resources/ResourceLocation;",
            opcode = Opcodes.GETSTATIC
        ),
        require = 0
    )
    private static ResourceLocation gonzotech$redirectSunLocation() {
        return SpaceSkyState.getOverworldSunTexture(VANILLA_SUN);
    }
}
