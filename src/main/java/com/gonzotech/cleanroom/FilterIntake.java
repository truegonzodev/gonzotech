package com.gonzotech.cleanroom;

/** One shared input budget per game tick, across all faces and all senders. */
public final class FilterIntake {
    private long tick = Long.MIN_VALUE;
    private long received;

    public long offer(long now, long requested, long space, boolean simulate) {
        long used = now == tick ? received : 0;
        long accepted = Math.max(0, Math.min(Math.min(requested, space), FilterCycle.INTAKE_MILLI_PER_TICK - used));
        if (!simulate) {
            tick = now;
            received = used + accepted;
        }
        return accepted;
    }
}
