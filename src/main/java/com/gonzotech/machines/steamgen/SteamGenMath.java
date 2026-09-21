package com.gonzotech.machines.steamgen;

import com.gonzotech.machines.energy.MachineDefs;

/**
 * Общие, детерминированные расчёты продвинутого парогенератора.
 *
 * <p>Вся логика параметров живёт здесь, а не размазывается между валидатором,
 * BlockEntity и GUI: клиент получает число ядер, сумму (C+H) и число
 * теплообменников и восстанавливает ровно те же ёмкости и темпы, что и сервер.</p>
 *
 * <h2>Формула</h2>
 * Цикл варки: {@code 15 mB воды + 11 GTH → 12 mB пара × M}, где
 * <pre>
 *   M        = 1 + E_avg × (1 + 0.1 × (n − 1))
 *   E_avg    = (C+H)_{avg} / 200        (средняя по n теплообменникам)
 * </pre>
 * Ядра задают базовый throughput: {@code 34 mB/т × M} на ядро (34 — предел
 * ДО модификатора; множитель теплообменников умножает и потолок). Теплообменники
 * лишь повышают эффективность преобразования: на единицу пара тратится
 * 15/(12M) mB воды и 11/(12M) GTH.
 */
public final class SteamGenMath {

    private SteamGenMath() {
    }

    /**
     * Множитель теплообменников {@code M = 1 + E_avg·(1 + 0.1·(n−1))}.
     *
     * @param sumCH сумма (C+H) по всем теплообменникам
     * @param count число теплообменников (0 → множитель ровно 1)
     */
    public static double multiplier(int sumCH, int count) {
        if (count <= 0 || sumCH <= 0) return 1.0D;
        double eAvg = sumCH / (MachineDefs.STEAMGEN_EXCHANGER_DIVISOR * (double) count);
        double growth = 1.0D
            + (count - 1) / (double) MachineDefs.STEAMGEN_EXCHANGER_STEP;
        return 1.0D + eAvg * growth;
    }

    /** Бонус теплообменников над базой, доля (0.75 = +75%). */
    public static double bonusFraction(int sumCH, int count) {
        return multiplier(sumCH, count) - 1.0D;
    }

    /** Буфер воды, mB: 488 на ядро. */
    public static int waterCapacity(int cores) {
        return cores * MachineDefs.STEAMGEN_WATER_CAPACITY_PER_CORE;
    }

    /** Буфер пара, mB: 1526 на ядро. */
    public static int steamCapacity(int cores) {
        return cores * MachineDefs.STEAMGEN_STEAM_CAPACITY_PER_CORE;
    }

    /** Буфер GTH, milli: 4096 GTH на ядро. */
    public static long gthCapacityMilli(int cores) {
        return (long) cores * MachineDefs.STEAMGEN_GTH_CAPACITY_PER_CORE * MachineDefs.MILLI;
    }

    /** GTH-ёмкость в целых GTH (для шкалы GUI). */
    public static int gthCapacityUnits(int cores) {
        return cores * MachineDefs.STEAMGEN_GTH_CAPACITY_PER_CORE;
    }

    /**
     * Потолок выработки пара, mB/т: {@code 34 × ядра × M}. Это лимит ПОСЛЕ
     * множителя (34 — базовый throughput ядра, ДО модификатора).
     */
    public static double maxSteamPerTick(int cores, int sumCH, int count) {
        if (cores <= 0) return 0.0D;
        return cores * MachineDefs.STEAMGEN_STEAM_PER_TICK_PER_CORE * multiplier(sumCH, count);
    }

    /** Номинальный потолок выработки, milli-mB/т (для тултипов). */
    public static long maxSteamPerTickMilli(int cores, int sumCH, int count) {
        return (long) Math.floor(maxSteamPerTick(cores, sumCH, count) * MachineDefs.MILLI);
    }

    /** Пропускная способность жидкостных входов/выходов, mB/т: 128 на ядро. */
    public static int fluidIOLimit(int cores) {
        return cores * MachineDefs.STEAMGEN_FLUID_IO_PER_CORE;
    }

    /**
     * Сколько целых циклов варки даёт доступная вода, без перерасхода:
     * floor(вода / 15).
     */
    public static long unitsFromWater(long waterMb) {
        return waterMb / MachineDefs.STEAMGEN_WATER_PER_UNIT;
    }

    /** Сколько целых циклов варки даёт доступный GTH: floor(milli / 11000). */
    public static long unitsFromGth(long gthMilli) {
        return gthMilli / MachineDefs.STEAMGEN_GTH_PER_UNIT_MILLI;
    }
}
