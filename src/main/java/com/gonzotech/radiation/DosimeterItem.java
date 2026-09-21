package com.gonzotech.radiation;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Дозиметр: ПКМ в руке → приватный отчёт в чат (автор 20.09, п.5):
 * текущая доза облучения (числом шкалы + расшифровка категории) и фон
 * текущего сектора (координаты чанка + эмиссия «X/t»). Сама HUD-шкала под
 * дозиметром уже существует ({@code PsycheHud}), здесь — только отчёт.
 */
public class DosimeterItem extends Item {

    public DosimeterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);
        double percent = psyche.getRadiation() / 10.0; // permille → %

        ChunkPos cp = new ChunkPos(serverPlayer.blockPosition());
        double chunkNzt = ChunkRadiationData.get(serverLevel).value(serverLevel, cp.toLong());

        serverPlayer.sendSystemMessage(
                Component.literal("☢ ").withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal("Доза: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(String.format(java.util.Locale.ROOT, "%.1f%%", percent))
                                .withStyle(colorFor(percent)))
                        .append(Component.literal(" (" + category(percent) + ")").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(" · Сектор [" + cp.x + ", " + cp.z + "]: ")
                                .withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(RadUnits.format(chunkNzt)).withStyle(ChatFormatting.YELLOW)));
        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /** Расшифровка шкалы (п.5): категория дозы по процентам. */
    private static String category(double percent) {
        if (percent < 5.0) return "фон в норме";
        if (percent < 20.0) return "повышенное облучение";
        if (percent < 50.0) return "опасная доза";
        if (percent < 80.0) return "критическая доза";
        return "смертельная доза";
    }

    private static ChatFormatting colorFor(double percent) {
        if (percent < 5.0) return ChatFormatting.GREEN;
        if (percent < 20.0) return ChatFormatting.YELLOW;
        if (percent < 50.0) return ChatFormatting.GOLD;
        if (percent < 80.0) return ChatFormatting.RED;
        return ChatFormatting.DARK_RED;
    }
}
