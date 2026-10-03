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
 * Цикл варки (нёрф конверсии автора 02.10, было {@code 16 W + 12 GTH → 12 пара}):
 * {@code 1.14 mB воды + 1.93 GTH → 0.44 mB пара × M}, где
 * <pre>
 *   M        = 1 + E_avg × (1 + 0.1 × (n − 1))
 *   E_avg    = (C+H)_{avg} / 200 × 0.93  (средняя по n теплообменникам, -7% с 0.3.64)
 * </pre>
 * Ядра задают базовый throughput: {@code 34 mB/т × M} на ядро (34 — предел
 * ДО модификатора; множитель теплообменников умножает и потолок). Теплообменники
 * лишь повышают эффективность преобразования: на единицу пара тратится
 * 114/(44M) mB воды и 193/(44M) GTH.
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
        double eAvg = (sumCH / (MachineDefs.STEAMGEN_EXCHANGER_DIVISOR * (double) count))
            * MachineDefs.STEAMGEN_EXCHANGER_EFFICIENCY; // 0.3.64: -7% к E обменников
        double growth = 1.0D
            + (count - 1) / (double) MachineDefs.STEAMGEN_EXCHANGER_STEP;
        return 1.0D + eAvg * growth;
    }

    /** Бонус теплообменников над базой, доля (0.75 = +75%). */
    public static double bonusFraction(int sumCH, int count) {
        return multiplier(sumCH, count) - 1.0D;
    }

    /** Номинальная трата GTH на 1 mB пара: {@code GTH_за_цикл / (44 × M)}. */
    public static double nominalGthPerSteamMb(int sumCH, int count) {
        double steamPerEvent = MachineDefs.STEAMGEN_STEAM_PER_UNIT * multiplier(sumCH, count);
        return (MachineDefs.STEAMGEN_GTH_PER_UNIT_MILLI / (double) MachineDefs.MILLI) / steamPerEvent;
    }

    /** Номинальная трата воды на 1 mB пара: {@code вода_за_цикл / (44 × M)}. */
    public static double nominalWaterPerSteamMb(int sumCH, int count) {
        double steamPerEvent = MachineDefs.STEAMGEN_STEAM_PER_UNIT * multiplier(sumCH, count);
        return MachineDefs.STEAMGEN_WATER_PER_UNIT / steamPerEvent;
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
     * Средний потолок устойчивой выработки пара, mB/t: {@code 34 × ядра × M}.
     * Это лимит ПОСЛЕ множителя (34 — базовый throughput ядра, ДО модификатора);
     * разовый максимум за тик с остатками считает {@link #maxSteamBurstPerTickMilli}.
     */
    public static double maxSteamPerTick(int cores, int sumCH, int count) {
        if (cores <= 0) return 0.0D;
        return cores * MachineDefs.STEAMGEN_STEAM_PER_TICK_PER_CORE * multiplier(sumCH, count);
    }

    /** Средний потолок устойчивой выработки, milli-mB/t. */
    public static long maxSteamPerTickMilli(int cores, int sumCH, int count) {
        return (long) Math.floor(maxSteamPerTick(cores, sumCH, count) * MachineDefs.MILLI);
    }

    /**
     * Максимум целых mB, реально выпускаемых за один тик при изобилии воды/GTH
     * и свободном баке. burnSteam накапливает дробные циклы, поэтому отдельный
     * тик может получить ceil(capacity) событий и превысить средний поток
     * 34 × cores × M. Цикл остатков короткий: не более 1000 событийных фаз;
     * затем математически учитываются все достижимые остатки пара.
     */
    public static long maxSteamBurstPerTickMilli(int cores, int sumCH, int count) {
        if (cores <= 0) return 0L;
        long cycleCapMilli = (long) cores * MachineDefs.STEAMGEN_STEAM_PER_TICK_PER_CORE
            * MachineDefs.MILLI / MachineDefs.STEAMGEN_STEAM_PER_UNIT;
        int period = MachineDefs.MILLI / gcd((int) (cycleCapMilli % MachineDefs.MILLI), MachineDefs.MILLI);
        double mult = multiplier(sumCH, count);
        long[] producedMilliByPhase = new long[period];
        int[] steamRemainderBefore = new int[period];
        int eventRemainder = 0;
        int steamRemainder = 0;
        long totalProducedMilli = 0L;

        for (int phase = 0; phase < period; phase++) {
            long eventBudget = cycleCapMilli + eventRemainder;
            int events = (int) (eventBudget / MachineDefs.MILLI);
            eventRemainder = (int) (eventBudget % MachineDefs.MILLI);
            long producedMilli = (long) Math.floor(MachineDefs.STEAMGEN_STEAM_PER_UNIT * mult
                * events * MachineDefs.MILLI);
            producedMilliByPhase[phase] = producedMilli;
            steamRemainderBefore[phase] = steamRemainder;
            steamRemainder = (int) ((producedMilli + steamRemainder) % MachineDefs.MILLI);
            totalProducedMilli += producedMilli;
        }

        int remainderStep = (int) (totalProducedMilli % MachineDefs.MILLI);
        int remainderGcd = gcd(remainderStep, MachineDefs.MILLI);
        long maxWholeSteam = 0L;
        for (int phase = 0; phase < period; phase++) {
            int remainder = steamRemainderBefore[phase];
            int maxReachableRemainder = remainder
                + ((MachineDefs.MILLI - 1 - remainder) / remainderGcd) * remainderGcd;
            long wholeSteam = (producedMilliByPhase[phase] + maxReachableRemainder)
                / MachineDefs.MILLI;
            maxWholeSteam = Math.max(maxWholeSteam, wholeSteam);
        }
        return maxWholeSteam * MachineDefs.MILLI;
    }

    private static int gcd(int a, int b) {
        while (b != 0) {
            int remainder = a % b;
            a = b;
            b = remainder;
        }
        return a;
    }

    /** Пропускная способность жидкостных входов/выходов, mB/т: 128 на ядро. */
    public static int fluidIOLimit(int cores) {
        return cores * MachineDefs.STEAMGEN_FLUID_IO_PER_CORE;
    }

    /**
     * Сколько целых циклов варки даёт доступная вода, без перерасхода:
     * floor(вода / 114).
     */
    public static long unitsFromWater(long waterMb) {
        return waterMb / MachineDefs.STEAMGEN_WATER_PER_UNIT;
    }

    /** Сколько целых циклов варки даёт доступный GTH: floor(milli / 193000). */
    public static long unitsFromGth(long gthMilli) {
        return gthMilli / MachineDefs.STEAMGEN_GTH_PER_UNIT_MILLI;
    }
}
