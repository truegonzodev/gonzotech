package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.WolfRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Растёт торс у волка: {@code body} и {@code upperBody} (грудной мех) —
 * лапы/голова/хвост нормальные (двойник {@link FelineModelFatMixin}).
 */
@Mixin(WolfModel.class)
public abstract class WolfModelFatMixin {

    @Shadow
    private ModelPart body;

    @Shadow
    private ModelPart upperBody;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/WolfRenderState;)V",
        at = @At("TAIL"))
    private void gonzo$fatBody(WolfRenderState state, CallbackInfo ci) {
        float fatness = state instanceof FatnessState fatState ? fatState.gonzotech$fatness() : 1.0F;
        this.body.xScale = fatness;
        this.body.yScale = fatness;
        this.body.zScale = fatness;
        this.upperBody.xScale = fatness;
        this.upperBody.yScale = fatness;
        this.upperBody.zScale = fatness;
    }
}
