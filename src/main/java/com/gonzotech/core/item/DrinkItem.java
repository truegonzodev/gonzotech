package com.gonzotech.core.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Питьё «зажать ПКМ» (спека автора 24.09.2026: «флаг eatable use»): анимация и
 * задержка как у еды/зелья ({@value #USE_TICKS} тиков).
 *
 * <p>После глотка остаётся тара: ведро пива → пустое ведро, бутылка водки →
 * пузырёк; кружка пива расходуется целиком. Используется для кружки пива,
 * ведра пива и бутылки водки (вкладка «Приколы»).</p>
 */
public class DrinkItem extends Item {

    /** Задержка «как у еды». */
    public static final int USE_TICKS = 32;
    /** Награда полного курса ДТПА (автор 24.09): снятие стресса и кризиса. */
    private static final int COURSE_STRESS_RELIEF = 20_000;
    private static final int COURSE_CRISIS_RELIEF = 2_000;

    /** Что остаётся после глотка; {@code null} — не остаётся ничего (кружка). */
    private final Item remainder;

    public DrinkItem(Properties properties, Item remainder) {
        super(properties);
        this.remainder = remainder;
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
        if (!(level instanceof ServerLevel serverLevel) || !(livingEntity instanceof ServerPlayer serverPlayer)) {
            return stack;
        }
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        if (serverPlayer.getAbilities().instabuild) {
            return stack;
        }
        if (remainder != null) {
            return ItemUtils.createFilledResult(stack, serverPlayer, new ItemStack(remainder));
        }
        stack.shrink(1);
        return stack;
    }
}
