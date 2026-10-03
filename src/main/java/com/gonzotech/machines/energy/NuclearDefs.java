package com.gonzotech.machines.energy;

/** Balance and safety thresholds for the Discovery-2 nuclear firebox and passive tungsten absorber. */
public final class NuclearDefs {

    private NuclearDefs() {
    }

    // ─────────────────────────── Ядерная топка ───────────────────────────

    /** Nuclear Firebox internal heat buffer, in mGTH (68,408 displayed GTH). */
    public static final int NUCLEAR_FIREBOX_GTH_CAPACITY = 68_408 * MachineDefs.MILLI;
    /** Constant heat generation while one accepted nuclear fuel item burns, in mGTH/t. */
    public static final int NUCLEAR_FIREBOX_GTH_PER_TICK = 116 * MachineDefs.MILLI;
    /** Max GTH output from the firebox's buffer, in mGTH/t. */
    public static final int NUCLEAR_FIREBOX_GTH_OUTPUT = 282 * MachineDefs.MILLI;
    /** Passive heat loss, 0.2 GTH/t, in mGTH/t. */
    public static final int NUCLEAR_FIREBOX_GTH_LOSS = 200;

    /** No fuel acceleration at or below this stored heat amount, in mGTH. */
    public static final int NUCLEAR_FIREBOX_ACCELERATION_START = 22_040 * MachineDefs.MILLI;
    /** Full fuel-time acceleration at this stored heat amount, in mGTH. */
    public static final int NUCLEAR_FIREBOX_ACCELERATION_END = NUCLEAR_FIREBOX_GTH_CAPACITY;
    /** Maximum burn-time reduction at a full GTH buffer (25%). */
    public static final int NUCLEAR_FIREBOX_MAX_BURN_REDUCTION_PERMILLE = 250;

    /** Above this stored GTH, nearby blocks can ignite and touching players are set on fire. */
    public static final int NUCLEAR_FIREBOX_IGNITION_THRESHOLD = 50_000 * MachineDefs.MILLI;
    /** Strictly above this stored GTH, the reactor melts its 3×3 footprint into corium. */
    public static final int NUCLEAR_FIREBOX_CORIUM_THRESHOLD = 64_000 * MachineDefs.MILLI;

    /** One second equals twenty Minecraft ticks. */
    public static final int TICKS_PER_SECOND = 20;
    /**
     * Grace period after the GTH crosses {@link #NUCLEAR_FIREBOX_CORIUM_THRESHOLD}.
     * While the buffer stays above the threshold for this whole span without
     * resetting (any drop below 64,000 GTH restarts the timer), the firebox
     * block itself is melt-protected; afterwards it becomes a melt candidate.
     */
    public static final int NUCLEAR_FIREBOX_SELF_MELT_GRACE_TICKS = 20 * TICKS_PER_SECOND;

    // 0.3.143: новые исходные времена автора; остальные формы сохраняют прежние
    // отношения к слитку. Дробные секунды округляются к ближайшему тику (0.05 с).
    public static final int URANIUM_INGOT_BURN_TICKS = 130 * TICKS_PER_SECOND;
    /** Старое отношение 100/900 = 1/9 от уранового слитка. */
    public static final int URANIUM_NUGGET_BURN_TICKS = (URANIUM_INGOT_BURN_TICKS + 4) / 9;
    /** Старое отношение 8100/900 = 9× от уранового слитка. */
    public static final int URANIUM_BLOCK_BURN_TICKS = 9 * URANIUM_INGOT_BURN_TICKS;
    /** Старое отношение 500/900 = 5/9 от уранового слитка. */
    public static final int URANINITE_BURN_TICKS = (5 * URANIUM_INGOT_BURN_TICKS + 4) / 9;
    /** Пыль переплавляется в слиток 1:1, поэтому горит как слиток. */
    public static final int URANIUM_DUST_BURN_TICKS = URANIUM_INGOT_BURN_TICKS;

    public static final int THORIUM_INGOT_BURN_TICKS = 40 * TICKS_PER_SECOND;
    /** Старое отношение 35/300 = 7/60 от ториевого слитка. */
    public static final int THORIUM_NUGGET_BURN_TICKS = (7 * THORIUM_INGOT_BURN_TICKS + 30) / 60;
    /** Старое отношение 2700/300 = 9× от ториевого слитка. */
    public static final int THORIUM_BLOCK_BURN_TICKS = 9 * THORIUM_INGOT_BURN_TICKS;
    /** Старое отношение 175/300 = 7/12 от ториевого слитка. */
    public static final int THORIANITE_BURN_TICKS = (7 * THORIUM_INGOT_BURN_TICKS + 6) / 12;
    /** Пыль переплавляется в слиток 1:1, поэтому горит как слиток. */
    public static final int THORIUM_DUST_BURN_TICKS = THORIUM_INGOT_BURN_TICKS;

    // ───────────────────────── Вольфрамовый абсорбер ─────────────────────────

    /** Tungsten block heat capacity, in mGTH (112,000 displayed GTH). */
    public static final int TUNGSTEN_ABSORBER_GTH_CAPACITY = 112_000 * MachineDefs.MILLI;
    /** Tungsten's heat-pipe-limited max intake, in mGTH/t. */
    public static final int TUNGSTEN_ABSORBER_GTH_INTAKE = 488 * MachineDefs.MILLI;
    /** Tungsten deliberately dissipates a very large 66 GTH/t, in mGTH/t. */
    public static final int TUNGSTEN_ABSORBER_GTH_LOSS = 66 * MachineDefs.MILLI;
    /** Fire hazard threshold, in mGTH. */
    public static final int TUNGSTEN_ABSORBER_IGNITION_THRESHOLD = 50_000 * MachineDefs.MILLI;
    /** Strictly above this stored GTH, the tungsten block melts its 3×3 footprint into lava. */
    public static final int TUNGSTEN_ABSORBER_LAVA_THRESHOLD = 94_000 * MachineDefs.MILLI;
    /** Extra tungsten-absorber heat dissipation per adjacent superdense ice block, in mGTH/t. */
    public static final int SUPERDENSE_ICE_COOLING_PER_BLOCK = 32 * MachineDefs.MILLI;
}
