package com.gonzotech.machines.steamgen;

import com.gonzotech.machines.energy.MachineDefs;

/**
 * Общие детерминированные расчёты продвинутого парогенератора.
 *
 * <p>Платиновая кривая задаётся контрольными конфигурациями по числу
 * теплообменников; промежуточные значения линейно интерполируются. Состав
 * теплообменников влияет только на выход пара: затраты воды и GTH определяются
 * числом обменников и остаются теми же, что у платины. Это делает менее
 * эффективные материалы действительно дороже на единицу полученного пара.</p>
 */
public final class SteamGenMath {

    private static final int[] CURVE_EXCHANGERS = {0, 2, 5, 12, 22, 26};
    /** Платиновый расход GTH на цикл, в milli-GTH; цикл выполняется максимум раз/т. */
    private static final long[] PLATINUM_GTH_PER_CYCLE_MILLI = {
        1_273_000L, 1_388_000L, 1_503_000L, 1_081_000L, 698_000L, 429_000L
    };
    /** Платиновый расход воды на цикл, mB. */
    private static final long[] PLATINUM_WATER_PER_CYCLE = {863, 911, 921, 719, 498, 233};
    /** Платиновый выход пара на цикл, в milli-mB; цикл выполняется максимум раз/т. */
    private static final long[] PLATINUM_STEAM_PER_CYCLE_MILLI = {
        319_000L, 384_000L, 466_000L, 353_000L, 237_000L, 156_000L
    };

    private SteamGenMath() {
    }

    /**
     * Эффективность материала в permille относительно платины.
     * Gold (C+H=125) намеренно даёт 70%, Redstone (95) — 50%; между точками
     * плавная линейная кривая. На низком качестве установлен пол 10%, чтобы
     * любой теплообменник сохранял ненулевой полезный эффект.
     *
     * @param sumCH сумма C+H всех установленных теплообменников
     * @param count число теплообменников; без обменников базовый профиль равен 100%
     */
    public static int exchangerEfficiencyPermille(int sumCH, int count) {
        if (count <= 0) return 1_000;
        double average = Math.max(0.0D, sumCH / (double) count);
        double efficiency;
        if (average >= 125.0D) {
            efficiency = 0.70D + (average - 125.0D) / 150.0D * 1.80D;
        } else {
            efficiency = 0.50D + (average - 95.0D) / 150.0D;
        }
        efficiency = Math.max(0.10D, Math.min(1.0D, efficiency));
        return (int) Math.round(efficiency * 1_000.0D);
    }

    /** Доля выработки от платиновой, диапазон 0.10..1.00. */
    public static double exchangerEfficiency(int sumCH, int count) {
        return exchangerEfficiencyPermille(sumCH, count) / 1_000.0D;
    }

    /**
     * Фактическая эффективность преобразования GTH в пар в процентах от
     * профиля той же сборки с ядрами, но без теплообменников (эталон = 100%).
     * Учитывает фактические GTH-расход и выход пара для текущего числа обменников,
     * а также их материал; это не доля платинового выхода.
     */
    public static double steamPerGthPercentFromCoreOnly(int cores, int sumCH, int exchangers) {
        if (cores <= 0) return 0.0D;

        long baselineGthMilli = gthPerCycleMilli(cores, 0);
        long baselineSteamMilli = steamPerCycleMilli(cores, 0, 0);
        long currentGthMilli = gthPerCycleMilli(cores, exchangers);
        long currentSteamMilli = steamPerCycleMilli(cores, sumCH, exchangers);
        if (baselineGthMilli <= 0L || baselineSteamMilli <= 0L
                || currentGthMilli <= 0L || currentSteamMilli <= 0L) {
            return 0.0D;
        }

        double baselineSteamPerGth = baselineSteamMilli / (double) baselineGthMilli;
        double currentSteamPerGth = currentSteamMilli / (double) currentGthMilli;
        return currentSteamPerGth / baselineSteamPerGth * 100.0D;
    }

    /** Буфер воды, mB: 256 на ядро. */
    public static int waterCapacity(int cores) {
        return Math.max(0, cores) * MachineDefs.STEAMGEN_WATER_CAPACITY_PER_CORE;
    }

    /** Буфер пара, mB: 512 на ядро. */
    public static int steamCapacity(int cores) {
        return Math.max(0, cores) * MachineDefs.STEAMGEN_STEAM_CAPACITY_PER_CORE;
    }

    /** Буфер GTH, milli: 1536 GTH на ядро. */
    public static long gthCapacityMilli(int cores) {
        return (long) Math.max(0, cores) * MachineDefs.STEAMGEN_GTH_CAPACITY_PER_CORE * MachineDefs.MILLI;
    }

    /** GTH-ёмкость в целых GTH (для шкалы GUI). */
    public static int gthCapacityUnits(int cores) {
        return Math.max(0, cores) * MachineDefs.STEAMGEN_GTH_CAPACITY_PER_CORE;
    }

    /** Затрата GTH за один цикл варки, milli-GTH. */
    public static long gthPerCycleMilli(int cores, int exchangers) {
        if (cores <= 0) return 0L;
        return interpolate(PLATINUM_GTH_PER_CYCLE_MILLI, exchangers);
    }

    /** Затрата воды за один цикл варки, mB. */
    public static int waterPerCycle(int cores, int exchangers) {
        if (cores <= 0) return 0;
        return (int) interpolate(PLATINUM_WATER_PER_CYCLE, exchangers);
    }

    /** Платиновый выход одного цикла, milli-mB. */
    public static long platinumSteamPerCycleMilli(int cores, int exchangers) {
        if (cores <= 0) return 0L;
        return interpolate(PLATINUM_STEAM_PER_CYCLE_MILLI, exchangers);
    }

    /** Реальный выход одного цикла для заданного среднего C+H, milli-mB. */
    public static long steamPerCycleMilli(int cores, int sumCH, int exchangers) {
        long platinumOutput = platinumSteamPerCycleMilli(cores, exchangers);
        return Math.round(platinumOutput * (double) exchangerEfficiencyPermille(sumCH, exchangers) / 1_000.0D);
    }

    /** Номинальная трата GTH на 1 mB пара с точностью, пригодной для GUI. */
    public static double nominalGthPerSteamMb(int cores, int sumCH, int exchangers) {
        long outputMilli = steamPerCycleMilli(cores, sumCH, exchangers);
        return outputMilli <= 0L ? 0.0D
            : (gthPerCycleMilli(cores, exchangers) / (double) MachineDefs.MILLI)
                / (outputMilli / (double) MachineDefs.MILLI);
    }

    /** Номинальная трата воды на 1 mB пара с точностью, пригодной для GUI. */
    public static double nominalWaterPerSteamMb(int cores, int sumCH, int exchangers) {
        long outputMilli = steamPerCycleMilli(cores, sumCH, exchangers);
        return outputMilli <= 0L ? 0.0D
            : waterPerCycle(cores, exchangers)
                / (outputMilli / (double) MachineDefs.MILLI);
    }

    /** Номинальный поток для текущего материала, mB/t (цикл не чаще раза за тик). */
    public static double maxSteamPerTick(int cores, int sumCH, int exchangers) {
        return maxSteamBurstPerTickMilli(cores, sumCH, exchangers) / (double) MachineDefs.MILLI;
    }

    /** Номинальный поток пара, milli-mB/t. */
    public static long maxSteamPerTickMilli(int cores, int sumCH, int exchangers) {
        return maxSteamBurstPerTickMilli(cores, sumCH, exchangers);
    }

    /**
     * Максимальный целый выпуск за тик с учётом достижимого дробного остатка.
     * На платиновых контрольных точках он в точности равен заданному профилю;
     * у менее эффективных материалов округление вверх возможно не чаще чем
     * при переносе накопленной доли mB между последовательными циклами.
     */
    public static long maxSteamBurstPerTickMilli(int cores, int sumCH, int exchangers) {
        long outputMilli = steamPerCycleMilli(cores, sumCH, exchangers);
        return ((outputMilli + MachineDefs.MILLI - 1L) / MachineDefs.MILLI) * MachineDefs.MILLI;
    }

    /** Пропускная способность жидкостных входов/выходов, mB/т. */
    public static int fluidIOLimit(int cores) {
        if (cores <= 0) return 0;
        return Math.max(MachineDefs.STEAMGEN_FLUID_IO_MINIMUM,
            cores * MachineDefs.STEAMGEN_FLUID_IO_PER_CORE);
    }

    private static long interpolate(long[] values, int exchangers) {
        int count = Math.max(0, Math.min(MachineDefs.STEAMGEN_MAX_EXCHANGERS, exchangers));
        for (int i = 1; i < CURVE_EXCHANGERS.length; i++) {
            int right = CURVE_EXCHANGERS[i];
            if (count <= right) {
                int left = CURVE_EXCHANGERS[i - 1];
                long span = right - left;
                long offset = count - left;
                long numerator = values[i - 1] * (span - offset) + values[i] * offset;
                return (numerator + span / 2L) / span;
            }
        }
        return values[values.length - 1];
    }
}
