package com.gonzotech.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side diagnostics. These switches only affect logging, never gameplay. */
public final class GonzoServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue PIPE_ROUTING_DIAGNOSTICS;
    public static final ModConfigSpec.BooleanValue RADIATION_INVENTORY_DEBUG;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        PIPE_ROUTING_DIAGNOSTICS = builder
                .comment("Write detailed pipe-routing and node-clump rebuild diagnostics to the server log.",
                        "Includes routes, BFS visits, distribution rounds and residual-loop work.",
                        "High-volume logging can affect performance; enable only for a short profiling session.")
                .translation("gonzotech.configuration.pipeRoutingDiagnostics")
                .define("pipeRoutingDiagnostics", false);
        RADIATION_INVENTORY_DEBUG = builder
                .comment("Server-side radiation inventory debugger: logs every container click",
                        "with the exact before/after item total of the open menu (slot duplication",
                        "proof) and each radiation inventory pass (writes vs click-freeze skips).",
                        "Logging only; never affects gameplay.")
                .translation("gonzotech.configuration.radiationInventoryDebug")
                .define("radiationInventoryDebug", false);
        SPEC = builder.build();
    }

    private GonzoServerConfig() {}
}
