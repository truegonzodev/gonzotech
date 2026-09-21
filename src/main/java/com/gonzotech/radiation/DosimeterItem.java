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
 * Дозиметр: ПКМ в руке → приватный отчёт в чат (автор 20.09, п.5; формат
 * уточнён 21.09): две строки — «Доза: X % (категория)» и «Чанк [x, z]: Y/t».
 * Тексты — через lang-ключи ({@code message.gonzotech.dosimeter.*}); значок радиации
 * убран. Сама HUD-шкала под дозиметром уже существует ({@code PsycheHud}),
 * здесь — только отчёт.
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
        double percent = RadDose.percent(psyche.getRadiation()); // permille → %

        ChunkPos cp = new ChunkPos(serverPlayer.blockPosition());
        double chunkNzt = ChunkRadiationData.get(serverLevel).value(serverLevel, cp.toLong());

        // Автор (21.09): вывод в две строки — сначала доза, затем чанк; значок радиации убран.
        serverPlayer.sendSystemMessage(Component.translatable(
                        "message.gonzotech.dosimeter.dose",
                        Component.literal(String.format(java.util.Locale.ROOT, "%.1f%%", percent))
                                .withStyle(colorFor(percent)),
                        Component.translatable(categoryKey(percent)))
                .withStyle(ChatFormatting.GRAY));
        serverPlayer.sendSystemMessage(Component.translatable(
                        "message.gonzotech.dosimeter.chunk",
                        cp.x, cp.z,
                        Component.literal(RadUnits.format(chunkNzt)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));

        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /**
     * Ключ категории дозы (п.5): тексты живут в lang (автор 21.09 — без значка
     * радиации). Сами пороги — в {@link RadDose}: по ним же работают эффекты
     * лучевой болезни, поэтому «отчёт» и «последствия» всегда совпадают.
     */
    private static String categoryKey(double percent) {
        return RadDose.category((int) Math.round(percent * 10.0)).langKey();
    }

    private static ChatFormatting colorFor(double percent) {
        return switch (RadDose.category((int) Math.round(percent * 10.0))) {
            case FINE -> ChatFormatting.GREEN;
            case ELEVATED -> ChatFormatting.YELLOW;
            case DANGEROUS -> ChatFormatting.GOLD;
            case CRITICAL -> ChatFormatting.RED;
            case LETHAL -> ChatFormatting.DARK_RED;
        };
    }
}
