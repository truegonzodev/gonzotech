package com.gonzotech.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.effect.MobEffectInstance;
import com.gonzotech.core.registry.ModEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Зуд (автор 24.09.2026): срезаем natural regen — натуральную регенерацию игрока —
 * на {@code 20 * уровень} % (I → −20 %, II → −40 %, III → −60 %). Работает только
 * на «ванильной» регенерации (FoodData), зелья/маяки/яблоки лечат как обычно.
 */
@Mixin(FoodData.class)
public class FoodDataMixin {

    /** Срез natural regen: heal(1) долечивает только остаток от (1 − 20·L %). */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;heal(F)V"))
    private void gonzotech$itchCutsNaturalRegen(Player player, float amount) {
        int level = 0;
        MobEffectInstance itch = player.getEffect(ModEffects.ITCH);
        if (itch != null) {
            level = itch.getAmplifier() + 1;
        }
        double keep = Math.max(0.0, 1.0 - 0.2 * level);
        if (keep > 0.0) {
            player.heal((float) (amount * keep));
        }
    }
}
