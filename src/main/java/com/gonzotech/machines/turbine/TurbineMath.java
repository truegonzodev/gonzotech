package com.gonzotech.machines.turbine;

import com.gonzotech.machines.energy.MachineDefs;

/**
 * Общие, детерминированные расчёты паровой турбины.
 *
 * <p>Вся логика размера намеренно живёт здесь, а не размазывается между
 * валидатором, BlockEntity и GUI. Поэтому клиент получает число роторов и
 * восстанавливает ровно те же ёмкости, что и сервер.</p>
 */
public final class TurbineMath {

    private TurbineMath() {
    }

    /**
     * Эффективное число роторов. Кривая имеет максимум около N=56.29 и обращается
     * в ноль при N=100, поэтому N>=100 не образует турбину.
     */
    public static double multiplier(int rotors) {
        if (rotors <= 0 || rotors >= MachineDefs.TURBINE_MAX_ROTORS_EXCLUSIVE) return 0.0D;
        double n = rotors;
        return n * (1.0D - Math.pow(n / MachineDefs.TURBINE_MAX_ROTORS_EXCLUSIVE,
            MachineDefs.TURBINE_ROTOR_CURVE_EXPONENT));
    }

    /** Целое игровое значение характеристики, масштабированной кривой. */
    public static int scaled(int perEffectiveRotor, int rotors) {
        if (perEffectiveRotor <= 0 || rotors <= 0 || rotors >= MachineDefs.TURBINE_MAX_ROTORS_EXCLUSIVE) {
            return 0;
        }
        return Math.max(1, (int) Math.round(perEffectiveRotor * multiplier(rotors)));
    }

    public static int steamCapacity(int rotors) {
        return scaled(MachineDefs.TURBINE_STEAM_CAPACITY_PER_EFFECTIVE_ROTOR, rotors);
    }

    public static int gtuCapacityUnits(int rotors) {
        return scaled(MachineDefs.TURBINE_GTU_CAPACITY_PER_EFFECTIVE_ROTOR, rotors);
    }

    public static long gtuCapacityMilli(int rotors) {
        return (long) gtuCapacityUnits(rotors) * MachineDefs.MILLI;
    }

    public static int maxSteamIntake(int rotors) {
        return scaled(MachineDefs.TURBINE_STEAM_INTAKE_PER_EFFECTIVE_ROTOR, rotors);
    }

    public static int maxSteamConsumption(int rotors) {
        return scaled(MachineDefs.TURBINE_STEAM_CONSUMPTION_PER_EFFECTIVE_ROTOR, rotors);
    }

    /**
     * Сколько mGTU получается из Steam. Остаток деления нужен контроллеру, чтобы
     * 1.5 GTU / 56 mB оставались точной долгосрочной конверсией.
     */
    public static long gtuMilliForSteam(int steamMb, int conversionRemainder) {
        if (steamMb <= 0) return 0;
        return ((long) steamMb * MachineDefs.TURBINE_GTU_PER_REFERENCE_STEAM_MILLI
            + Math.max(0, conversionRemainder)) / MachineDefs.TURBINE_REFERENCE_STEAM_MB;
    }

    public static int conversionRemainderAfter(int steamMb, int conversionRemainder) {
        if (steamMb <= 0) return Math.max(0, conversionRemainder) % MachineDefs.TURBINE_REFERENCE_STEAM_MB;
        return (int) (((long) steamMb * MachineDefs.TURBINE_GTU_PER_REFERENCE_STEAM_MILLI
            + Math.max(0, conversionRemainder)) % MachineDefs.TURBINE_REFERENCE_STEAM_MB);
    }

    /** Номинальный предел отдачи GTU за тик: полная номинальная прожорливость роторов. */
    public static long maxGtuOutputMilli(int rotors) {
        return gtuMilliForSteam(maxSteamConsumption(rotors), 0);
    }
}
