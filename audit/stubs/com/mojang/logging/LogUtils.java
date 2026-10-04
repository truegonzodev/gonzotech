package com.mojang.logging;

import org.slf4j.Logger;

/** Minimal logging stub for the pipe diagnostics typecheck. */
public final class LogUtils {
    private static final Logger LOGGER = new Logger() {};
    public static Logger getLogger() { return LOGGER; }
    private LogUtils() {}
}
