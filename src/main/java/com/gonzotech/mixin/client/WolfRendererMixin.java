package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import com.gonzotech.swag.PetFatness;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Экстракция «жирности» волка в render state (двойник {@link CatRendererMixin}).
 */
@Mixin(WolfRenderer.class)
public abstract class WolfRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/Wolf;Lnet/minecraft/client/renderer/entity/state/WolfRenderState;F)V",
        at = @At("TAIL"))
    private void gonzo$extractFatness(Wolf wolf, WolfRenderState state, float partialTick, CallbackInfo ci) {
        ((FatnessState) state).gonzotech$setFatness(((PetFatness) wolf).gonzotech$fatness());
    }
}
