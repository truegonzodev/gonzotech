package com.gonzotech.machines.network;

/**
 * Потери энергии за блок проноса (автор 28.09.2026, 0.3.58). Провод/узел
 * провода I теряют 0.08 GTU за блок, теплотруба/узел теплотрубы I — 0.22 GTH;
 * тир II — 0.09 GTU и 0.18 GTH соответственно. Универсальный узел потерь НЕ
 * имеет (осознанный «легальный эксплойт» автора: узел дорог и уже понёрфлен
 * коэффициентом пропускной способности 0.9). Жидкости и предметы не теряются.
 *
 * <p>Все значения во внутренней milli-точности (1 GTU = 1000 milli). Класс
 * сознательно без зависимостей (как {@link CleanerPulse} в своём слое):
 * компилируется и тестируется вне Minecraft (audit/pipe_loss_test.py).</p>
 */
public final class PipeLoss {

    /** Провод/узел провода тир I: 0.08 GTU за блок (80 milli). */
    public static final long WIRE_T1 = 80;
    /** Провод/узел провода тир II: 0.09 GTU за блок (90 milli). */
    public static final long WIRE_T2 = 90;
    /** Теплотруба/узел тир I: 0.22 GTH за блок (220 milli). */
    public static final long HEAT_T1 = 220;
    /** Теплотруба/узел тир II: 0.18 GTH за блок (180 milli). */
    public static final long HEAT_T2 = 180;

    private PipeLoss() {
    }

    /** Потеря одной клетки-носителя: тир II и признак теплотрубы (milli единиц). */
    public static long perCell(boolean secondTier, boolean heat) {
        if (heat) return secondTier ? HEAT_T2 : HEAT_T1;
        return secondTier ? WIRE_T2 : WIRE_T1;
    }

    /** Суммарная потеря маршрута по клеткам (milli). */
    public static long sum(long[] perCell) {
        long total = 0;
        for (long v : perCell) total += v;
        return total;
    }

    /** Сколько из предложенного доедет до приёмника. */
    public static long delivered(long offered, long loss) {
        return Math.max(0, offered - loss);
    }

    /**
     * Фактический пронос через клетки маршрута: принятое + потеря. Если приёмник
     * не взял ничего — поток нулевой: трубы не платят и потерь не несут.
     */
    public static long flow(long accepted, long loss) {
        return accepted <= 0 ? 0 : accepted + loss;
    }
}
