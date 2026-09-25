package com.gonzotech.cleanroom;

/** Paid operating seconds. Pauses never consume fuel credit. */
public final class FilterCycle {
    public static final int COAL_SECONDS = 60;
    public static final int CATALYST_SECONDS = 180;
    private int coal;
    private int catalyst;

    public FilterCycle(int coal, int catalyst) {
        this.coal = Math.max(0, Math.min(COAL_SECONDS, coal));
        this.catalyst = Math.max(0, Math.min(CATALYST_SECONDS, catalyst));
    }
    public int coal() { return coal; }
    public int catalyst() { return catalyst; }
    public boolean needsCoal() { return coal == 0; }
    public boolean needsCatalyst() { return catalyst == 0; }

    /** Caller has atomically checked energy/room and paid for every exhausted supply. */
    public void workedSecond() {
        if (needsCoal()) coal = COAL_SECONDS;
        if (needsCatalyst()) catalyst = CATALYST_SECONDS;
        coal--;
        catalyst--;
    }
}
