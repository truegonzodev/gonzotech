package com.gonzotech.machines.energy;

/**
 * Баланс второго открытия.
 *
 * <p>Значения намеренно отделены от {@link MachineDefs}: изменение этого класса
 * не меняет уже построенные машины и логистику первого уровня.</p>
 */
public final class SecondTierDefs {

    private SecondTierDefs() {
    }

    // ─────────────────────────── логистика ───────────────────────────

    /** Провод II: 96 GTU/t, во внутренней milli-точности. */
    public static final long WIRE_THROUGHPUT = 96L * MachineDefs.MILLI;
    /** Теплотруба II: 696 GTH/t, во внутренней milli-точности. */
    public static final long HEAT_THROUGHPUT = 696L * MachineDefs.MILLI;
    /** Водная труба II: 1900 mB/t. */
    public static final long WATER_THROUGHPUT = 1_900L;
    /** Паровая труба II: 1900 mB/t. */
    public static final long STEAM_THROUGHPUT = 1_900L;
    /** Предметная труба II: пять типов по две штуки = десять предметов за тик. */
    public static final long ITEM_THROUGHPUT = 10L;
    /** Предельный поток одного точного Item через предметную трубу II. */
    public static final int ITEM_PER_TYPE_THROUGHPUT = 2;
    /** Общий Water + Steam бюджет универсальной жидкостной трубы/узла II. */
    public static final long UNIVERSAL_FLUID_THROUGHPUT = 1_500L;

    // ─────────────────────────── аккумулятор II ───────────────────────────

    public static final int ACCUMULATOR_GTU_CAPACITY = 48_900 * MachineDefs.MILLI;
    public static final int ACCUMULATOR_GTU_INTAKE = 144 * MachineDefs.MILLI;
    public static final int ACCUMULATOR_GTU_OUTPUT = 144 * MachineDefs.MILLI;
    /** 0.01 GTU/t, во внутренней milli-точности. */
    public static final int ACCUMULATOR_GTU_LOSS = 10;

    // ─────────────────────────── электропечь II ───────────────────────────

    public static final int ELECTRIC_GTU_CAPACITY = 8_400 * MachineDefs.MILLI;
    public static final int ELECTRIC_COOK_TIME = 90;
    public static final int ELECTRIC_GTU_PER_ITEM = 220 * MachineDefs.MILLI;
    /** 220 GTU / 90 т = 2.444… GTU/t; остаток распределяется без округления. */
    public static final int ELECTRIC_GTU_MILLI_PER_TICK_BASE =
        ELECTRIC_GTU_PER_ITEM / ELECTRIC_COOK_TIME;
    public static final int ELECTRIC_GTU_MILLI_PER_TICK_REMAINDER =
        ELECTRIC_GTU_PER_ITEM % ELECTRIC_COOK_TIME;

    // ─────────────────────────── фильтр II ───────────────────────────

    public static final int ITEM_FILTER_SLOTS = 5;

    // ─────────────────────────── помпа II ───────────────────────────

    public static final int PUMP_GTU_CAPACITY = 1_200 * MachineDefs.MILLI;
    public static final int PUMP_WATER_CAPACITY = 24_000;
    public static final int PUMP_GTU_INTAKE = 96 * MachineDefs.MILLI;
    public static final int PUMP_GTU_MILLI_PER_TICK = 3_000;
    public static final int PUMP_WATER_OUTPUT = 844;
    public static final int PUMP_SUCK_INTERVAL = 4;

    // ─────────────────────── генератор булыжника II ───────────────────────

    public static final int COBBLE_GTU_CAPACITY = 644 * MachineDefs.MILLI;
    public static final int COBBLE_WATER_CAPACITY = 12_000;
    public static final int COBBLE_GTU_INTAKE = 64 * MachineDefs.MILLI;
    public static final int COBBLE_WATER_INTAKE = 1_000;
    public static final int COBBLE_WATER_PER_ROCK = 1_000;
    public static final int COBBLE_GTU_MILLI_PER_TICK = 1_400;
    public static final int COBBLE_TICKS = 60;
}
