package com.gonzotech.radiation;

/**
 * 0.3.291: клиентская копия серверного гейта дебаггера (см.
 * {@link InventoryDebugGatePayload}). Выставляется на входе в мир; для
 * однопользовательской игры это тот же JVM-флаг, но читается он без
 * обращения к серверному конфиг-файлу.
 */
public final class InventoryDebugClientGate {
    private static final java.util.concurrent.atomic.AtomicBoolean ENABLED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private InventoryDebugClientGate() {}

    public static void set(boolean on) { ENABLED.set(on); }

    public static boolean isOn() { return ENABLED.get(); }
}
