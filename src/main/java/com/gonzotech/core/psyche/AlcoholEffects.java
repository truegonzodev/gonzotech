package com.gonzotech.core.psyche;

import com.gonzotech.chalkboard.ResonanceClue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** Called once, server-side, only after a complete drink action. */
public final class AlcoholEffects {
    private AlcoholEffects() {}

    public static void consume(ServerPlayer player, AlcoholDose dose) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setAddiction(psyche.getAddiction() + dose.addiction);
        psyche.setStress(psyche.getStress() + dose.stress);
        psyche.setCrisis(psyche.getCrisis() + dose.crisis);
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);

        var food = player.getFoodData();
        int newFood = dose.foodAfter(food.getFoodLevel());
        float newSaturation = dose.saturationAfter(food.getSaturationLevel(), newFood);
        food.setFoodLevel(newFood);
        food.setSaturation(newSaturation);
        if (dose.slownessTicks > 0) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dose.slownessTicks, 0));
        if (roll(player, dose.poisonChance)) player.addEffect(new MobEffectInstance(MobEffects.POISON, dose.poisonTicks, 0));
        if (dose.nauseaTicks > 0) player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, dose.nauseaTicks, 1));

        // Independent rolls, one per consumed serving, not one per nearby board.
        if (roll(player, dose.clueChance)) ResonanceClue.giveNatural(player);
        if (roll(player, dose.faintChance)) AlcoholFainting.start(player);
    }

    private static boolean roll(ServerPlayer player, int chance) {
        return chance > 0 && (chance >= 100 || AlcoholDose.succeeds(player.getRandom().nextInt(100), chance));
    }
}
