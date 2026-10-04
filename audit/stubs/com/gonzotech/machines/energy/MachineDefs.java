package com.gonzotech.machines.energy;
/** Стаб: только члены, затрагиваемые сетевым пакетом (типы сверены). */
public final class MachineDefs {
    public static final int UNIVERSAL_FLUID_OUTPUT = 316;
    /** milli-точность: 1 единица = 1000 milli. */
    public static final int MILLI = 1000;
    public static final int STEAMGEN_MAX_EXCHANGERS = 26;
    public static final int STEAMGEN_WATER_CAPACITY_PER_CORE = 256;
    public static final int STEAMGEN_STEAM_CAPACITY_PER_CORE = 512;
    public static final int STEAMGEN_GTH_CAPACITY_PER_CORE = 1_536;
    public static final int STEAMGEN_FLUID_IO_PER_CORE = 128;
    public static final int STEAMGEN_FLUID_IO_MINIMUM = 256;
    private MachineDefs() { }
}
