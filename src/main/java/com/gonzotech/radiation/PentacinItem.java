package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.psyche.PsycheNetwork;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Пентацин — экстренная декорпорация (спека автора 24.09.2026).
 *
 * <p>Применение — «как еда»: зажать ПКМ ({@value #USE_TICKS} тиков), без сообщений
 * и лора (автор 24.09). Приём (в порядке спеки):</p>
 * <ol>
 *   <li>«Очищение» 2 уровня на 10 секунд ({@link ModEffects#RAD_CLEANSE}, амплитуда 1);</li>
 *   <li>моментально <b>−20 % от ТЕКУЩЕЙ</b> дозы (100 % → 80 %, 50 % → 40 %);</li>
 *   <li>«Исушение» (ванильный Wither) 2 уровня на 5 секунд;</li>
 *   <li><b>+1 Tx</b> химии (= 1e9 nTx);</li>
 *   <li><b>+2 % шкалы стресса</b> и <b>+2 % зависимости</b> (= 20 000 очков, ровно);</li>
 *   <li>скрытый таймер 120 секунд: повтор в окне — <b>33 %</b> «Сердечный приступ»
 *       на 40 секунд (единственное оставляемое сообщение в чате — «Сердце
 *       прихватило…», автор 24.09) и <b>5 %</b> «Некроз».</li>
 * </ol>
 */
public class PentacinItem extends Item {

    /** Задержка «как у еды». */
    public static final int USE_TICKS = 32;

    /** Моментальный срез дозы: −20 % текущей. */
    private static final double DOSE_REMAINING = 0.8;

    /** «Очищение» 2 уровня (амплитуда 1) на 10 секунд. */
    private static final int CLEANSE_SECONDS = 10;
    private static final int CLEANSE_AMPLIFIER = 1;

    /** «Исушение» (Wither) 2 уровня (амплитуда 1) на 5 секунд. */
    private static final int WITHER_SECONDS = 5;
    private static final int WITHER_AMPLIFIER = 1;

    /** Химия приёма: +1 Tx = 1e9 nTx. */
    private static final double CHEM_NTX = 1.0e9;

    /** Стресс и зависимость: +2 % шкалы = 20 000 очков (1 000 000 = 100 %). */
    private static final int SCALE_POINTS = 20_000;

    /** Скрытое окно передозировки — 120 секунд. */
    public static final int OVERDOSE_WINDOW_SECONDS = 120;
    /** Шанс «Сердечного приступа» при повторе в окне. */
    public static final double HEART_ATTACK_CHANCE = 0.33;
    /** Шанс «Некроза» при повторе в окне. */
    public static final double NECROSIS_CHANCE = 0.05;

    public PentacinItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof ServerPlayer serverPlayer)) {
            return stack;
        }

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);

        // 1. «Очищение» II на 10 секунд.
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.RAD_CLEANSE,
                CLEANSE_SECONDS * 20, CLEANSE_AMPLIFIER, false, true));

        // 2. Моментально −20 % от ТЕКУЩЕЙ дозы.
        int currentDose = psyche.getRadiation();
        if (currentDose > 0) {
            psyche.setRadiation((int) Math.round(currentDose * DOSE_REMAINING));
        }

        // 3. «Исушение» II (Wither) на 5 секунд.
        serverPlayer.addEffect(new MobEffectInstance(MobEffects.WITHER,
                WITHER_SECONDS * 20, WITHER_AMPLIFIER));

        // 4. Химия: +1 Tx.
        PsycheChemical.addDoseToxicity(serverPlayer, CHEM_NTX);

        // 5. Стресс и зависимость: ровно +2 % шкалы (без бонуса зависимости/зуда).
        psyche.setStress(psyche.getStress() + SCALE_POINTS);
        psyche.addAddiction(SCALE_POINTS);

        // 6. Скрытый таймер передозировки: повтор в окне 120 с — 33 % приступ, 5 % некроз.
        long now = serverLevel.getGameTime();
        if (now < psyche.getPentacinOverdoseUntil()) {
            if (serverLevel.getRandom().nextDouble() < HEART_ATTACK_CHANCE) {
                serverPlayer.addEffect(new MobEffectInstance(ModEffects.HEART_ATTACK,
                        40 * 20, 0, false, true));
                // Единственное сообщение, которое оставили (автор 24.09).
                serverPlayer.displayClientMessage(Component.translatable("message.gonzotech.heart_attack.warning")
                        .withStyle(ChatFormatting.RED), false);
            }
            if (serverLevel.getRandom().nextDouble() < NECROSIS_CHANCE) {
                Necrosis.grant(serverPlayer, 1);
            }
        }
        psyche.setPentacinOverdoseUntil(now + OVERDOSE_WINDOW_SECONDS * 20L);

        serverPlayer.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(serverPlayer);

        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }
}
