package com.gonzotech.machines.miner;

/** Per-BE calculation admission; server-thread only. Never replays a side-effect result. */
public final class PlotnickTaskGate {
    public static final int COOLDOWN_TICKS = 200;
    private final long[] deadlines = new long[10];
    private int pending = -1, running = -1;

    public int pending() { return pending; }
    public boolean request(int operation, long now) {
        if (operation < 0 || operation >= deadlines.length) return false;
        if (pending == operation) return true; // coalesced; first request retains its actor
        if (pending >= 0 || running >= 0 || now < deadlines[operation]) return false;
        pending = operation;
        return true;
    }
    public int begin() {
        if (running >= 0 || pending < 0) return -1;
        running = pending;
        pending = -1;
        return running;
    }
    /** Success, failure and cancellation AFTER begin all consume this operation's cooldown. */
    public void complete(long now) {
        if (running >= 0) deadlines[running] = now + COOLDOWN_TICKS;
        running = -1;
    }
    public void cancel(long now) {
        pending = -1; // no work began: no cooldown
        complete(now);
    }
    public long[] save() { return deadlines.clone(); }
    public void load(long[] saved) {
        pending = running = -1; // unstarted calculations have no resources to resume
        java.util.Arrays.fill(deadlines, 0L);
        System.arraycopy(saved, 0, deadlines, 0, Math.min(saved.length, deadlines.length));
    }
}
