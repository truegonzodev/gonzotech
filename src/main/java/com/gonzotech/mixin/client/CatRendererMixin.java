package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import com.gonzotech.swag.PetFatness;
import net.minecraft.client.renderer.entity.CatRenderer;
import net.minecraft.client.renderer.entity.state.CatRenderState;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Экстракция «жирности» кота в render state (каждый кадр, клиент).
 * Ванильный peace: мы только дописываем своё поле после super-вызова.
 */
@Mixin(CatRenderer.class)
public abstract class CatRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/animal/Cat;Lnet/minecraft/client/renderer/entity/state/CatRenderState;F)V",
        at = @At("TAIL"))
    private void gonzo$extractFatness(Cat cat, CatRenderState state, float partialTick, CallbackInfo ci) {
        ((FatnessState) state).gonzotech$setFatness(((PetFatness) cat).gonzotech$fatness());
    }
}
