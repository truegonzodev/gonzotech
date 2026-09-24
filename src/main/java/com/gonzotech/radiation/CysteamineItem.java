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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Цистамин — радиозащитный препарат (спека автора 24.09.2026).
 *
 * <p>Приём даёт эффект <b>«Абсорбция дозы» 2 уровня на 8 минут</b>: пока он висит,
 * получаемая игроком доза срезается на {@code (30 + уровень²)} % = 34 % (сам разрез
 * — в {@link RadiationSystem}, там же щит хазмата).</p>
 *
 * <p>Разово при приёме («единоразово»):</p>
 * <ul>
 *   <li><b>+260 mTx</b> химии ({@link PsycheChemical#addDoseToxicity}; 1 mTx = 1e6 nTx);</li>
 *   <li><b>+10 000 очков</b> зависимости = <b>+1 %</b> шкалы (автор 24.09: «поправь,
 *       если я не прав по конверсии» — конверсия верная: 1 % = 10 000 очков,
 *       см. {@link PlayerPsyche#POINTS_PER_PERCENT}).</li>
 * </ul>
 */
public class CysteamineItem extends Item {

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
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        serverPlayer.addEffect(new MobEffectInstance(ModEffects.DOSE_ABSORPTION,
                ABSORPTION_SECONDS * 20, ABSORPTION_AMPLIFIER, false, true));

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);
        PsycheChemical.addDoseToxicity(serverPlayer, CHEM_NTX);
        psyche.addAddiction(ADDICTION_POINTS);
        serverPlayer.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(serverPlayer);

        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
        }

        serverPlayer.displayClientMessage(Component.translatable("message.gonzotech.cysteamine.applied")
                .withStyle(ChatFormatting.GOLD), false);
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
