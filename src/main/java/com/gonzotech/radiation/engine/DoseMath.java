package com.gonzotech.radiation.engine;

/** Rates, not a consumable radiation budget. No Minecraft or inventory mutations. */
public final class DoseMath {
    private DoseMath() {}
    public static double finite(double value) {
        return Double.isFinite(value) && value > 0 ? value : 0;
    }
    public static double merge(double a, int aCount, double b, int bCount) {
        long n = (long) Math.max(0, aCount) + Math.max(0, bCount);
        return n == 0 ? 0 : finite(a) * (Math.max(0, aCount) / (double) n)
                + finite(b) * (Math.max(0, bCount) / (double) n);
    }
}
