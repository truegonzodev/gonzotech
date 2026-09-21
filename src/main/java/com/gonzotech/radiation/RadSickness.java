package com.gonzotech.radiation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Последствия облучения — «что делает шкала» (спека автора 22.09.2026).
 * Работает раз в секунду из цикла {@link RadiationSystem}: своего тика нет.
 *
 * <p>Три вещи за секунду:</p>
 * <ol>
 *   <li><b>Постоянные эффекты</b> категории — держатся, пока доза в категории
 *       (обновляются каждую секунду, чтобы не мерцали);</li>
 *   <li><b>Вспышки</b> — с шансом категории ({@link RadDose.Tier#flashChance})
 *       игрок получает от 1 до 3 эффектов из пула категории на случайную
 *       длительность. Число эффектов — «три броска по 1/3»: 1 эффект всегда
 *       (первый бросок прошёл), 2 — с шансом 2/3, 3 — с шансом 1/3, повторов нет;</li>
 *   <li><b>Некроз</b> — выше 30 % дозы раз в секунду 0.1 % шанс получить вечный
 *       {@link ModEffects#NECROSIS} (уровень I, а выше 70 % — сразу II).
 *       Эффект НЕ снимается падением дозы: лечится отдельно (механика — {@link Necrosis}).</li>
 * </ol>
 *
 * <p>Смерть на 100 % — уроном {@code gonzotech:radiation}. Периодического урона
 * у категорий больше нет (в таблице автора его не было). Творческий/наблюдательный
 * режим и мёртвые игроки последствий не получают.</p>
 */
public final class RadSickness {

    /** Длительность постоянных эффектов: 3 с (обновляются каждую секунду). */
    private static final int EFFECT_TICKS = 60;

    /** Смертельный удар на 100 % дозы (заведомо больше любого запаса здоровья). */
    private static final float LETHAL_BLOW = 1000.0f;

    /** Последняя категория по игроку — чтобы сообщать только о ПЕРЕХОДАХ. */
    private static final Map<UUID, RadDose.Category> LAST_STAGE = new HashMap<>();
    /** RNG последствий (серверный тик, потокобезопасность не нужна). */
    private static final Random RNG = new Random();

    private RadSickness() {
    }

    /** Применить последствия текущей дозы (раз в секунду, после пересчёта шкалы). */
    public static void tick(ServerPlayer player, ServerLevel level, int permille) {
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            LAST_STAGE.remove(player.getUUID());
            return;
        }

        RadDose.Tier tier = RadDose.tier(permille);
        RadDose.Category category = RadDose.category(permille);
        RadDose.Category previous = LAST_STAGE.put(player.getUUID(), category);
        if (previous != category) {
            announce(player, category, previous);
        }

        for (RadDose.Steady steady : tier.steady()) {
            player.addEffect(new MobEffectInstance(steady.effect(), EFFECT_TICKS, steady.amplifier(), true, true));
        }
        rollFlash(player, tier);

        if (permille > RadDose.NECROSIS_AT && RNG.nextDouble() < RadDose.NECROSIS_CHANCE_PER_SECOND) {
            int wanted = permille > RadDose.NECROSIS_LEVEL2_AT ? 1 : 0;
            Necrosis.grant(player, wanted);
        }

        if (permille >= RadDose.MAX) {
            player.hurt(RadDose.damageSource(level), LETHAL_BLOW);
            LAST_STAGE.remove(player.getUUID());
        }
    }

    /** Забыть игрока (смерть/выход) — карта не должна течь. */
    public static void forget(UUID playerId) {
        LAST_STAGE.remove(playerId);
    }

    /**
     * Вспышка: с шансом категории — «три броска по 1/3». Первый прошёл (шанс
     * категории), число эффектов = число успешных бросков, но не больше пула;
     * варианты не повторяются.
     */
    private static void rollFlash(ServerPlayer player, RadDose.Tier tier) {
        List<RadDose.Flavor> pool = tier.pool();
        if (pool.isEmpty() || RNG.nextDouble() >= tier.flashChance()) {
            return;
        }
        int successes = 1;
        for (int i = 1; i < 3; i++) {
            if (RNG.nextInt(3) == 0) {
                successes++;
            }
        }
        int count = Math.min(successes, pool.size());
        List<RadDose.Flavor> left = new ArrayList<>(pool);
        for (int i = 0; i < count; i++) {
            RadDose.Flavor flavor = left.remove(RNG.nextInt(left.size()));
            int span = flavor.maxSeconds() - flavor.minSeconds();
            int seconds = flavor.minSeconds() + (span > 0 ? RNG.nextInt(span + 1) : 0);
            player.addEffect(new MobEffectInstance(flavor.effect(), seconds * 20,
                    flavor.amplifier(), true, true));
        }
    }

    /**
     * Сообщение о смене категории — в акшен-бар (не засоряет чат):
     * «Облучение: опасная доза» / «Облучение: фон в норме».
     */
    private static void announce(ServerPlayer player, RadDose.Category now, RadDose.Category before) {
        if (before == null) {
            return; // первый вход в игру — молча, состояние и так в HUD
        }
        ChatFormatting color = switch (now) {
            case FINE -> ChatFormatting.GREEN;
            case ELEVATED -> ChatFormatting.YELLOW;
            case DANGEROUS -> ChatFormatting.GOLD;
            case CRITICAL -> ChatFormatting.RED;
            case LETHAL -> ChatFormatting.DARK_RED;
        };
        player.displayClientMessage(
                Component.translatable("message.gonzotech.rad.stage", now.label()).withStyle(color), true);
    }
}
