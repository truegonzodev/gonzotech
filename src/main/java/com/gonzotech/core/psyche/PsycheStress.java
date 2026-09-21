package com.gonzotech.core.psyche;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Стресс и экзистенциальный кризис — шкалы «в очках» (спека автора 22.09.2026).
 *
 * <p><b>Единицы.</b> Полная шкала — {@link PlayerPsyche#POINT_MAX} = 1 000 000 очков
 * (100 %). «0.001 % шкалы в секунду» = 10 очков/с — так автор и просил считать,
 * чтобы не ловить округления в тысячных.</p>
 *
 * <h2>Что даёт стресс (очки)</h2>
 * <table>
 *   <tr><th>источник</th><th>ставка</th></tr>
 *   <tr><td>не спал больше {@value #SLEEP_WINDOW_TICKS} тиков</td><td>+{@value #BASE_PER_SECOND}/с</td></tr>
 *   <tr><td>зависимость &gt; {@value #CRAVING_ABOVE_PERCENT} % и сусло не пилось дольше коридора</td>
 *       <td>+{@value #BASE_PER_SECOND}/с ({@value #CRAVING_STACKS_WITH_SLEEP} — складывается с первым)</td></tr>
 *   <tr><td>секунда ночью</td><td>+{@value #NIGHT_PER_SECOND}/с</td></tr>
 *   <tr><td>секунда днём после сна</td><td>−{@value #DAY_SLEPT_RELIEF}/с</td></tr>
 *   <tr><td>секунда в интерфейсе доски резонанса</td><td>+{@value #BOARD_PER_SECOND}/с</td></tr>
 *   <tr><td>любой тик полученного урона</td><td>+{@value #DAMAGE_BURST} разом</td></tr>
 *   <tr><td>выпитое сусло</td><td>−{@value #MASH_RELIEF} разом</td></tr>
 *   <tr><td>проюз «Открытия»</td><td>−{@value #DISCOVERY_RELIEF} стресса, +{@value #DISCOVERY_CRISIS} кризиса</td></tr>
 * </table>
 *
 * <p><b>Бонус зависимости.</b> Каждый 1 % зависимости увеличивает ВСЕ прибавки
 * стресса на 1 % (10 % → ×1.1), снятие — без изменений (прямая спека автора).</p>
 *
 * <p><b>Коридор зависимости.</b> С {@value #CRAVING_ABOVE_PERCENT} % зависимости стресс капает
 * всегда, если сусло не пилось {@value #CRAVING_CORRIDOR_TICKS} тиков; каждый процент выше
 * снижает коридор на {@value #CRAVING_CORRIDOR_STEP} тиков (при 100 % — 3000 тиков).</p>
 *
 * <p><b>Кризис.</b> Капает +{@value #CRISIS_PER_SECOND}/с при стрессе &gt;
 * {@value #STRESS_CRISIS_ABOVE_PERCENT} % и ещё +{@value #CRISIS_PER_SECOND}/с при зависимости &gt;
 * {@value #ADDICTION_CRISIS_ABOVE_PERCENT} % (то есть до +10/с). Уменьшать кризис пока нечем —
 * способы появятся вместе с эффектами и препаратами (автор: «к эффектам перейдём позже»).</p>
 *
 * <p><b>Интерпретации, помеченные явно</b> (меняются одной константой): складывается ли
 * «капание» от зависимости с «капанием» от недосыпа ({@link #CRAVING_STACKS_WITH_SLEEP});
 * день/ночь считаются по игровому времени ({@code gameTime % 24000 < 12000} — работает и в
 * измерениях с фиксированным временем); во сне время не тикает вообще (спящий не копит).</p>
 */
public final class PsycheStress {

    // ── Ставки (очки) ──
    /** Базовая ставка «не спал / зависимость» — 0.001 % шкалы в секунду. */
    public static final int BASE_PER_SECOND = 10;
    /** Секунда в интерфейсе доски резонанса. */
    public static final int BOARD_PER_SECOND = 30;
    /** Секунда ночью. */
    public static final int NIGHT_PER_SECOND = 10;
    /** Снятие за секунду днём, если спал. */
    public static final int DAY_SLEPT_RELIEF = 15;
    /** Разово за тик полученного урона. */
    public static final int DAMAGE_BURST = 200;
    /** Разовое снятие за выпитое сусло. */
    public static final int MASH_RELIEF = 1000;
    /** Разовое снятие за проюз «Открытия». */
    public static final int DISCOVERY_RELIEF = 10_000;
    /** Разовый кризис за проюз «Открытия». */
    public static final int DISCOVERY_CRISIS = 3000;

    // ── Коридоры и пороги ──
    /** «Не спишь больше 24000 тиков» — сутки без сна. */
    public static final long SLEEP_WINDOW_TICKS = 24_000L;
    /** С какой зависимости начинает капать «всегда» (проценты). */
    public static final int CRAVING_ABOVE_PERCENT = 50;
    /** Коридор без сусла при 50 % зависимости (тики). */
    public static final int CRAVING_CORRIDOR_TICKS = 8000;
    /** Насколько коридор короче за каждый процент зависимости выше 50. */
    public static final int CRAVING_CORRIDOR_STEP = 100;
    /** Капание от зависимости складывается с капанием от недосыпа? (интерпретация) */
    public static final boolean CRAVING_STACKS_WITH_SLEEP = true;
    /** Порог стресса, с которого капает кризис (проценты). */
    public static final int STRESS_CRISIS_ABOVE_PERCENT = 70;
    /** Порог зависимости, с которого капает кризис (проценты). */
    public static final int ADDICTION_CRISIS_ABOVE_PERCENT = 60;
    /** Ставка кризиса за каждый выполненный порог. */
    public static final int CRISIS_PER_SECOND = 5;

    /** Период тика — раз в секунду, как у радиации. */
    private static final int PERIOD_TICKS = 20;
    /** Сколько тиков «сердечко» доски считается живым (сеть не мгновенна). */
    private static final long BOARD_PRESENCE_TTL = 60L;

    /** Когда в последний раз приходил пинг «я в интерфейсе доски» (gameTime). */
    private static final Map<UUID, Long> BOARD_SEEN = new HashMap<>();

    private PsycheStress() {
    }

    // ═══════════════════════ тик ═══════════════════════

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % PERIOD_TICKS != 0) {
            return;
        }
        tick(player);
    }

    /** Секундный пересчёт стресса и кризиса. */
    public static void tick(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        // Спящий не копит: сон и есть «пауза» шкалы; заодно отмечаем время сна,
        // чтобы после пробуждения день начался со снятия стресса.
        if (player.isSleeping()) {
            psyche.setSleepTick(now);
            save(player, psyche);
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }

        // Первый вход (или старая карта без таймеров): считаем, что игрок только что
        // спал и только что пил сусло — иначе шкала капала бы с первой секунды.
        if (psyche.getSleepTick() == 0L) {
            psyche.setSleepTick(now);
        }
        if (psyche.getMashTick() == 0L) {
            psyche.setMashTick(now);
        }

        int addiction = psyche.getAddiction();
        long sinceSleep = now - psyche.getSleepTick();
        long sinceMash = now - psyche.getMashTick();
        boolean sleepDeprived = sinceSleep > SLEEP_WINDOW_TICKS;
        boolean craving = addiction > CRAVING_ABOVE_PERCENT * 10
                && sinceMash > cravingCorridor(addiction);

        int gains = 0;
        int losses = 0;
        if (sleepDeprived) {
            gains += BASE_PER_SECOND;
        }
        if (craving && (CRAVING_STACKS_WITH_SLEEP || !sleepDeprived)) {
            gains += BASE_PER_SECOND;
        }

        boolean day = isDay(level);
        if (day) {
            if (!sleepDeprived) {
                losses += DAY_SLEPT_RELIEF;   // «днём если поспал» — снимаем
            }
        } else {
            gains += NIGHT_PER_SECOND;
        }
        if (isAtBoard(player, now)) {
            gains += BOARD_PER_SECOND;
        }

        int before = psyche.getStress();
        int after = clamp(before + bonus(addiction, gains) - losses);
        if (after != before) {
            psyche.setStress(after);
            save(player, psyche);
        }

        // Кризис: два независимых порога по 5/с (стек зависимость+стресс = 10/с).
        int crisisGain = 0;
        if (percent(psyche.getStress()) > STRESS_CRISIS_ABOVE_PERCENT) {
            crisisGain += CRISIS_PER_SECOND;
        }
        if (addiction > ADDICTION_CRISIS_ABOVE_PERCENT * 10) {
            crisisGain += CRISIS_PER_SECOND;
        }
        if (crisisGain > 0) {
            int wasCrisis = psyche.getCrisis();
            psyche.setCrisis(clamp(wasCrisis + crisisGain));
            if (psyche.getCrisis() != wasCrisis) {
                save(player, psyche);
            }
        }
    }

    // ═══════════════════════ разовые источники ═══════════════════════

    /** Любой тик полученного урона: +{@value #DAMAGE_BURST} очков стресса. */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0.0F) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || player.isDeadOrDying()) {
            return;
        }
        gain(player, DAMAGE_BURST);
    }

    /**
     * Разовое начисление стресса извне — прибавка идёт через бонус зависимости,
     * как и всё остальное. Готовый вызов для будущих источников (например,
     * «преждевременный крафт», число за автором — см. TODO в {@code Phase3Events}).
     */
    public static void gain(ServerPlayer player, int points) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setStress(clamp(psyche.getStress() + bonus(psyche.getAddiction(), points)));
        save(player, psyche);
    }

    /** Разовое снятие стресса извне — без бонуса зависимости (как и все снятия). */
    public static void relieve(ServerPlayer player, int points) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setStress(clamp(psyche.getStress() - points));
        save(player, psyche);
    }

    /**
     * Выпитое сусло: снимает {@value #MASH_RELIEF} стресса и сбрасывает коридор
     * зависимости (таймер сусла). Зависимость за сусло начисляет {@code Phase3Events}.
     */
    public static void onMashDrunk(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setStress(clamp(psyche.getStress() - MASH_RELIEF));
        psyche.setMashTick(player.serverLevel().getGameTime());
        save(player, psyche);
    }

    /**
     * Проюз «Открытия»: −{@value #DISCOVERY_RELIEF} стресса, +{@value #DISCOVERY_CRISIS}
     * кризиса (автор: «проюз открытия единовременно снимает 10000 стресса но добавляет 3000 кризиса»).
     */
    public static void onDiscoveryUsed(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setStress(clamp(psyche.getStress() - DISCOVERY_RELIEF));
        psyche.setCrisis(clamp(psyche.getCrisis() + DISCOVERY_CRISIS));
        save(player, psyche);
    }

    /** Клиент говорит, что интерфейс доски резонанса открыт (сердечко раз в секунду). */
    public static void seenBoard(ServerPlayer player) {
        BOARD_SEEN.put(player.getUUID(), player.serverLevel().getGameTime());
    }

    /** Забыть игрока (выход/смерть) — карты не должны течь. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BOARD_SEEN.remove(player.getUUID());
        }
    }

    // ═══════════════════════ математика ═══════════════════════

    /**
     * Бонус зависимости к прибавкам: 1 % зависимости = +1 % к получаемым очкам
     * (зависимость хранится в тысячных, поэтому делим на 1000).
     */
    public static int bonus(int addictionPermille, int points) {
        return (int) Math.round(points * (1.0 + addictionPermille / 1000.0));
    }

    /** Сколько тиков без сусла терпит такая зависимость (при 50 % — 8000, при 100 % — 3000). */
    public static long cravingCorridor(int addictionPermille) {
        int percentAbove = Math.max(0, addictionPermille / 10 - CRAVING_ABOVE_PERCENT);
        return Math.max(0, CRAVING_CORRIDOR_TICKS - (long) percentAbove * CRAVING_CORRIDOR_STEP);
    }

    /** Проценты шкалы «в очках» (1 000 000 = 100 %). */
    public static int percent(int points) {
        return points / (PlayerPsyche.POINT_MAX / 100);
    }

    /** День по игровому времени: 0–12000 тиков = день (работает в любом измерении). */
    private static boolean isDay(ServerLevel level) {
        return level.getDayTime() % 24_000L < 12_000L;
    }

    private static boolean isAtBoard(ServerPlayer player, long now) {
        Long seen = BOARD_SEEN.get(player.getUUID());
        return seen != null && now - seen <= BOARD_PRESENCE_TTL;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(PlayerPsyche.POINT_MAX, v));
    }

    private static void save(ServerPlayer player, PlayerPsyche psyche) {
        player.setData(ModPsycheAttachments.PSYCHE, psyche);
        PsycheNetwork.sendToPlayer(player);
    }
}
