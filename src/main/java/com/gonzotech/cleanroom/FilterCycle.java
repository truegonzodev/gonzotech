package com.gonzotech.cleanroom;

/** Paid operating ticks. Fixed-point energy: 1 GTU = 1000 milli-GTU. */
public final class FilterCycle {
    public static final int COAL_SECONDS = 60;
    public static final int CATALYST_SECONDS = 180;
    public static final int CAPACITY_GTU = 2040;
    public static final int WORK_MILLI_PER_TICK = 2600;
    public static final int LEAK_MILLI_PER_TICK = 19;
    public static final int INTAKE_MILLI_PER_TICK = 32000;
    public static final double QUALITY_PER_TICK = 0.2 / 20;
    private int coalTicks;
    private int catalystTicks;
    private boolean coalStarted;
    private boolean catalystStarted;

    /** Migration from the 0.3.18/19 prepaid seconds. */
    public FilterCycle(int coal, int catalyst) {
        coalTicks = Math.clamp(coal, 0, COAL_SECONDS) * 20;
        catalystTicks = Math.clamp(catalyst, 0, CATALYST_SECONDS) * 20;
        coalStarted = coalTicks > 0;
        catalystStarted = catalystTicks > 0;
    }
    public static FilterCycle fromTicks(int coal, int catalyst, boolean coalStarted, boolean catalystStarted) {
        FilterCycle cycle = new FilterCycle(0, 0);
        cycle.coalTicks = Math.clamp(coal, 0, COAL_SECONDS * 20);
        cycle.catalystTicks = Math.clamp(catalyst, 0, CATALYST_SECONDS * 20);
        cycle.coalStarted = coalStarted || cycle.coalTicks > 0;
        cycle.catalystStarted = catalystStarted || cycle.catalystTicks > 0;
        return cycle;
    }
    public int coal() { return (coalTicks + 19) / 20; }
    public int catalyst() { return (catalystTicks + 19) / 20; }
    public int coalTicks() { return coalTicks; }
    public int catalystTicks() { return catalystTicks; }
    public boolean coalStarted() { return coalStarted; }
    public boolean catalystStarted() { return catalystStarted; }
    public boolean needsCoal() { return coalTicks == 0; }
    public boolean needsCatalyst() { return catalystTicks == 0; }
    public int coalUsedHundredths() { return used(coalTicks, COAL_SECONDS * 20, coalStarted); }
    public int catalystUsedHundredths() { return used(catalystTicks, CATALYST_SECONDS * 20, catalystStarted); }
    private static int used(int left, int total, boolean started) {
        return started ? Math.round(10000f * (total - left) / total) : 0;
    }

    public record Step(long energySpent, boolean working, boolean loadCoal, boolean loadCatalyst) {}

    /** Leakage always applies; fuel is prepaid only after ALL work requirements pass. */
    public Step tick(long energy, boolean room, boolean coalAvailable, boolean catalystAvailable) {
        long leak = Math.min(Math.max(0, energy), LEAK_MILLI_PER_TICK);
        if (!room || energy - leak < WORK_MILLI_PER_TICK
                || (needsCoal() && !coalAvailable) || (needsCatalyst() && !catalystAvailable)) {
            return new Step(leak, false, false, false);
        }
        boolean loadCoal = needsCoal(), loadCatalyst = needsCatalyst();
        workedTick();
        return new Step(leak + WORK_MILLI_PER_TICK, true, loadCoal, loadCatalyst);
    }

    private void workedTick() {
        if (needsCoal()) coalTicks = COAL_SECONDS * 20;
        if (needsCatalyst()) catalystTicks = CATALYST_SECONDS * 20;
        coalStarted = catalystStarted = true;
        coalTicks--;
        catalystTicks--;
    }
    /** Compatibility helper for the seconds-based core regression suite. */
    public void workedSecond() { for (int i = 0; i < 20; i++) workedTick(); }
}
