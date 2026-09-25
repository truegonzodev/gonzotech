package com.gonzotech.cleanroom;

/** Redstone edge detection and exactly 160 processed active ticks, independent of Minecraft. */
public final class CleanerPulse {
    private int remaining;
    private boolean powered;
    private long lastActiveTick = Long.MIN_VALUE;

    public CleanerPulse(int remaining, boolean powered) {
        this.remaining = Math.max(0, Math.min(160, remaining));
        this.powered = powered;
    }
    public int remaining() { return remaining; }
    public boolean powered() { return powered; }
    public boolean signal(boolean signal) {
        if (signal == powered) return false;
        powered = signal;
        if (signal) remaining = 160;
        return true;
    }
    public boolean tick(long time) {
        if (remaining <= 0) return false;
        remaining--;
        lastActiveTick = time;
        return true;
    }
    public boolean active(long time) { return lastActiveTick == time; }
}
