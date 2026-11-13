package com.gonzotech.machines.network;

import java.io.InputStream;
import java.util.Properties;

/** Fixed for the lifetime of the JVM: never switch routing under live caches. */
public final class PipeExperiment {
    private static final Properties SETTINGS = settings();
    public static final String VARIANT = variant();
    public static final boolean ENABLED = VARIANT.equals("experimental");
    public static final boolean PROFILE = Boolean.parseBoolean(System.getProperty(
        "gonzotech.pipe.profile", SETTINGS.getProperty("profile", "false")));
    public static final String SNAPSHOT = "pipe-II-340-" + (ENABLED ? "B" : "A");
    private PipeExperiment() {}
    private static Properties settings() {
        Properties p = new Properties();
        try (InputStream in = PipeExperiment.class.getResourceAsStream("/gonzotech-pipe-snapshot.properties")) {
            if (in != null) p.load(in);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read pipe snapshot", e); }
        return p;
    }
    private static String variant() {
        String value = System.getProperty("gonzotech.pipe.variant", SETTINGS.getProperty("variant", "baseline"));
        if (!value.equals("baseline") && !value.equals("experimental"))
            throw new IllegalStateException("Unknown pipe variant: " + value);
        return value;
    }
}
