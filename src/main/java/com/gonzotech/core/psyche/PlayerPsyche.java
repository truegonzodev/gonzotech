package com.gonzotech.core.psyche;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Персональные шкалы состояния игрока. Две системы измерения — намеренно:
 * <ul>
 *   <li><b>очки</b> ({@code 0..1_000_000}, {@link #POINT_MAX} = 100 %) —
 *       Зависимость, Стресс и Экзистенциальный кризис: автор 22.09.2026 задал
 *       ставки этих шкал в очках («0.001 % шкалы в сек = 10 очков», тотем
 *       «+2000 = 0.2 % зависимости»), поэтому и хранение в очках, чтобы 1 очко
 *       не терялось в округлении;</li>
 *   <li><b>проценты</b> ({@code 0..1000} тысячных, {@link #MAX} = 100 %) —
 *       Облучение, УФ и Химическое заражение (там числа заданы в mZt/процентах).</li>
 * </ul>
 *
 * <p>Перевод: 1 % шкалы в очках = 10 000 очков, 1 тысячная = 1 000 очков
 * (автор 24.09 спрашивал про конверсию «10000 очков = 1 %» — она верная).
 * В HUD очки показываются в тысячных — {@link #pointsToPermille(int)}.</p>
 *
 * <p>Таймеры источников стресса и скрытые таймеры препаратов живут здесь же
 * (переживают выход и смерть): {@link #getSleepTick()} — когда игрок последний раз
 * спал, {@link #getMashTick()} — когда последний раз пил сусло,
 * {@link #getPentacinOverdoseUntil()} — скрытое окно передозировки Пентацином
 * (120 с, спека 24.09), {@link #getDtpaCourseCount()} — скрытый счётчик доз
 * ДТПА («ДТПА принято кол-во», шестая доза завершает курс).</p>
 *
 * <ul>
 *   <li><b>Зависимость</b> (addiction) — ЖИВАЯ, в очках: каждое съеденное сусло
 *       +0.1 % = +1000 очков ({@code Phase3Events}); падением и смертью управляет
 *       {@link PsycheStress};</li>
 *   <li><b>Облучение</b> (radiation) — ЖИВАЯ: дозу пишет {@code RadiationSystem},
 *       шкала видна в HUD только с дозиметром в руке;</li>
 *   <li><b>Стресс</b> (stress) — ЖИВАЯ: копится по спеке 22.09.2026,
 *       источник истины — {@link PsycheStress};</li>
 *   <li><b>Экзистенциальный кризис</b> (crisis) — ЖИВОЙ: капает при стрессе
 *       &gt; 70 % и зависимости &gt; 60 % ({@link PsycheStress}); эффекты — позже;</li>
 *   <li><b>УФ излучение</b> (uv) — ЗАДЕЛ под УФ-механику (пока 0); шкала видна
 *       с УФ-радиометром;</li>
 *   <li><b>Химическое заражение</b> (chemical) — ЖИВАЯ (зуд и бонусы дозы),
 *       шкала видна всегда.</li>
 * </ul>
 */
public class PlayerPsyche {

    /** Максимум «процентных» шкал: 1000 тысячных = 100 %. */
    public static final int MAX = 1000;
    /** Максимум шкал «в очках» (зависимость, стресс, кризис): 1 000 000 = 100 % (автор 22.09). */
    public static final int POINT_MAX = 1_000_000;
    /** Сколько очков в одном проценте любой «очковой» шкалы. */
    public static final int POINTS_PER_PERCENT = POINT_MAX / 100;

    public static final Codec<PlayerPsyche> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("addiction", 0).forGetter(PlayerPsyche::getAddiction),
                    Codec.INT.optionalFieldOf("stress", 0).forGetter(PlayerPsyche::getStress),
                    Codec.INT.optionalFieldOf("crisis", 0).forGetter(PlayerPsyche::getCrisis),
                    Codec.INT.optionalFieldOf("radiation", 0).forGetter(PlayerPsyche::getRadiation),
                    Codec.INT.optionalFieldOf("uv", 0).forGetter(PlayerPsyche::getUv),
                    Codec.INT.optionalFieldOf("chemical", 0).forGetter(PlayerPsyche::getChemical),
                    Codec.LONG.optionalFieldOf("sleep_tick", 0L).forGetter(PlayerPsyche::getSleepTick),
                    Codec.LONG.optionalFieldOf("mash_tick", 0L).forGetter(PlayerPsyche::getMashTick),
                    Codec.INT.optionalFieldOf("dtpa_course_count", 0).forGetter(PlayerPsyche::getDtpaCourseCount),
                    Codec.LONG.optionalFieldOf("pentacin_overdose_until", 0L).forGetter(PlayerPsyche::getPentacinOverdoseUntil)
            ).apply(instance, PlayerPsyche::new)
    );

    private int addiction;
    private int stress;
    private int crisis;
    private int radiation;
    private int uv;
    private int chemical;
    /** Игровое время последнего сна (для «не спишь больше 24000 тиков»). */
    private long sleepTick;
    /** Игровое время последнего выпитого сусла (для «коридора зависимости»). */
    private long mashTick;
    /** Скрытый счётчик «ДТПА принято кол-во» (спека 24.09; 0..5, шестая доза сбрасывает). */
    private int dtpaCourseCount;
    /** Игровое время окончания скрытого окна передозировки Пентацином (спека 24.09). */
    private long pentacinOverdoseUntil;

    public PlayerPsyche() {
        this(0, 0, 0, 0, 0, 0, 0L, 0L, 0, 0L);
    }

    public PlayerPsyche(int addiction, int stress, int crisis, int radiation, int uv, int chemical) {
        this(addiction, stress, crisis, radiation, uv, chemical, 0L, 0L, 0, 0L);
    }

    public PlayerPsyche(int addiction, int stress, int crisis, int radiation, int uv, int chemical,
                        long sleepTick, long mashTick) {
        this(addiction, stress, crisis, radiation, uv, chemical, sleepTick, mashTick, 0, 0L);
    }

    public PlayerPsyche(int addiction, int stress, int crisis, int radiation, int uv, int chemical,
                        long sleepTick, long mashTick, int dtpaCourseCount, long pentacinOverdoseUntil) {
        this.addiction = clampPoints(addiction);
        this.stress = clampPoints(stress);
        this.crisis = clampPoints(crisis);
        this.radiation = clampPercent(radiation);
        this.uv = clampPercent(uv);
        this.chemical = clampPercent(chemical);
        this.sleepTick = sleepTick;
        this.mashTick = mashTick;
        this.dtpaCourseCount = Math.max(0, dtpaCourseCount);
        this.pentacinOverdoseUntil = pentacinOverdoseUntil;
    }

    private static int clampPercent(int v) {
        return Math.max(0, Math.min(MAX, v));
    }

    private static int clampPoints(int v) {
        return Math.max(0, Math.min(POINT_MAX, v));
    }

    public int getAddiction() {
        return addiction;
    }

    public int getStress() {
        return stress;
    }

    public int getCrisis() {
        return crisis;
    }

    public int getRadiation() {
        return radiation;
    }

    public int getUv() {
        return uv;
    }

    public int getChemical() {
        return chemical;
    }

    public long getSleepTick() {
        return sleepTick;
    }

    public long getMashTick() {
        return mashTick;
    }

    public int getDtpaCourseCount() {
        return dtpaCourseCount;
    }

    public long getPentacinOverdoseUntil() {
        return pentacinOverdoseUntil;
    }

    /** Зависимость — в очках ({@link #POINT_MAX} = 100 %). */
    public void setAddiction(int v) {
        this.addiction = clampPoints(v);
    }

    /** Стресс — в очках ({@link #POINT_MAX} = 100 %). */
    public void setStress(int v) {
        this.stress = clampPoints(v);
    }

    /** Кризис — в очках ({@link #POINT_MAX} = 100 %). */
    public void setCrisis(int v) {
        this.crisis = clampPoints(v);
    }

    public void setRadiation(int v) {
        this.radiation = clampPercent(v);
    }

    public void setUv(int v) {
        this.uv = clampPercent(v);
    }

    public void setChemical(int v) {
        this.chemical = clampPercent(v);
    }

    public void setSleepTick(long v) {
        this.sleepTick = v;
    }

    public void setMashTick(long v) {
        this.mashTick = v;
    }

    public void setDtpaCourseCount(int v) {
        this.dtpaCourseCount = Math.max(0, v);
    }

    public void setPentacinOverdoseUntil(long v) {
        this.pentacinOverdoseUntil = v;
    }

    /** Прибавить к зависимости {@code delta} очков (1000 очков = 0.1 %). */
    public void addAddiction(int delta) {
        setAddiction(this.addiction + delta);
    }

    /** Проценты «очковой» шкалы (1 000 000 = 100 %). */
    public static int pointsPercent(int points) {
        return points / POINTS_PER_PERCENT;
    }

    /** Очки → тысячные: для HUD, который рисует все шкалы в одной сетке 0..1000. */
    public static int pointsToPermille(int points) {
        return points / (POINT_MAX / MAX);
    }
}
