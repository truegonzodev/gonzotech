package org.slf4j;

/** Minimal methods used by diagnostics in the local typecheck. */
public interface Logger {
    default void info(String message) { }
    default void warn(String message) { }
}
