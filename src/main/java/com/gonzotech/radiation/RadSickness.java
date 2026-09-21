package com.gonzotech.radiation;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Последствия облучения — «что делает шкала» (автор: «заняться шкалами»,
 * решение 22.09.2026). Работает на сервере раз в секунду из того же цикла,
 * что и {@link RadiationSystem} (отдельного тика нет — экономия).
 *
 * <p>Все числа собраны ЗДЕСЬ, чтобы автор мог докрутить баланс одной правкой.
 * Категории — из {@link RadDose} (те же, что печатает дозиметр):</p>
 * <pre>
 *   повышенное (5–20%)   голод I;                        тошнота I — 5 с каждые 15 с
 *   опасная   (20–50%)   голод II, слабость I;           тошнота I — 5 с /10 с, усталость I — 5 с /10 с
 *   критическая(50–80%)  + слабость II, усталость I, замедление I; тошнота II — 4 с /6 с; 1 урон каждые 8 с
 *   смертельная(80–100%) + голод III, слабость III, усталость II; тошнота II — 4 с /5 с, слепота 4 с /30 с;
 *                        2 урона каждые 4 с; на 100% — смерть «лучевая болезнь»
 * </pre>
 *
 * <p>Амплификатор 0 = уровень I. Постоянные эффекты обновляются каждую секунду
 * длительностью {@link #EFFECT_TICKS} — значки не мерцают, а из зоны облучения
 * эффекты уходят за 3 секунды сами. Урон идёт по своему типу
 * ({@code gonzotech:radiation}) и <b>броню игнорирует</b> — от дозы спасает не
 * броня, а экран/костюм (следующий срез) и лечение ({@link AntiradinItem}).
 *
 * <p>Творческий/наблюдательный режим и мёртвые игроки последствий не получают
 * (админская защита, как и в остальном моде).
 */
public final class RadSickness {

    /** Длительность постоянных эффектов: 3 с (обновляются каждую секунду). */
    private static final int EFFECT_TICKS = 60;

    /** Урон «лучевой болезни» по категориям (в единицах здоровья; 2.0 = 1 сердце). */
    private static final float CRITICAL_DAMAGE = 1.0f;
    private static final int CRITICAL_DAMAGE_PERIOD = 8 * 20;
    private static final float LETHAL_DAMAGE = 2.0f;
    private static final int LETHAL_DAMAGE_PERIOD = 4 * 20;

    /** Смертельный удар на 100% дозы (заведомо больше любого запаса здоровья). */
    private static final float LETHAL_BLOW = 1000.0f;

    /** Последняя категория по игроку — чтобы сообщать только о ПЕРЕХОДАХ. */
    private static final Map<UUID, RadDose.Category> LAST_STAGE = new HashMap<>();

    private RadSickness() {
    }

    /**
     * Применить последствия текущей дозы. Вызывается раз в секунду,
     * после пересчёта шкалы.
     */
    public static void tick(ServerPlayer player, ServerLevel level, int permille) {
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            LAST_STAGE.remove(player.getUUID());
            return;
        }

        RadDose.Category category = RadDose.category(permille);
        RadDose.Category previous = LAST_STAGE.put(player.getUUID(), category);
        if (previous != category) {
            announce(player, category, previous);
        }

        applyEffects(player, category);

        if (category == RadDose.Category.LETHAL) {
            if (permille >= RadDose.MAX) {
                player.hurt(RadDose.damageSource(level), LETHAL_BLOW);
                LAST_STAGE.remove(player.getUUID());
            } else if (player.tickCount % LETHAL_DAMAGE_PERIOD == 0) {
                player.hurt(RadDose.damageSource(level), LETHAL_DAMAGE);
            }
        } else if (category == RadDose.Category.CRITICAL
                && player.tickCount % CRITICAL_DAMAGE_PERIOD == 0) {
            player.hurt(RadDose.damageSource(level), CRITICAL_DAMAGE);
        }
    }

    /** Забыть игрока (смерть/выход) — карта не должна течь. */
    public static void forget(UUID playerId) {
        LAST_STAGE.remove(playerId);
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

    /** Постоянные эффекты и «пульсы» по категории. */
    private static void applyEffects(ServerPlayer player, RadDose.Category category) {
        switch (category) {
            case FINE -> {
                // ничего: шкала ещё не мешает жить
            }
            case ELEVATED -> {
                constant(player, MobEffects.HUNGER, 0);
                pulse(player, MobEffects.CONFUSION, 0, 15, 5);
            }
            case DANGEROUS -> {
                constant(player, MobEffects.HUNGER, 1);
                constant(player, MobEffects.WEAKNESS, 0);
                pulse(player, MobEffects.CONFUSION, 0, 10, 5);
                pulse(player, MobEffects.DIG_SLOWDOWN, 0, 10, 5);
            }
            case CRITICAL -> {
                constant(player, MobEffects.HUNGER, 1);
                constant(player, MobEffects.WEAKNESS, 1);
                constant(player, MobEffects.DIG_SLOWDOWN, 0);
                constant(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
                pulse(player, MobEffects.CONFUSION, 1, 6, 4);
            }
            case LETHAL -> {
                constant(player, MobEffects.HUNGER, 2);
                constant(player, MobEffects.WEAKNESS, 2);
                constant(player, MobEffects.DIG_SLOWDOWN, 1);
                constant(player, MobEffects.MOVEMENT_SLOWDOWN, 0);
                pulse(player, MobEffects.CONFUSION, 1, 5, 4);
                pulse(player, MobEffects.BLINDNESS, 0, 30, 4);
            }
        }
    }

    /** Постоянный (обновляемый) эффект. */
    private static void constant(ServerPlayer player, Holder<MobEffect> effect, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amplifier, true, true));
    }

    /** «Пульс»: раз в {@code everySeconds} секунд на {@code seconds} секунд. */
    private static void pulse(ServerPlayer player, Holder<MobEffect> effect, int amplifier,
                              int everySeconds, int seconds) {
        if (player.tickCount % (everySeconds * 20) == 0) {
            player.addEffect(new MobEffectInstance(effect, seconds * 20, amplifier, true, true));
        }
    }
}
