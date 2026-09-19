package com.gonzotech.mixin.client;

import com.gonzotech.swag.FatnessState;
import com.gonzotech.swag.client.FatPartScaler;
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
 * {@code body} по ширине и высоте на фактор жирности из render state.
 * Длина (самая длинная ось) не трогается — см. {@link FatPartScaler}.
 * Миксин на базовую {@link FelineModel} → покрывает и домашнего кота, и оцелота
 * (у оцелота fatness всегда 1.0 → ранний выход без эффекта).
 */
@Mixin(FelineModel.class)
public abstract class FelineModelFatMixin {

    @Shadow
    protected ModelPart body;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/FelineRenderState;)V",
        at = @At("TAIL"))
    private void gonzo$fatBody(FelineRenderState state, CallbackInfo ci) {
        float fatness = state instanceof FatnessState fatState ? fatState.gonzotech$fatness() : 1.0F;
        // Куб тела кота: addBox(-2, 3, -8; 4, 16, 6) → центр куба (0, 11, -5).
        FatPartScaler.fatten(this.body, fatness, 0.0F, -5.0F);
    }
}
