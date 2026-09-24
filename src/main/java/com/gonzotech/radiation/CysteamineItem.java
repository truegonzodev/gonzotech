package com.gonzotech.radiation;

import com.gonzotech.core.registry.ModEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Цистамин — радиозащитный препарат («даёт именно щит»).
 * Снижает входящее облучение на 85% на 3 минуты (180 секунд).
 */
public class CysteamineItem extends Item {

    public static final int DURATION_SECONDS = 180;

    public CysteamineItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        serverPlayer.addEffect(new MobEffectInstance(ModEffects.CYSTEAMINE.get(),
                DURATION_SECONDS * 20, 0, false, true));

        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        serverPlayer.displayClientMessage(Component.translatable("message.gonzotech.cysteamine.applied")
                .withStyle(ChatFormatting.GOLD), false);
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResult.SUCCESS;
    }
}
