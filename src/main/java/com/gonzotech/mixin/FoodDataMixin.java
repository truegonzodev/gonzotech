package com.gonzotech.mixin;

import net.minecraft.world.entity.LivingEntity;
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
 *
 * <p>Таргет: {@code LivingEntity;heal} — javac эмитит Methodref с declaring class
 * (объявлен в {@link LivingEntity}, Player не переопределяет), не со статическим
 * типом ресивера (проверено крашем 24.09: `Player;heal` → «Scanned 0 target(s)»).</p>
 */
@Mixin(FoodData.class)
public class FoodDataMixin {

    /** Срез natural regen: heal(1) долечивает только остаток от (1 − 20·L %). */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;heal(F)V"))
    private void gonzotech$itchCutsNaturalRegen(LivingEntity player, float amount) {
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
