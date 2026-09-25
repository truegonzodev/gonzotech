package com.gonzotech.radiation;

/** Thread-local guard: radiation is ignored for stack identity only while a
 * server container click is actively performing an explicit player merge. */
public final class RadiationStackContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private RadiationStackContext() {}

    public static void begin() { DEPTH.set(DEPTH.get() + 1); }
    public static void end() { DEPTH.set(Math.max(0, DEPTH.get() - 1)); }
    public static boolean explicit() { return DEPTH.get() > 0 && !BYPASS.get(); }

    public static boolean bypass() { return BYPASS.get(); }
    public static void withBypass(Runnable action) {
        boolean old = BYPASS.get();
        BYPASS.set(true);
        try { action.run(); } finally { BYPASS.set(old); }
    }
}
