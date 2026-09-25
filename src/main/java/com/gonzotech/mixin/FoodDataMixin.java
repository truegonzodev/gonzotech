package com.gonzotech.mixin;

import com.gonzotech.core.registry.ModEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Зуд (автор 24.09.2026): срезаем natural regen — натуральную регенерацию игрока —
 * на {@code 20 * уровень} % (I → −20 %, II → −40 %, III → −60 %). Работает только
 * на «ванильной» регенерации (FoodData), зелья/маяки/яблоки лечат как обычно.
 *
 * <p>Подход HEAD/RETURN: помним HP до тика {@code FoodData.tick} и отнимаем долю
 * любого прироста внутри него. Надёжнее {@code @Redirect} на лечебный вызов: байткод
 * {@code FoodData.tick} менялся между версиями (сигнатура {@code tick(ServerPlayer)},
 * вызов лечения уже дважды не матчился по owner'у — краш-опыт 24.09), а тут достаточно
 * найти сам метод. Урон от голода не трогаем (прироста нет), зелья идут вне тика.</p>
 */
@Mixin(FoodData.class)
public class FoodDataMixin {

    @Unique private float gonzotech$healthBeforeTick;
    @Unique private boolean gonzotech$itchActive;

    /** Запомнить HP до тика голода (только под зудом). */
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"))
    private void gonzotech$rememberHealth(ServerPlayer player, CallbackInfo ci) {
        this.gonzotech$itchActive = player.getEffect(ModEffects.ITCH) != null;
        this.gonzotech$healthBeforeTick = this.gonzotech$itchActive ? player.getHealth() : 0.0F;
    }

    /** Срез natural regen: доля прироста HP внутри тика не достаётся игроку. */
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"))
    private void gonzotech$cutNaturalRegen(ServerPlayer player, CallbackInfo ci) {
        if (!this.gonzotech$itchActive) {
            return;
        }
        this.gonzotech$itchActive = false;
        MobEffectInstance itch = player.getEffect(ModEffects.ITCH);
        if (itch == null) {
            return;
        }
        float gained = player.getHealth() - this.gonzotech$healthBeforeTick;
        if (gained > 0.0F) {
            int level = itch.getAmplifier() + 1;
            double keep = Math.max(0.0, 1.0 - 0.2 * level);
            player.setHealth(this.gonzotech$healthBeforeTick + (float) (gained * keep));
        }
    }
}
