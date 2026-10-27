package com.gonzotech.machines.energy;

/** Author-specified finite III storage. All quantities here are milli-GTU. */
public final class ThirdStorageDefs {
    public record Spec(long capacity, long intake, long output, long idleLoss) {
        public Spec {
            if (capacity <= 0 || intake <= 0 || output <= 0 || idleLoss < 0)
                throw new IllegalArgumentException("Invalid energy storage specification");
        }
    }
    public static final Spec ACCUMULATOR = new Spec(230_882_000L, 294_000L, 294_000L, 32L);
    public static final Spec BATTERY = new Spec(68_088_000L, 244_000L, 188_000L, 0L);
    public static final int ITEM_SLOTS = 3;
    public static final int SYNC_PARTS = 3;
    private ThirdStorageDefs() {}
}
