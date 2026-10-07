package com.gonzotech.core.item;

import com.gonzotech.core.psyche.PsycheStress;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/** Drinkable sedative: reduces stress and prevents tremor for five minutes. */
public final class SedativeItem extends Item {

    public static final int USE_TICKS = 32;
    public static final int RELAXATION_DURATION_TICKS = 6_000;
    public static final int FADE_TICKS = 100;
    public static final int STRESS_RELIEF = 1_000;
    public static final int ADDICTION_INCREASE = 2_000;

    public SedativeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof ServerPlayer player)) {
            return stack;
        }

        ConsumptionAccounting.record(player, stack);
        player.removeEffect(ModEffects.TREMOR);
        PsycheStress.relieve(player, STRESS_RELIEF);
        PsycheStress.addict(player, ADDICTION_INCREASE);
        player.addEffect(new MobEffectInstance(ModEffects.RELAXATION,
                RELAXATION_DURATION_TICKS, 0, false, true));

        serverLevel.playSound(null, player.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }
}
