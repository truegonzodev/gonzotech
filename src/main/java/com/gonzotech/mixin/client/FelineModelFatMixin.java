package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import net.minecraft.client.model.FelineModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.FelineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Растёт торс у котов: в {@code setupAnim} (каждый кадр) масштабируем
 * {@code body} на фактор жирности из render state. Голова/лапы/хвост остаются
 * нормальными — «жирный корпус» (автор: «их корпус становился больше»).
 * Миксин на базовую {@link FelineModel} → покрывает и домашнего кота, и оцелота.
 */
@Mixin(FelineModel.class)
public abstract class FelineModelFatMixin {

    @Shadow
    protected ModelPart body;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/FelineRenderState;)V",
        at = @At("TAIL"))
    private void gonzo$fatBody(FelineRenderState state, CallbackInfo ci) {
        float fatness = state instanceof FatnessState fatState ? fatState.gonzotech$fatness() : 1.0F;
        this.body.xScale = fatness;
        this.body.yScale = fatness;
        this.body.zScale = fatness;
    }
}
