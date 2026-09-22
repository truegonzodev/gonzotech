package com.gonzotech.core.item;

import com.gonzotech.core.psyche.ModPsycheAttachments;
import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.radiation.ChunkRadiationData;
import com.gonzotech.radiation.RadDose;
import com.gonzotech.core.text.GtUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

import java.util.Locale;

/**
 * «Телифон» (автор 22.09.2026) — универсальный прибор рядом с дозиметром.
 *
 * <p><b>Пока в руке:</b> совмещает все уже существующие приборы — правый столбец шкал HUD
 * показывает и облучение (как дозиметр), и УФ (как УФ-радиометр), над хотбаром одновременно
 * висят строка спидометра и строка солнечных часов. Левые три шкалы (зависимость, стресс,
 * кризис) и химия видны всегда, так что прибор даёт полную картину.
 *
 * <p><b>ПКМ:</b> приватный отчёт в чат — доза, чанк, кортизол (стресс), кризис, зависимость,
 * сколько секунд не пил сусло, УФ и химическое заражение (та самая шестая шкала).
 *
 * <p>Единицы те же, что в HUD: проценты. Внутри зависимость/стресс/кризис — очки
 * ({@code 1 000 000 = 100 %}), облучение/УФ/химия — тысячные.
 */
public class TelifonItem extends Item {

    public TelifonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }

        PlayerPsyche psyche = serverPlayer.getData(ModPsycheAttachments.PSYCHE);

        // ── Облучение и чанк: ровно как у дозиметра, чтобы приборы не расходились ──
        double dose = RadDose.percent(psyche.getRadiation());
        ChunkPos cp = new ChunkPos(serverPlayer.blockPosition());
        double chunkNzt = ChunkRadiationData.get(serverLevel).value(serverLevel, cp.toLong());

        serverPlayer.sendSystemMessage(Component.translatable(
                        "message.gonzotech.telifon.dose",
                        percent(dose).withStyle(colorForDose(dose)),
                        Component.translatable(RadDose.category((int) Math.round(dose * 10.0)).langKey()))
                .withStyle(ChatFormatting.GRAY));
        serverPlayer.sendSystemMessage(Component.translatable(
                        "message.gonzotech.telifon.chunk",
                        cp.x, cp.z,
                        GtUnits.zt(chunkNzt))
                .withStyle(ChatFormatting.GRAY));

        // ── Психика: кортизол (стресс), кризис, зависимость ──
        serverPlayer.sendSystemMessage(Component.translatable("message.gonzotech.telifon.stress",
                        percent(pointsToPercent(psyche.getStress()))).withStyle(ChatFormatting.GRAY));
        serverPlayer.sendSystemMessage(Component.translatable("message.gonzotech.telifon.crisis",
                        percent(pointsToPercent(psyche.getCrisis()))).withStyle(ChatFormatting.GRAY));
        serverPlayer.sendSystemMessage(Component.translatable("message.gonzotech.telifon.addiction",
                        percent(PlayerPsyche.pointsPercent(psyche.getAddiction()))).withStyle(ChatFormatting.GRAY));

        // ── «Сколько секунд не пил сусло» (коридор зависимости) ──
        long mashTick = psyche.getMashTick();
        serverPlayer.sendSystemMessage((mashTick <= 0L
                ? Component.translatable("message.gonzotech.telifon.mash.never")
                : Component.translatable("message.gonzotech.telifon.mash",
                        (serverLevel.getGameTime() - mashTick) / 20L))
                .withStyle(ChatFormatting.GRAY));

        // ── УФ и шестая шкала (химическое заражение) ──
        serverPlayer.sendSystemMessage(Component.translatable("message.gonzotech.telifon.uv",
                        percent(psyche.getUv() / 10.0D)).withStyle(ChatFormatting.GRAY));
        serverPlayer.sendSystemMessage(Component.translatable("message.gonzotech.telifon.chemical",
                        percent(psyche.getChemical() / 10.0D)).withStyle(ChatFormatting.GRAY));

        serverLevel.playSound(null, serverPlayer.blockPosition(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    /** Проценты шкалы «в очках» (0…1 000 000) → проценты 0…100. */
    private static double pointsToPercent(int points) {
        return points / (double) PlayerPsyche.POINTS_PER_PERCENT;
    }

    /**
     * Значение с одним знаком после точки (как в HUD).
     *
     * <p>Возвращаем {@link MutableComponent}, а не {@link Component}: только у изменяемого
     * компонента есть {@code withStyle(ChatFormatting)} — именно на этом падала сборка автора
     * (22.09.2026), потому что {@code percent(dose).withStyle(...)} вызывался на интерфейсе.
     */
    private static MutableComponent percent(double value) {
        return Component.literal(String.format(Locale.ROOT, "%.1f %%", value))
                .withStyle(ChatFormatting.YELLOW);
    }

    /** Цвет категории дозы — те же пороги, что у дозиметра и лучевой болезни. */
    private static ChatFormatting colorForDose(double percent) {
        return switch (RadDose.category((int) Math.round(percent * 10.0))) {
            case FINE -> ChatFormatting.GREEN;
            case ELEVATED -> ChatFormatting.YELLOW;
            case DANGEROUS -> ChatFormatting.GOLD;
            case CRITICAL -> ChatFormatting.RED;
            case LETHAL -> ChatFormatting.DARK_RED;
        };
    }
}
