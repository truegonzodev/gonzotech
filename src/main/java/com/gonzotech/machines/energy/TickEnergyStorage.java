package com.gonzotech.machines.energy;

/** Finite milli-GTU store with aggregate, not per-call, game-tick budgets.
 * Simulation is read-only, including the budget clocks. No item charging yet. */
public final class TickEnergyStorage {
    private final ThirdStorageDefs.Spec spec;
    private long stored;
    private long inputTick = Long.MIN_VALUE, outputTick = Long.MIN_VALUE, lossTick = Long.MIN_VALUE;
    private long received, extracted;

    public TickEnergyStorage(ThirdStorageDefs.Spec spec) { this.spec = spec; }
    public ThirdStorageDefs.Spec spec() { return spec; }
    public long stored() { return stored; }
    public void restore(long amount) { stored = Math.max(0L, Math.min(spec.capacity(), amount)); }

    public long receive(long requested, boolean simulate, long tick) {
        if (requested <= 0) return 0;
        long used = inputTick == tick ? received : 0;
        long accepted = Math.min(requested, Math.min(spec.intake() - used, spec.capacity() - stored));
        if (!simulate && accepted > 0) {
            inputTick = tick; received = used + accepted; stored += accepted;
        }
        return accepted;
    }
    public long availableOutput(long tick) {
        return Math.min(stored, spec.output() - (outputTick == tick ? extracted : 0));
    }
    public long extract(long requested, boolean simulate, long tick) {
        if (requested <= 0) return 0;
        long given = Math.min(requested, availableOutput(tick));
        if (!simulate && given > 0) {
            long used = outputTick == tick ? extracted : 0;
            outputTick = tick; extracted = used + given; stored -= given;
        }
        return given;
    }
    /** Loss applies once per loaded game tick; no offline or item-mode self-discharge. */
    public long idle(long tick) {
        if (lossTick == tick) return 0;
        lossTick = tick;
        long loss = Math.min(stored, spec.idleLoss()); stored -= loss; return loss;
    }
    public int syncPart(int index) {
        return switch (index) {
            case 0 -> (int)(stored % 1_000L);
            case 1 -> (int)((stored / 1_000L) % 1_000L);
            case 2 -> (int)(stored / 1_000_000L);
            default -> 0;
        };
    }
    /** Each limb is below 1000, safe through signed-short ContainerData packets. */
    public static long decode(int fraction, int units, int thousands) {
        return Math.clamp(fraction, 0, 999) + 1_000L * Math.clamp(units, 0, 999)
            + 1_000_000L * Math.clamp(thousands, 0, 999);
    }
}
