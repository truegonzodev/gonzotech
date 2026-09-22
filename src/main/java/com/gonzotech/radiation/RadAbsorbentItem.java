package com.gonzotech.radiation;

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
 * Антирадиновый абсорбент (спека автора 22.09.2026) — расходник-<b>поглотитель</b>,
 * а не лекарство и не щит: «главная философия — абсорбент».
 *
 * <p>ПКМ накладывает эффект {@link ModEffects#RAD_CLEANSE «Очищение»} на
 * {@link RadDose#CLEANSE_SECONDS} секунд: эффект <b>плавно</b> выводит
 * {@link RadDose#CLEANSE_PER_LEVEL_PERMILLE} % текущей дозы. По окончании —
 * голод I на 10 секунд и синие крапинки лазурита (см. {@link RadCleanse} и
 * {@link RadiationSystem}).</p>
 *
 * <p>Крафт доступен всегда, но рецепт показывается после Открытия 2
 * ({@code RecipeUnlocks} тир 2 + {@code TierTwoCrafting}); сам абсорбент —
 * по решению автора во вкладке «Снаряжение».</p>
 */
public class RadAbsorbentItem extends Item {

    public RadAbsorbentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.RAD_CLEANSE,
                RadDose.CLEANSE_SECONDS * 20, 0, false, true));
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        // Психика (автор 22.09): проюз абсорбента добавляет +100 очков стресса.
        com.gonzotech.core.psyche.PsycheStress.gain(serverPlayer,
                com.gonzotech.core.psyche.PsycheStress.ABSORBENT_STRESS_BURST);
        serverPlayer.displayClientMessage(Component.translatable("message.gonzotech.absorbent.applied")
                .withStyle(ChatFormatting.AQUA), true);
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResult.SUCCESS;
    }
}
