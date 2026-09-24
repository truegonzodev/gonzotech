package com.gonzotech.radiation;

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
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * Антирадиновый абсорбент (спека автора 22.09.2026) — расходник-<b>поглотитель</b>,
 * а не лекарство и не щит: «главная философия — абсорбент».
 *
 * <p>Применение — «как еда»: зажать ПКМ ({@value #USE_TICKS} тиков; автор 24.09,
 * без сообщений и лора). Накладывает эффект {@link ModEffects#RAD_CLEANSE «Очищение»} на
 * {@link RadDose#CLEANSE_SECONDS} секунд: эффект <b>плавно</b> выводит
 * {@link RadDose#CLEANSE_PER_LEVEL_PERMILLE} % текущей дозы. По окончании —
 * голод I на 10 секунд и синие крапинки лазурита (см. {@link RadCleanse}).</p>
 *
 * <p>Крафт доступен всегда, но рецепт показывается после Открытия 2
 * ({@code RecipeUnlocks} тир 2 + {@code TierTwoCrafting}); сам абсорбент —
 * по решению автора во вкладке «Снаряжение».</p>
 */
public class RadAbsorbentItem extends Item {

    /** Задержка «как у еды». */
    public static final int USE_TICKS = 32;

    public RadAbsorbentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
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
        serverPlayer.addEffect(new MobEffectInstance(ModEffects.RAD_CLEANSE,
                RadDose.CLEANSE_SECONDS * 20, 0, false, true));

        // Химия (автор 22.09.2026): применение антирадина добавляет +1 % к заражению.
        com.gonzotech.core.psyche.PsycheChemical.addPermille(serverPlayer,
                com.gonzotech.core.psyche.PsycheChemical.ABSORBENT_ADD_PERMILLE);

        // Психика (автор 22.09): проюз абсорбента добавляет +100 очков стресса.
        com.gonzotech.core.psyche.PsycheStress.gain(serverPlayer,
                com.gonzotech.core.psyche.PsycheStress.ABSORBENT_STRESS_BURST);

        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return stack;
    }
}
