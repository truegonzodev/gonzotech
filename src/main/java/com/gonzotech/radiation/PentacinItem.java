package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Пентацин — экстренный хелатирующий препарат («быстрое лечение средней радиации с дебаффами»).
 * Мгновенно сбрасывает дозу радиации на 50% (или до 350 permille), но вызывает тошноту, слабость и голод.
 */
public class PentacinItem extends Item {

    public PentacinItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);
        int currentDose = psyche.getRadiation();
        if (currentDose > 0) {
            int toRemove = Math.max(150, currentDose / 2);
            psyche.setRadiation(Math.max(0, currentDose - toRemove));
            PsycheNetwork.sendToPlayer(serverPlayer);
        }

        // Дебаффы препарата: Тошнота (10 сек), Слабость (15 сек), Голод II (15 сек)
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 0));
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 1));

        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        serverPlayer.displayClientMessage(Component.translatable("message.gonzotech.pentacin.applied")
                .withStyle(ChatFormatting.RED), false);
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResult.SUCCESS;
    }
}
