package com.gonzotech.core.network;

/** Thread-safe per-connection sliding window AND outstanding-main-work bound.
 * No world ticks, player references, packet keys or client-controlled time. */
public final class PayloadFloodWindow {
    public enum Admission { ACCEPTED, EXCEEDED, CLOSED }
    public static final long SECOND_NANOS = 1_000_000_000L;
    private long[] times = new long[0];
    private int head, count, outstanding;
    private boolean closed;
    private long accepted;

    public synchronized Admission acquire(long now, int threshold) {
        if (closed) return Admission.CLOSED;
        if (threshold < 1 || threshold > 4096) throw new IllegalArgumentException("Invalid flood threshold");
        while (count > 0 && now - times[head] >= SECOND_NANOS) {
            head = (head + 1) % times.length;
            count--;
        }
        if (count >= threshold || outstanding >= threshold) {
            closed = true; // disconnect once; later packets cannot allocate/enqueue more work
            return Admission.EXCEEDED;
        }
        if (times.length != threshold) {
            long[] resized = new long[threshold];
            for (int i = 0; i < count; i++) resized[i] = times[(head + i) % times.length];
            times = resized;
            head = 0;
        }
        times[(head + count) % times.length] = now;
        count++;
        outstanding++;
        accepted++;
        return Admission.ACCEPTED;
    }
    public synchronized boolean isClosed() { return closed; }
    public synchronized void close() { closed = true; }
    public synchronized void release() {
        if (outstanding <= 0) throw new IllegalStateException("Unbalanced payload admission");
        outstanding--;
    }
    public synchronized int outstanding() { return outstanding; }
    public synchronized long accepted() { return accepted; }
}
