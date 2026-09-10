package com.gonzotech.mixin.client;

import com.gonzotech.space.client.SpaceSkyState;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Подмена текстуры солнца в Оверворлде по текущему состоянию {@link SpaceSkyState#sunState}.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSunMixin {

    @ModifyArg(
        method = "renderSky",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"
        ),
        index = 1
    )
    private ResourceLocation gonzotech$modifyOverworldSunTexture(ResourceLocation texture) {
        if (texture != null && (texture.getPath().equals("textures/environment/sun.png") || texture.getPath().endsWith("/sun.png"))) {
            return SpaceSkyState.getOverworldSunTexture(texture);
        }
        return texture;
    }
}
