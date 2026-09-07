package com.gonzotech.core.psyche;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Персональные шкалы состояния игрока (пока в основном косметические):
 * <ul>
 *   <li><b>Зависимость</b> (addiction) — каждое съеденное сусло +0.1%;</li>
 *   <li><b>Стресс</b> (stress) — задел на будущее (пока 0);</li>
 *   <li><b>Экзистенциальный кризис</b> (crisis) — задел на будущее (пока 0);</li>
 *   <li><b>Облучение</b> (radiation) — задел, видно только с дозиметром в руке;</li>
 *   <li><b>УФ излучение</b> (uv) — задел, видно только с УФ-радиометром в руке;</li>
 *   <li><b>Химическое заражение</b> (chemical) — задел, видно всегда.</li>
 * </ul>
 *
 * <p>Значения хранятся в тысячных долях полной шкалы: {@code 0..1000}, где
 * {@code 1000 = 100%}. Один шаг ({@code +1}) = 0.1% — точный целочисленный
 * инкремент без накопления ошибок float.
 */
public class PlayerPsyche {

    /** Максимум шкалы (100%). */
    public static final int MAX = 1000;

    public static final Codec<PlayerPsyche> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("addiction", 0).forGetter(PlayerPsyche::getAddiction),
                    Codec.INT.optionalFieldOf("stress", 0).forGetter(PlayerPsyche::getStress),
                    Codec.INT.optionalFieldOf("crisis", 0).forGetter(PlayerPsyche::getCrisis),
                    Codec.INT.optionalFieldOf("radiation", 0).forGetter(PlayerPsyche::getRadiation),
                    Codec.INT.optionalFieldOf("uv", 0).forGetter(PlayerPsyche::getUv),
                    Codec.INT.optionalFieldOf("chemical", 0).forGetter(PlayerPsyche::getChemical)
            ).apply(instance, PlayerPsyche::new)
    );

    private int addiction;
    private int stress;
    private int crisis;
    private int radiation;
    private int uv;
    private int chemical;

    public PlayerPsyche() {
        this(0, 0, 0, 0, 0, 0);
    }

    public PlayerPsyche(int addiction, int stress, int crisis, int radiation, int uv, int chemical) {
        this.addiction = clamp(addiction);
        this.stress = clamp(stress);
        this.crisis = clamp(crisis);
        this.radiation = clamp(radiation);
        this.uv = clamp(uv);
        this.chemical = clamp(chemical);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(MAX, v));
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

    public void setAddiction(int v) {
        this.addiction = clamp(v);
    }

    public void setStress(int v) {
        this.stress = clamp(v);
    }

    public void setCrisis(int v) {
        this.crisis = clamp(v);
    }

    public void setRadiation(int v) {
        this.radiation = clamp(v);
    }

    public void setUv(int v) {
        this.uv = clamp(v);
    }

    public void setChemical(int v) {
        this.chemical = clamp(v);
    }

    /** Прибавить к зависимости {@code delta} тысячных (1 = 0.1%). */
    public void addAddiction(int delta) {
        setAddiction(this.addiction + delta);
    }
}
