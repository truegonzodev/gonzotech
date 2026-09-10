package com.gonzotech.mixin.client;

import com.gonzotech.space.SpaceDimensions;
import com.gonzotech.space.SunState;
import com.gonzotech.space.client.SpaceSkyEffects;
import com.gonzotech.space.client.SpaceSkyState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * РАЗВЯЗКА ОСВЕЩЕНИЯ ДНЯ/НОЧИ И ВЕЧНАЯ ТЬМА ПРИ ПОГАСШЕМ СОЛНЦЕ (GONE).
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyDarkenMixin {

    @Inject(method = "getSkyDarken(F)F", at = @At("HEAD"), cancellable = true)
    private void gonzotech$decoupleSkyLight(float partialTick,
                                            CallbackInfoReturnable<Float> cir) {
        ClientLevel self = (ClientLevel) (Object) this;

        // Если солнце взорвалось/погасло (GONE) — вечная ночь (0.2F) на Земле, Луне, Марсе, Европе и орбите Солнца
        if (SpaceSkyState.sunState == SunState.GONE) {
            ResourceKey<Level> dim = self.dimension();
            if (dim == Level.OVERWORLD || dim == SpaceDimensions.SOLAR_ORBIT
                    || dim == SpaceDimensions.MOON || dim == SpaceDimensions.MARS || dim == SpaceDimensions.EUROPA) {
                cir.setReturnValue(0.2F);
                return;
            }
        }

        DimensionSpecialEffects effects = self.effects();
        if (effects instanceof SpaceSkyEffects space && space.overridesSkyLight()) {
            cir.setReturnValue(space.computeSkyDarken(self, partialTick));
        }
    }
}
