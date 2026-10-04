package net.neoforged.neoforge.common;

/** Minimal server-config stub for the pipe diagnostics typecheck. */
public final class ModConfigSpec {
    public boolean isLoaded() { return true; }

    public static final class Builder {
        public Builder comment(String... lines) { return this; }
        public Builder translation(String key) { return this; }
        public BooleanValue define(String key, boolean defaultValue) { return new BooleanValue(defaultValue); }
        public ModConfigSpec build() { return new ModConfigSpec(); }
    }

    public static final class BooleanValue {
        private final boolean value;
        public BooleanValue(boolean value) { this.value = value; }
        public boolean get() { return value; }
    }
}
