package com.gonzotech.radiation;

import java.util.Locale;

/**
 * Единицы радиации мода: базовая — <b>nZt</b> (нано-Zt), приставки по ×1000:
 * nZt → µZt → mZt → Zt → kZt → MZt → GZt.
 *
 * <p>Автор (20.09, п.7): «все цифры через движок как /в сек, так понятнее и
 * легче для вычислений» — ВСЕ внутренние значения = эмиссия в nZt<b>/с</b>.
 * Суффикс в выводе — «/t» (флейворная подпись из спеки автора: «30mZt/t»).</p>
 */
public final class RadUnits {

    public static final double MICRO = 1_000.0;              // µZt
    public static final double MILLI = 1_000_000.0;          // mZt
    public static final double UNIT  = 1_000_000_000.0;      // Zt
    public static final double KILO  = 1_000_000_000_000.0;  // kZt
    public static final double MEGA  = 1_000_000_000_000_000.0; // MZt
    public static final double GIGA  = 1_000_000_000_000_000_000.0; // GZt

    private static final String[] PREFIXES = { "nZt", "µZt", "mZt", "Zt", "kZt", "MZt", "GZt" };

    private RadUnits() {
    }

    /**
     * Формат «12.34mZt/t»: подбираем приставку, чтобы мантисса была [1, 1000),
     * знаков — до 3 значимых (старшие цифры важнее хвоста).
     */
    public static String format(double nZt) {
        if (nZt < 0.0) {
            nZt = 0.0;
        }
        int idx = 0;
        double v = nZt;
        while (v >= 1000.0 && idx < PREFIXES.length - 1) {
            v /= 1000.0;
            idx++;
        }
        return trim(v) + PREFIXES[idx] + "/t";
    }

    /** 3 значимые цифры, без хвостовых нулей: 4.2 / 34.5 / 120. */
    private static String trim(double v) {
        String s;
        if (v >= 99.95) {
            s = String.format(Locale.ROOT, "%.0f", v);
        } else if (v >= 9.995) {
            s = String.format(Locale.ROOT, "%.1f", v);
        } else {
            s = String.format(Locale.ROOT, "%.2f", v);
        }
        if (s.contains(".")) {
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        return s;
    }
}
