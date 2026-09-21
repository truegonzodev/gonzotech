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

import java.util.Locale;

/**
 * Антирадин — расходное средство от лучевой болезни (автор 22.09.2026:
 * «добавить антирадин»). ПКМ в руке выводит {@link #SHED_FRACTION} накопленной
 * дозы; при нулевой дозе НЕ тратится (игрок получает подсказку).
 *
 * <p>Побочка — тошнота {@link #NAUSEA_TICKS}: средство чистит шкалу, но не
 * делает игрока бессмертным к облучению, поэтому «догоняться» им под
 * источником бессмысленно (см. {@link RadSickness}). Шкалу чистит
 * СЕРВЕРНАЯ, поэтому HUD обновляется сразу ({@link PsycheNetwork}).</p>
 *
 * <p>Ограничений по частоте нет: каждая доза стоит крафта (йод из центрифуги),
 * так что спам ограничен экономикой — как у всех расходников мода.</p>
 */
public class AntiradinItem extends Item {

    /** Доля набранной дозы, которую выводит одна доза (0.25 = −25%). */
    public static final double SHED_FRACTION = 0.25;

    /** Тошнота после приёма: 12 секунд. */
    private static final int NAUSEA_TICKS = 20 * 12;

    public AntiradinItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);
        int before = psyche.getRadiation();
        if (before <= 0) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.gonzotech.antiradin.none").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }

        int shed = Math.max(1, (int) Math.round(before * SHED_FRACTION));
        psyche.setRadiation(before - shed);
        PsycheNetwork.sendToPlayer(serverPlayer);

        serverPlayer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_TICKS, 0));
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        serverPlayer.displayClientMessage(Component.translatable("message.gonzotech.antiradin.applied",
                percent(psyche.getRadiation())).withStyle(ChatFormatting.GREEN), false);
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    private static String percent(int permille) {
        return String.format(Locale.ROOT, "%.1f", RadDose.percent(permille));
    }
}
