package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import com.gonzotech.swag.client.FatPartScaler;
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
 * по ширине и высоте, без длины (двойник {@link FelineModelFatMixin}).
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
        // body: addBox(-3, -2, -3; 6, 9, 6) → центр (0, 2.5, 0) — центр на пивоте, сдвига нет.
        FatPartScaler.fatten(this.body, fatness, 0.0F, 0.0F);
        // upper_body: addBox(-3, -3, -3; 8, 6, 7) → центр (1, 0, 0.5) — компенсация сдвига.
        FatPartScaler.fatten(this.upperBody, fatness, 1.0F, 0.5F);
    }
}
