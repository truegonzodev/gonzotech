package com.gonzotech.machines.energy;

import java.math.BigInteger;

/**
 * Форматирование больших величин GTU/GTH для показа игроку.
 *
 * <p>Значения хранятся в милли (см. {@link GtBuffer}); здесь мы переводим их в
 * ЦЕЛЫЕ единицы и печатаем с суффиксом тысячных тиров:
 * <pre>
 *   K  = 10^3   тысяча          Qi = 10^18  квинтиллион
 *   M  = 10^6   миллион         Sx = 10^21  секстиллион
 *   B  = 10^9   миллиард        Sp = 10^24  септиллион
 *   T  = 10^12  триллион        Oc = 10^27  октиллион
 *   Qa = 10^15  квадриллион     No = 10^30  нониллион
 * </pre>
 * Ниже 1000 единиц печатаем точное целое (пар/атом читаются как есть). От 1000
 * и выше — «12.3M», «4.5B», «7.8T», … (одна десятая, суффикс). Суффиксы —
 * латиница, международно узнаваемы и не требуют перевода.
 *
 * <h2>Синк в GUI</h2>
 * {@code ContainerData} шлёт значения как {@code short} (макс 32767), поэтому
 * большое число нельзя передать «числом». Метод {@link #packMantissa} кодирует
 * величину в компактную мантиссу [0..9999] + тир, а {@link #unpackUnits}
 * восстанавливает приблизительную величину на клиенте для показа. Точность —
 * 4 значащих цифры, чего для шкалы/подписи достаточно.
 */
public final class GtFormat {

    private GtFormat() {
    }

    private static final String[] SUFFIX = {
        "", "K", "M", "B", "T", "Qa", "Qi", "Sx", "Sp", "Oc", "No", "Dc"
    };

    private static final BigInteger THOUSAND = BigInteger.valueOf(1000);

    /** Милли → строка единиц с суффиксом (напр. 12_345_678_000 → «12.3M»). */
    public static String format(BigInteger milli) {
        if (milli == null || milli.signum() <= 0) return "0";
        BigInteger units = milli.divide(BigInteger.valueOf(MachineDefs.MILLI));
        return formatUnits(units);
    }

    /** Целые единицы → строка с суффиксом. */
    public static String formatUnits(BigInteger units) {
        if (units == null || units.signum() <= 0) return "0";
        if (units.compareTo(THOUSAND) < 0) {
            return units.toString(); // < 1000 — точное целое
        }
        // Найти тир: сколько раз делится на 1000.
        int tier = 0;
        BigInteger v = units;
        while (v.compareTo(THOUSAND) >= 0 && tier < SUFFIX.length - 1) {
            v = v.divide(THOUSAND);
            tier++;
        }
        // Одна десятая: берём units / 1000^(tier-1), делим на 100 → сотни, /10.
        BigInteger scale = THOUSAND.pow(tier);
        // целая часть в этом тире
        BigInteger whole = units.divide(scale);
        // десятая: (units - whole*scale) * 10 / scale
        BigInteger rem = units.subtract(whole.multiply(scale));
        int tenths = rem.multiply(BigInteger.TEN).divide(scale).intValueExact();
        String suffix = SUFFIX[tier];
        return tenths == 0
            ? whole + suffix
            : whole + "." + tenths + suffix;
    }

    /**
     * Милли → строка ПОТОКА за тик. Малый поток (&lt; 1000 единиц) печатается с
     * одной десятой прямо из милли (2800 mGTU → «2.8», 10200 → «10.2»), потому
     * что для скоростей паровой эры десятые значимы. От 1000 единиц — как
     * {@link #formatUnits} с суффиксом (12.3M) — там десятые уже неразличимы.
     */
    public static String formatRate(long milli) {
        if (milli <= 0) return "0";
        long units = milli / MachineDefs.MILLI;
        if (units < 1000) {
            long whole = milli / MachineDefs.MILLI;
            long tenths = (milli % MachineDefs.MILLI) / 100;
            return tenths == 0 ? Long.toString(whole) : whole + "." + tenths;
        }
        return formatUnits(BigInteger.valueOf(units));
    }

    /** Милли → строка «единицы/ёмкость» для тултипа шкалы (обе с суффиксами). */
    public static String formatFraction(BigInteger milliAmount, BigInteger milliCapacity) {
        return format(milliAmount) + " / " + format(milliCapacity);
    }

    // ─────────────────────── упаковка для ContainerData (short) ───────────────────────
    //
    // Кодируем ЕДИНИЦЫ как мантиссу [0..9999] (4 значащих цифры) + десятичный
    // порядок exp, так что {@code units ≈ mantissa × 10^exp}. Оба влезают в short;
    // передаём двумя слотами ContainerData. 4 значащих цифры → «12.34M» точности
    // хватает и на шкалу, и на подпись.

    /** Мантисса [0..9999] величины в единицах (4 значащих цифры). */
    public static int packMantissa(BigInteger milli) {
        BigInteger units = milli.divide(BigInteger.valueOf(MachineDefs.MILLI));
        if (units.signum() <= 0) return 0;
        BigInteger v = units;
        while (v.compareTo(BigInteger.valueOf(10000)) >= 0) {
            v = v.divide(BigInteger.TEN);
        }
        return v.intValueExact(); // 0..9999
    }

    /** Десятичный порядок exp для {@link #packMantissa} ({@code units ≈ mantissa·10^exp}). */
    public static int packExp(BigInteger milli) {
        BigInteger units = milli.divide(BigInteger.valueOf(MachineDefs.MILLI));
        if (units.signum() <= 0) return 0;
        int exp = 0;
        BigInteger v = units;
        while (v.compareTo(BigInteger.valueOf(10000)) >= 0) {
            v = v.divide(BigInteger.TEN);
            exp++;
        }
        return exp;
    }

    /** Обратно: (мантисса, exp) → приблизительная величина в ЕДИНИЦАХ. */
    public static BigInteger unpackUnits(int mantissa, int exp) {
        if (mantissa <= 0) return BigInteger.ZERO;
        return BigInteger.valueOf(mantissa).multiply(BigInteger.TEN.pow(Math.max(0, exp)));
    }

    /** (мантисса, exp) → готовая строка для GUI. */
    public static String formatPacked(int mantissa, int exp) {
        return formatUnits(unpackUnits(mantissa, exp));
    }
}
