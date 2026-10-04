package com.gonzotech.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side diagnostics. These switches only affect logging, never gameplay. */
public final class GonzoServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PIPE_ROUTING_DIAGNOSTICS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PIPE_ROUTING_DIAGNOSTICS = builder
                .comment("Write detailed pipe-routing and node-clump rebuild diagnostics to the server log.",
                        "Includes routes, BFS visits, distribution rounds and residual-loop work.",
                        "High-volume logging can affect performance; enable only for a short profiling session.")
                .translation("gonzotech.configuration.pipeRoutingDiagnostics")
                .define("pipeRoutingDiagnostics", false);
        SPEC = builder.build();
    }

    private GonzoServerConfig() {}
}
