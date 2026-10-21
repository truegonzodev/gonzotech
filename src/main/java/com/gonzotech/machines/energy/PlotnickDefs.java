package com.gonzotech.machines.energy;

/**
 * Плотник автошахтера (пакет 1, 0.3.256) — энергетика и хранилище.
 * Смету GTU автор задаст позже; значения — рабочая заглушка пакета 1.
 */
public final class PlotnickDefs {

    /** Вместимость GTU: 4096 ед. */
    public static final long GTU_CAPACITY_MILLI = 4_096L * MachineDefs.MILLI;

    /** Приём GTU за тик: 96 ед./т (как у измельчителя отработки). */
    public static final long GTU_INTAKE_MILLI = 96L * MachineDefs.MILLI;

    /** Каждый работающий бур тратит 6.5 GTU/т (0.3.258, автор). */
    public static final long DRILL_GTU_MILLI = 6_500L;

    /** Бур углубляется раз в 2 секунды (40 тиков), 39 тиков — сбор руд. */
    public static final int DRILL_CYCLE_TICKS = 40;

    /** Демонтаж сети и закладка ТНТ — максимум 3 блока за тик. */
    public static final int PACK_BLOCKS_PER_TICK = 3;

    private PlotnickDefs() {
    }
}
