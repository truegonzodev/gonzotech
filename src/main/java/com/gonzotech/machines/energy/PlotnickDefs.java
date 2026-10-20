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

    private PlotnickDefs() {
    }
}
