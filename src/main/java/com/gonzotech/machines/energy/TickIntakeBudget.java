package com.gonzotech.machines.energy;

/** Aggregate intake gate for existing GtBuffer owners. Keeps their BigInteger
 * storage/NBT and numbers unchanged; probes never spend or reset the clock.
 * Owned by one BE on the server thread, not shared across stores or worlds. */
public final class TickIntakeBudget {
    private boolean committed;
    private long inputTick, received;

    public long receive(GtBuffer buffer, long requested, long limit, boolean simulate, long tick) {
        if (requested <= 0 || limit <= 0) return 0;
        long used = committed && inputTick == tick ? received : 0;
        long allowance = Math.max(0, limit - used);
        long accepted = buffer.receive(Math.min(requested, allowance), simulate);
        if (!simulate && accepted > 0) {
            committed = true;
            inputTick = tick;
            received = used + accepted;
        }
        return accepted;
    }
}
