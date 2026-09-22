package com.gonzotech.core.psyche;

import com.gonzotech.core.registry.ModEffects;
import com.gonzotech.sunevent.SunEventServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Шкала стресса и экзистенциального кризиса (спека автора 22.09.2026, расширена в тот же день).
 *
 * <p><b>Единицы.</b> Зависимость, стресс и кризис — в очках
 * ({@link PlayerPsyche#POINT_MAX} = 1 000 000 = 100 %); «0.001 % в секунду» = 10 очков/с.
 * Кризис персистентный: смертью не чистится, наоборот даёт +{@value #DEATH_CRISIS_BURST}.
 * Пороги и проценты считаются через {@link PlayerPsyche#pointsPercent(int)}.</p>
 *
 * <h2>Постоянные источники (за секунду)</h2>
 * <table>
 *   <tr><th>источник</th><th>очков/с</th></tr>
 *   <tr><td>не спал больше {@value #SLEEP_WINDOW_TICKS} тиков</td><td>+{@value #BASE_PER_SECOND}</td></tr>
 *   <tr><td>зависимость &gt; {@value #CRAVING_ABOVE_PERCENT} % и сусло не пилось дольше коридора</td>
 *       <td>+{@value #BASE_PER_SECOND}</td></tr>
 *   <tr><td>ночь</td><td>+{@value #NIGHT_PER_SECOND}</td></tr>
 *   <tr><td>интерфейс доски резонанса</td><td>+{@value #BOARD_PER_SECOND}</td></tr>
 *   <tr><td>Ад и Энд</td><td>+{@value #NETHER_END_PER_SECOND}</td></tr>
 *   <tr><td>багровый день (суневент)</td><td>+{@value #SUN_EVENT_PER_SECOND}</td></tr>
 *   <tr><td>голод ниже 20 %</td><td>+{@value #STARVING_PER_SECOND}</td></tr>
 *   <tr><td>здоровье ниже 20 %</td><td>+{@value #LOW_HEALTH_PER_SECOND}</td></tr>
 *   <tr><td>эффект «Некроз»</td><td>+{@value #NECROSIS_PER_SECOND}</td></tr>
 *   <tr><td>биом deep_dark</td><td>+{@value #DEEP_DARK_PER_SECOND}</td></tr>
 *   <tr><td>полная темнота (свет 0–1)</td><td>+{@value #DARKNESS_PER_SECOND}</td></tr>
 *   <tr><td>день, спал</td><td>−{@value #DAY_SLEPT_RELIEF}</td></tr>
 *   <tr><td>гроза на улице, а игрок под крышей у источника света</td><td>−{@value #SHELTER_RAIN_RELIEF}</td></tr>
 *   <tr><td>любой из эффектов сила/регенерация/абсорбция/защита (не суммируется)</td>
 *       <td>−{@value #GOOD_EFFECTS_RELIEF}</td></tr>
 *   <tr><td>плавать в лаве под огнестойкостью</td><td>−{@value #LAVA_SWIM_RELIEF}</td></tr>
 *   <tr><td>день, без брони, вишнёвая роща или грибной остров</td><td>−{@value #SAFE_BIOME_RELIEF}</td></tr>
 *   <tr><td>пластинка играет днём при полном голоде и здоровье</td><td>−{@value #JUKEBOX_RELIEF}</td></tr>
 * </table>
 *
 * <h2>Разовые события</h2>
 * <p>Все они — в {@link PsycheStressEvents} (урон, взрывы, смерти сущностей, тотем, сон, еда,
 * рычаг, эндермен и т. д.). Здесь — только то, что привязано к тику: падение зависимости
 * ({@link #decayAddiction}) и смерть игрока {@link #onPlayerClone}.</p>
 *
 * <p><b>Бонус зависимости</b> множит только ПРИБАВКИ ({@link #bonus}); снятия и падение
 * зависимости — без изменений.</p>
 *
 * <p><b>Кризис</b> (автор 22.09): +{@value #CRISIS_PER_SECOND}/с при стрессе &gt;
 * {@value #STRESS_CRISIS_ABOVE_PERCENT} % (стрессом можно дойти до 100 %), и +{@value #CRISIS_PER_SECOND}/с
 * при зависимости {@value #ADDICTION_CRISIS_MIN_PERCENT}–{@value #ADDICTION_CRISIS_MAX_PERCENT} %,
 * но только пока кризис ниже {@value #ADDICTION_CRISIS_CAP_PERCENT} % — «до 100 % дойти нельзя на
 * зависимости, на стрессе можно».</p>
 */
public final class PsycheStress {

    // ── Постоянные ставки (очки/с) ──
    /** Базовая ставка «не спал / зависимость» — 0.001 % шкалы в секунду. */
    public static final int BASE_PER_SECOND = 10;
    /** Секунда в интерфейсе доски резонанса. */
    public static final int BOARD_PER_SECOND = 30;
    /** Секунда ночью. */
    public static final int NIGHT_PER_SECOND = 10;
    /** Снятие за секунду днём, если спал. */
    public static final int DAY_SLEPT_RELIEF = 15;
    /** Ад и Энд (автор 22.09). */
    public static final int NETHER_END_PER_SECOND = 10;
    /** Багровый день суневента (автор 22.09). */
    public static final int SUN_EVENT_PER_SECOND = 10;
    /** Голод ниже 20 % (автор 22.09). */
    public static final int STARVING_PER_SECOND = 5;
    /** Здоровье ниже 20 % (автор 22.09). */
    public static final int LOW_HEALTH_PER_SECOND = 5;
    /** Эффект «Некроз» (автор 22.09). */
    public static final int NECROSIS_PER_SECOND = 5;
    /** Биом deep_dark (автор 22.09). */
    public static final int DEEP_DARK_PER_SECOND = 5;
    /** Полная темнота, уровень света 0–1 (автор 22.09). */
    public static final int DARKNESS_PER_SECOND = 1;

    /** Гроза на улице, а игрок под крышей у источника света (автор 22.09). */
    public static final int SHELTER_RAIN_RELIEF = 5;
    /** Сила/регенерация/абсорбция/защита — наличие хотя бы одного (автор 22.09). */
    public static final int GOOD_EFFECTS_RELIEF = 5;
    /** Плавать в лаве под огнестойкостью (автор 22.09). */
    public static final int LAVA_SWIM_RELIEF = 5;
    /** День, без брони, вишнёвая роща/грибной остров (автор 22.09). */
    public static final int SAFE_BIOME_RELIEF = 5;
    /** Пластинка играет рядом днём при полном голоде и здоровье (автор 22.09). */
    public static final int JUKEBOX_RELIEF = 9;
    /** Радиус поиска играющего проигрывателя. */
    public static final int JUKEBOX_RADIUS = 8;

    // ── Разовые (события — в PsycheStressEvents) ──
    /** Любой тик урона. */
    public static final int DAMAGE_BURST = 200;
    /** Взрыв рядом. */
    public static final int EXPLOSION_NEAR_BURST = 500;
    /** Радиус «взрыва рядом». */
    public static final double EXPLOSION_NEAR_RADIUS = 8.0;
    /** Дополнительно, если урон пришёл от взрыва. */
    public static final int EXPLOSION_DAMAGE_BURST = 200;
    /** Смерть кота/оцелота/собаки/волка (даже не прирученных). */
    public static final int PET_DEATH_BURST = 4000;
    public static final int PET_DEATH_CRISIS = 100;
    /** Смерть детёныша кота/оцелота/собаки/волка. */
    public static final int PET_BABY_DEATH_BURST = 40_000;
    public static final int PET_BABY_DEATH_CRISIS = 1000;
    /** Смерть скотины (овца/корова и т. д.), если кризис ниже 20 %. */
    public static final int LIVESTOCK_DEATH_BURST = 100;
    public static final int LIVESTOCK_CRISIS_BELOW_PERCENT = 20;
    /** Насколько далеко смерть зверя дотягивается до игрока (убийца — всегда). */
    public static final double DEATH_AFFECT_RADIUS = 32.0;
    /** Кормление питомца с рук. */
    public static final int PET_FEED_RELIEF = 100;
    /** Кулдаун на «кормление», чтобы клик-спам не был фармом (тики). */
    public static final long PET_FEED_COOLDOWN = 20L;
    /** Прок тотема бессмертия: +0.2 % зависимости = 2000 очков (автор 22.09). */
    public static final int TOTEM_ADDICTION_BURST = 2000;
    public static final int TOTEM_STRESS_BURST = 1000;
    /** Проюз антирадинового абсорбента. */
    public static final int ABSORBENT_STRESS_BURST = 100;
    /** Скример эндермена (взгляд в глаза). */
    public static final int ENDERMAN_STARE_BURST = 400;
    /** Убийство жителя. */
    public static final int VILLAGER_KILL_STRESS = 1000;
    public static final int VILLAGER_KILL_CRISIS = 50;
    /** Сон в кровати. */
    public static final int SLEEP_RELIEF = 500;
    /** Сладкое за порцию (золотое яблоко, печенье, торт). */
    public static final int SWEET_RELIEF = 50;
    /** Убийство эндер-дракона. */
    public static final int DRAGON_KILL_RELIEF = 5000;
    public static final int DRAGON_KILL_CRISIS = 400;
    /** Ваншот моба. */
    public static final int ONESHOT_RELIEF = 200;
    public static final int ONESHOT_CRISIS = 10;
    /** Рычаг/кнопка: антистресс-фиджет. */
    public static final int LEVER_RELIEF = 1;
    public static final int LEVER_DAILY_LIMIT = 200;
    public static final int LEVER_PLATEAU_CRISIS = 1;
    public static final long LEVER_COOLDOWN_TICKS = 24_000L;
    /** Выпитое сусло. */
    public static final int MASH_RELIEF = 1000;
    /** Проюз «Открытия». */
    public static final int DISCOVERY_RELIEF = 10_000;
    public static final int DISCOVERY_CRISIS = 3000;

    // ── Смерть игрока ──
    /** Смерть чистит 5 % ТЕКУЩЕГО значения зависимости и стресса. */
    public static final int DEATH_CLEAR_PERCENT = 5;
    /** Смерть добавляет кризиса (он персистентен, смертью не чистится). */
    public static final int DEATH_CRISIS_BURST = 500;

    // ── Зависимость ──
    /** Дней без сусла до начала падения зависимости. */
    public static final long ADDICTION_DECAY_AFTER_DAYS = 20L;
    /** Очков зависимости в секунду за каждый день сверх 20 (автор 22.09). */
    public static final int ADDICTION_DECAY_PER_DAY = 10;
    /** Потолок падения (1 %/с), чтобы очень старый мир не обнулял шкалу мгновенно. */
    public static final int ADDICTION_DECAY_CAP = 10_000;

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
    /** Зависимость, с которой начинается кризис (проценты). */
    public static final int ADDICTION_CRISIS_MIN_PERCENT = 60;
    /** Выше этого процента зависимость кризис НЕ даёт (автор 22.09). */
    public static final int ADDICTION_CRISIS_MAX_PERCENT = 80;
    /** Потолок кризиса от зависимости: на зависимости до 100 % не дойти (автор 22.09). */
    public static final int ADDICTION_CRISIS_CAP_PERCENT = 60;
    /** Ставка кризиса за каждый выполненный порог. */
    public static final int CRISIS_PER_SECOND = 5;

    /** Период тика — раз в секунду, как у радиации. */
    private static final int PERIOD_TICKS = 20;
    /** Сколько тиков «сердечко» доски считается живым (сеть не мгновенна). */
    private static final long BOARD_PRESENCE_TTL = 60L;

    /** Когда в последний раз приходил пинг «я в интерфейсе доски» (gameTime). */
    private static final Map<UUID, Long> BOARD_SEEN = new HashMap<>();
    /** Накопитель дробного падения зависимости (тысячные). */
    private static final Map<UUID, Double> DECAY_ACC = new HashMap<>();

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

    /** Секундный пересчёт: зависимость (падение), стресс, кризис. */
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
        Tally tally = new Tally();
        applyBase(player, level, psyche, now, tally);

        int before = psyche.getStress();
        // «Зуд» (химия): +10 / +20 / +40 % к прибавкам стресса (автор 22.09.2026).
        int gains = (int) Math.round(tally.gains * PsycheChemical.stressMultiplier(player));
        int after = clamp(before + bonus(addiction, gains) - tally.losses);
        if (after != before) {
            psyche.setStress(after);
            save(player, psyche);
        }

        decayAddiction(player, psyche, now);
        trickleCrisis(psyche);
    }

    /** Недосып, зависимость, день/ночь, доска — «постоянная» часть шкалы. */
    private static void applyBase(ServerPlayer player, ServerLevel level, PlayerPsyche psyche,
                                  long now, Tally tally) {
        int addiction = psyche.getAddiction();
        long sinceSleep = now - psyche.getSleepTick();
        long sinceMash = now - psyche.getMashTick();
        boolean sleepDeprived = sinceSleep > SLEEP_WINDOW_TICKS;
        boolean craving = PlayerPsyche.pointsPercent(addiction) > CRAVING_ABOVE_PERCENT
                && sinceMash > cravingCorridor(addiction);

        if (sleepDeprived) {
            tally.gain(BASE_PER_SECOND);
        }
        if (craving && (CRAVING_STACKS_WITH_SLEEP || !sleepDeprived)) {
            tally.gain(BASE_PER_SECOND);
        }

        if (isDay(level)) {
            if (!sleepDeprived) {
                tally.lose(DAY_SLEPT_RELIEF);   // «днём если поспал» — снимаем
            }
        } else {
            tally.gain(NIGHT_PER_SECOND);
        }
        if (isAtBoard(player, now)) {
            tally.gain(BOARD_PER_SECOND);
        }

        applyEnvironment(player, level, tally);
    }

    /** Окружение: измерения, биомы, погода, свет, эффекты, здоровье, пластинки. */
    private static void applyEnvironment(ServerPlayer player, ServerLevel level, Tally tally) {
        if (level.dimension() == Level.NETHER || level.dimension() == Level.END) {
            tally.gain(NETHER_END_PER_SECOND);
        }
        if (SunEventServer.eventDayNow(level)) {
            tally.gain(SUN_EVENT_PER_SECOND);   // именно багровый день
        }

        // Голод и здоровье — ниже 20 %.
        if (player.getFoodData().getFoodLevel() < 4) {
            tally.gain(STARVING_PER_SECOND);
        }
        if (player.getHealth() < player.getMaxHealth() * 0.2F) {
            tally.gain(LOW_HEALTH_PER_SECOND);
        }
        // Вечный некроз давит постоянно.
        if (player.hasEffect(ModEffects.NECROSIS)) {
            tally.gain(NECROSIS_PER_SECOND);
        }

        BlockPos pos = player.blockPosition();
        if (level.getBiome(pos).is(Biomes.DEEP_DARK)) {
            tally.gain(DEEP_DARK_PER_SECOND);
        }
        if (level.getMaxLocalRawBrightness(pos) <= 1) {
            tally.gain(DARKNESS_PER_SECOND);    // полная темнота, свет 0–1
        }

        // Гроза на улице, а игрок под крышей у источника света: уютно.
        boolean storm = level.isThundering() || level.isRaining();
        if (storm && level.getBrightness(LightLayer.SKY, pos) == 0
                && level.getBrightness(LightLayer.BLOCK, pos) > 0) {
            tally.lose(SHELTER_RAIN_RELIEF);
        }

        // Положительные эффекты (не суммируются).
        if (player.hasEffect(MobEffects.DAMAGE_BOOST) || player.hasEffect(MobEffects.REGENERATION)
                || player.hasEffect(MobEffects.ABSORPTION) || player.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
            tally.lose(GOOD_EFFECTS_RELIEF);
        }
        // Лава под огнестойкостью — «как в спа».
        if (player.isInLava() && player.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            tally.lose(LAVA_SWIM_RELIEF);
        }
        // День, без брони, в вишнёвой роще или на грибном острове.
        if (isDay(level) && isBare(player)
                && (level.getBiome(pos).is(Biomes.CHERRY_GROVE)
                    || level.getBiome(pos).is(Biomes.MUSHROOM_FIELDS))) {
            tally.lose(SAFE_BIOME_RELIEF);
        }
        // Пластинка днём при полном голоде и здоровье.
        if (isDay(level) && player.getFoodData().getFoodLevel() >= 20
                && player.getHealth() >= player.getMaxHealth() && isJukeboxPlaying(level, pos)) {
            tally.lose(JUKEBOX_RELIEF);
        }
    }

    /** Кризис: стресс > 70 % (до 100 %) и зависимость 60–80 % (только до 60 %). */
    private static void trickleCrisis(PlayerPsyche psyche) {
        int crisisGain = 0;
        if (percent(psyche.getStress()) > STRESS_CRISIS_ABOVE_PERCENT) {
            crisisGain += CRISIS_PER_SECOND;
        }
        int addictionPercent = PlayerPsyche.pointsPercent(psyche.getAddiction());
        if (addictionPercent >= ADDICTION_CRISIS_MIN_PERCENT
                && addictionPercent <= ADDICTION_CRISIS_MAX_PERCENT
                && percent(psyche.getCrisis()) < ADDICTION_CRISIS_CAP_PERCENT) {
            crisisGain += CRISIS_PER_SECOND;
        }
        if (crisisGain > 0) {
            psyche.setCrisis(clamp(psyche.getCrisis() + crisisGain));
        }
    }

    /**
     * Падение зависимости: если сусло не пилось больше 20 игровых дней —
     * {@code 10 × (дней − 20)} тысячных в секунду (автор 22.09).
     *
     * <p>Зависимость считается в очках (0..1 000 000), поэтому и падение — в очках:
     * на 21-й день это 10 очков/с (0.001 %/с). Дробная часть копится.</p>
     */
    private static void decayAddiction(ServerPlayer player, PlayerPsyche psyche, long now) {
        int addiction = psyche.getAddiction();
        if (addiction <= 0) {
            DECAY_ACC.remove(player.getUUID());
            return;
        }
        long days = (now - psyche.getMashTick()) / 24_000L;
        if (days <= ADDICTION_DECAY_AFTER_DAYS) {
            return;
        }
        double perSecond = Math.min(ADDICTION_DECAY_CAP,
                (double) ADDICTION_DECAY_PER_DAY * (days - ADDICTION_DECAY_AFTER_DAYS));
        double acc = DECAY_ACC.getOrDefault(player.getUUID(), 0.0) + perSecond;
        int drop = (int) acc;
        DECAY_ACC.put(player.getUUID(), acc - drop);
        if (drop > 0) {
            psyche.setAddiction(addiction - drop);
            save(player, psyche);
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
     * Смерть игрока: зависимость и стресс чистятся на
     * {@value #DEATH_CLEAR_PERCENT} % ОТ ТЕКУЩЕГО значения, кризис персистентен и
     * наоборот получает +{@value #DEATH_CRISIS_BURST} (автор 22.09).
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setAddiction(psyche.getAddiction() - psyche.getAddiction() * DEATH_CLEAR_PERCENT / 100);
        psyche.setStress(psyche.getStress() - psyche.getStress() * DEATH_CLEAR_PERCENT / 100);
        psyche.setCrisis(clamp(psyche.getCrisis() + DEATH_CRISIS_BURST));
        DECAY_ACC.remove(player.getUUID());
        // Смерть чистит УФ полностью и заражение на 30 % от текущего (автор 22.09.2026).
        PsycheUltraviolet.clear(player);
        PsycheChemical.onDeath(player);
        save(player, psyche);
    }

    /**
     * Ручной триггер «смерти игрока» для админ-команды: −{@value #DEATH_CLEAR_PERCENT} % от
     * ТЕКУЩИХ зависимости и стресса, +{@value #DEATH_CRISIS_BURST} кризиса (кризис смертью не
     * чистится). Тело то же, что в {@link #onPlayerClone(PlayerEvent.Clone)}.
     */
    public static void onDebugDeath(ServerPlayer player) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setAddiction(psyche.getAddiction() - psyche.getAddiction() * DEATH_CLEAR_PERCENT / 100);
        psyche.setStress(psyche.getStress() - psyche.getStress() * DEATH_CLEAR_PERCENT / 100);
        psyche.setCrisis(clamp(psyche.getCrisis() + DEATH_CRISIS_BURST));
        DECAY_ACC.remove(player.getUUID());
        PsycheUltraviolet.clear(player);
        PsycheChemical.onDeath(player);
        save(player, psyche);
    }

    /**
     * Выпитое сусло: снимает {@value #MASH_RELIEF} стресса и сбрасывает коридор
     * зависимости (таймер сусла). Зависимость за сусло начисляет {@code Phase3Events}.
     */
    public static void onMashDrunk(ServerPlayer player) {
        relieve(player, MASH_RELIEF);
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setMashTick(player.serverLevel().getGameTime());
        save(player, psyche);
    }

    /**
     * Проюз «Открытия»: −{@value #DISCOVERY_RELIEF} стресса, +{@value #DISCOVERY_CRISIS} кризиса.
     */
    public static void onDiscoveryUsed(ServerPlayer player) {
        relieve(player, DISCOVERY_RELIEF);
        crisis(player, DISCOVERY_CRISIS);
    }

    /** Клиент говорит, что интерфейс доски резонанса открыт (сердечко раз в секунду). */
    public static void seenBoard(ServerPlayer player) {
        BOARD_SEEN.put(player.getUUID(), player.serverLevel().getGameTime());
    }

    /** Забыть игрока (выход) — карты не должны течь. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BOARD_SEEN.remove(player.getUUID());
            DECAY_ACC.remove(player.getUUID());
        }
    }

    // ═══════════════════════ API для источников ═══════════════════════

    /**
     * Разовое начисление стресса извне: прибавка идёт через бонус зависимости, как и всё
     * остальное. Готовый вызов для будущих источников (например, «преждевременный крафт» —
     * число за автором, см. TODO в {@code Phase3Events}).
     */
    public static void gain(ServerPlayer player, int points) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        // «Зуд» (химия): +10 / +20 / +40 % к прибавкам стресса (автор 22.09.2026).
        int withItch = (int) Math.round(points * PsycheChemical.stressMultiplier(player));
        psyche.setStress(clamp(psyche.getStress() + bonus(psyche.getAddiction(), withItch)));
        save(player, psyche);
    }

    /** Разовое снятие стресса извне — без бонуса зависимости (как и все снятия). */
    public static void relieve(ServerPlayer player, int points) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setStress(clamp(psyche.getStress() - points));
        save(player, psyche);
    }

    /** Разовое начисление кризиса извне (бонус зависимости на кризис не действует). */
    public static void crisis(ServerPlayer player, int points) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.setCrisis(clamp(psyche.getCrisis() + points));
        save(player, psyche);
    }

    /** Разовое начисление зависимости в очках (1000 очков = 0.1 %). */
    public static void addict(ServerPlayer player, int points) {
        PlayerPsyche psyche = player.getData(ModPsycheAttachments.PSYCHE);
        psyche.addAddiction(points);
        save(player, psyche);
    }

    // ═══════════════════════ математика ═══════════════════════

    /**
     * Бонус зависимости к прибавкам: 1 % зависимости = +1 % к получаемым очкам
     * (зависимость и прибавки — обе шкалы «в очках», делим на {@link PlayerPsyche#POINT_MAX}).
     */
    public static int bonus(int addictionPoints, int points) {
        return (int) Math.round(points * (1.0 + (double) addictionPoints / PlayerPsyche.POINT_MAX));
    }

    /** Сколько тиков без сусла терпит такая зависимость (при 50 % — 8000, при 100 % — 3000). */
    public static long cravingCorridor(int addictionPoints) {
        int percentAbove = Math.max(0, PlayerPsyche.pointsPercent(addictionPoints) - CRAVING_ABOVE_PERCENT);
        return Math.max(0, CRAVING_CORRIDOR_TICKS - (long) percentAbove * CRAVING_CORRIDOR_STEP);
    }

    /** Проценты шкалы «в очках» (1 000 000 = 100 %). */
    public static int percent(int points) {
        return points / (PlayerPsyche.POINT_MAX / 100);
    }

    /** Светло ли сейчас по игровому времени: 0–12000 тиков — день. */
    public static boolean isDay(ServerLevel level) {
        return level.getDayTime() % 24_000L < 12_000L;
    }

    /** Игрок совсем без брони (для «день без брони в безопасном биоме»). */
    private static boolean isBare(ServerPlayer player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }

    /**
     * Играет ли пластинка в проигрывателе поблизости. В 1.21.4 у {@link JukeboxBlockEntity} нет
     * {@code getRecord()} — «играет сейчас» спрашиваем у {@code JukeboxSongPlayer} (если автору
     * нужно «пластинка просто вставлена и доиграла», заменить на {@code jukebox.getTheItem()}).
     */
    private static boolean isJukeboxPlaying(ServerLevel level, BlockPos center) {
        BlockPos min = center.offset(-JUKEBOX_RADIUS, -4, -JUKEBOX_RADIUS);
        BlockPos max = center.offset(JUKEBOX_RADIUS, 4, JUKEBOX_RADIUS);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity jukebox
                    && jukebox.getSongPlayer().isPlaying()) {
                return true;
            }
        }
        return false;
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

    /** Пара накопителей за секунду: прибавки (с бонусом зависимости) и снятия (без). */
    private static final class Tally {
        private int gains;
        private int losses;

        void gain(int points) {
            gains += points;
        }

        void lose(int points) {
            losses += points;
        }
    }
}
