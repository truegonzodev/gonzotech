package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheChemical;
import com.gonzotech.core.psyche.PsycheNetwork;
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
 * Цистамин — радиозащитный препарат (спека автора 24.09.2026).
 *
 * <p>Применение — «как еда»: зажать ПКМ до конца ({@value #USE_TICKS} тиков), см. спеку
 * 24.09 «eatable use». Приём даёт эффект <b>«Абсорбция дозы» 2 уровня на 8 минут</b>:
 * пока он висит, получаемая игроком доза срезается на {@code (30 + уровень²)} % = 34 %
 * (сам разрез — в {@link RadiationSystem}). Разово: +260 mTx химии
 * ({@link PsycheChemical#addDoseToxicity}; 1 mTx = 1e6 nTx) и +10 000 очков = +1 %
 * зависимости (1 % = 10 000 очков, см. {@link PlayerPsyche#POINTS_PER_PERCENT}).
 * Без сообщений в чат (автор 24.09) и без лора в тултипе.</p>
 */
public class CysteamineItem extends Item {

    /** Задержка «как у еды». */
    public static final int USE_TICKS = 32;

    /** «Абсорбция дозы» 2 уровня (амплитуда 1) на 8 минут. */
    public static final int ABSORPTION_SECONDS = 8 * 60;
    public static final int ABSORPTION_AMPLIFIER = 1;

    /** Разовая химия приёма: 260 mTx (1 mTx = 1e6 nTx, см. PsycheChemical). */
    private static final double CHEM_NTX = 260.0 * 1_000_000.0;

    /** Разовая зависимость приёма: +1 % = 10 000 очков (1 000 000 = 100 %). */
    private static final int ADDICTION_POINTS = 10_000;

    public CysteamineItem(Properties properties) {
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

        serverPlayer.addEffect(new MobEffectInstance(ModEffects.DOSE_ABSORPTION,
                ABSORPTION_SECONDS * 20, ABSORPTION_AMPLIFIER, false, true));

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);
        PsycheChemical.addDoseToxicity(serverPlayer, CHEM_NTX);
        psyche.addAddiction(ADDICTION_POINTS);
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
