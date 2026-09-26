package com.gonzotech.core.psyche;

/** Author's one-shot doses. Points: 1,000,000 = 100%; food: vanilla 0..20 units. */
public enum AlcoholDose {
    BEER_MUG(4_000, -3_000, 0, 2, -2, 1, 0, 0, 0, 0, 0),
    BEER_BUCKET(4_000, -9_000, 0, 6, -8, 3, 600, 50, 120, 0, 0),
    VODKA(80_000, -40_000, 1_000, -3, -6, 9, 0, 100, 260, 400, 25);

    public final int addiction, stress, crisis, food, saturation, clueChance;
    public final int slownessTicks, poisonChance, poisonTicks, nauseaTicks, faintChance;
    public static final int FAINT_TICKS = 80;
    public static final int BLINDNESS_TICKS = 60;
    public static final double CLUE_RADIUS = 16;

    AlcoholDose(int addiction, int stress, int crisis, int food, int saturation, int clueChance,
                int slownessTicks, int poisonChance, int poisonTicks, int nauseaTicks, int faintChance) {
        this.addiction = addiction; this.stress = stress; this.crisis = crisis;
        this.food = food; this.saturation = saturation; this.clueChance = clueChance;
        this.slownessTicks = slownessTicks; this.poisonChance = poisonChance;
        this.poisonTicks = poisonTicks; this.nauseaTicks = nauseaTicks; this.faintChance = faintChance;
    }

    public int foodAfter(int current) { return Math.clamp(current + food, 0, 20); }
    public float saturationAfter(float current, int newFood) {
        return Math.clamp(current + saturation, 0.0F, (float)newFood);
    }
    /** Exactly chance out of the 100 possible server rolls; zero never succeeds. */
    public static boolean succeeds(int roll, int chance) { return roll >= 0 && roll < chance && roll < 100; }
    public static boolean withinClueRadius(double squaredDistance) {
        return squaredDistance >= 0 && squaredDistance <= CLUE_RADIUS * CLUE_RADIUS;
    }
}
