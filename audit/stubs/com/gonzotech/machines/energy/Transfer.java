package com.gonzotech.machines.energy;
/** Стаб: сверен с реальным Transfer (Receiver — @FunctionalInterface). */
public final class Transfer {
    @FunctionalInterface
    public interface Receiver {
        long receive(long amount, boolean simulate);
    }
    private Transfer() { }
}
