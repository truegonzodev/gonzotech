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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * ДТПА (диэтилентриаминпентауксусная кислота) — мощный радиопротектор глубокого действия
 * («мощное лечение и защита, но применяется долго, почти без дебаффов»).
 * Полностью снимает Некроз (Necrosis.cure), запускает мощное 60-секундное выведение
 * радионуклидов (RadCleanse III) и даёт 60-секундный защитный щит от облучения.
 */
public class DtpaItem extends Item {

    public DtpaItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        // 1. Полное излечение некроза
        Necrosis.cure(serverPlayer);

        // 2. Мощный курс выведения радиации (уровень 2 = 60% дозы за курс)
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.RAD_CLEANSE.get(),
                60 * 20, 2, false, true));

        // 3. Радиозащитный щит на 60 секунд
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.CYSTEAMINE.get(),
                60 * 20, 0, false, true));

        // Мягкий побочный эффект: лёгкий голод I на 5 секунд
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0));

        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        serverPlayer.displayClientMessage(Component.translatable("message.gonzotech.dtpa.applied")
                .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResult.SUCCESS;
    }
}
