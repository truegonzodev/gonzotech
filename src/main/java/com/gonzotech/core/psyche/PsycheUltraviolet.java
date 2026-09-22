package com.gonzotech.core.psyche;

import com.gonzotech.radiation.Necrosis;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Шкала «УФ излучение» (спека автора 22.09.2026) — работает иначе, чем остальные:
 * <b>сама по себе эффектов не даёт</b>, а только тает и «кусает» триггерами.
 *
 * <h2>Правила автора (дословно)</h2>
 * <ul>
 *   <li><b>Таяние:</b> {@value #DECAY_PERMILLE_PER_TICK} тысячных в тик — это ровно
 *       {@value #DECAY_PERCENT_PER_TICK} % от МАКСИМУМА шкалы (не от текущего значения);</li>
 *   <li><b>Триггер:</b> за каждые снятые {@value #TRIGGER_STEP_PERCENT} % — урон
 *       {@value #TRIGGER_DAMAGE_HP} HP; и каждый триггер отдельно бросает
 *       {@value #WEAK_SLOW_PERCENT} % на «Слабость I + Замедление I» на 3 минуты и
 *       {@value #NECROSIS_PERCENT} % на «Некроз I» навсегда;</li>
 *   <li><b>Переключаемая:</b> пока идёт прибавка УФ — шкала НЕ тает; как только прибавка
 *       прекратилась, начинается таяние и триггеры;</li>
 *   <li><b>Источник (пока единственный):</b> доза радиации выше
 *       {@value #RADIATION_SOURCE_PERCENT} % — игрок постепенно получает УФ,
 *       +{@value #GAIN_PERCENT_PER_TICK} % в тик;</li>
 *   <li><b>Смерть</b> полностью очищает шкалу.</li>
 * </ul>
 *
 * <p>Остальные источники (космос, токамак и т.д.) — «потом» (автор): точка входа для них
 * уже есть — {@link #charge(ServerPlayer, double)}.</p>
 */
public final class PsycheUltraviolet {

    /** Источник: доза радиации выше 70 % (в тысячных — 700). */
    public static final int RADIATION_SOURCE_PERMILLE = 700;
    // Автор: «доза радиации >70 % — игрок начинает постепенно получать и УФ, +0.025 % в тик».
    public static final int RADIATION_SOURCE_PERCENT = RADIATION_SOURCE_PERMILLE / 10;
    /** +0.025 % в тик (в тысячных — 0.25). */
    public static final double GAIN_PERCENT_PER_TICK = 0.025;
    private static final double GAIN_PERMILLE_PER_TICK = GAIN_PERCENT_PER_TICK * 10.0;

    /** Таяние: 1 % от максимума в тик (= 10 тысячных). */
    public static final int DECAY_PERCENT_PER_TICK = 1;
    private static final int DECAY_PERMILLE_PER_TICK = DECAY_PERCENT_PER_TICK * 10;

    /** Триггер каждые снятые 5 %. */
    public static final int TRIGGER_STEP_PERCENT = 5;
    private static final int TRIGGER_STEP_PERMILLE = TRIGGER_STEP_PERCENT * 10;
    /** Урон за триггер — ровно 1 HP. */
    public static final float TRIGGER_DAMAGE_HP = 1.0F;
    /** Шанс «Слабость I + Замедление I» на 3 минуты. */
    public static final double WEAK_SLOW_PERCENT = 2.0;
    private static final double WEAK_SLOW_CHANCE = WEAK_SLOW_PERCENT / 100.0;
    private static final int WEAK_SLOW_TICKS = 3 * 60 * 20;
    /** Шанс «Некроз I» навсегда. */
    public static final double NECROSIS_PERCENT = 0.5;
    private static final double NECROSIS_CHANCE = NECROSIS_PERCENT / 100.0;

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Random RNG = new Random();

    private PsycheUltraviolet() {
    }

    /** Накопители: прибавка дробная (0.25 в тик), счётчик «снятых 5 %» — целый. */
    private static final class State {
        private double gainAcc;
        private double triggerAcc;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        // Источник пока один: радиация выше порога. Прибавка — «в тик», поэтому и тик
        // здесь каждый (не раз в секунду, как у остальных шкал).
        if (player.getData(ModPsycheAttachments.PSYCHE).getRadiation() > RADIATION_SOURCE_PERMILLE) {
            charge(player, GAIN_PERMILLE_PER_TICK);
        } else {
            decay(player);
        }
    }

    /**
     * Прибавка УФ из любого источника (тысячные). Пока прибавка идёт, шкала не тает —
     * это и есть «переключаемость». Точка входа для будущих источников
     * (космос, токамак): просто звать раз в тик.
     */
    public static void charge(ServerPlayer player, double permille) {
        if (permille <= 0.0) {
            return;
        }
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());
        state.gainAcc += permille;
        int gain = (int) state.gainAcc;
        if (gain <= 0) {
            return;
        }
        state.gainAcc -= gain;
        int before = psyche.getUv();
        int value = Math.min(PlayerPsyche.MAX, before + gain);
        if (value != before) {
            psyche.setUv(value);
            player.setData(ModPsycheAttachments.PSYCHE, psyche);
            PsycheNetwork.sendToPlayer(player);
        }
    }

    /** Таяние: 1 % от максимума в тик + триггеры за каждые снятые 5 %. */
    private static void decay(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        int before = psyche.getUv();
        if (before <= 0) {
            return;
        }
        State state = STATES.computeIfAbsent(player.getUUID(), key -> new State());
        int removed = Math.min(DECAY_PERMILLE_PER_TICK, before);
        psyche.setUv(before - removed);
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);

        state.triggerAcc += removed;
        while (state.triggerAcc >= TRIGGER_STEP_PERMILLE) {
            state.triggerAcc -= TRIGGER_STEP_PERMILLE;
            trigger(player);
        }
    }

    /**
     * Триггер за снятые 5 %: 1 HP урона, затем два независимых броска — «Слабость I +
     * Замедление I» на 3 минуты (2 %) и «Некроз I» навсегда (0.5 %).
     */
    private static void trigger(ServerPlayer player) {
        player.hurt(player.damageSources().generic(), TRIGGER_DAMAGE_HP);
        if (RNG.nextDouble() < WEAK_SLOW_CHANCE) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_SLOW_TICKS, 0, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WEAK_SLOW_TICKS, 0, false, true));
        }
        if (RNG.nextDouble() < NECROSIS_CHANCE) {
            Necrosis.grant(player, 0);   // «Некроз I» навсегда
        }
    }

    /** Смерть: шкала очищается полностью (автор). */
    public static void clear(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setUv(0);
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        STATES.remove(player.getUUID());
        PsycheNetwork.sendToPlayer(player);
    }

    /** Забыть игрока (выход). */
    public static void forget(UUID playerId) {
        STATES.remove(playerId);
    }
}
