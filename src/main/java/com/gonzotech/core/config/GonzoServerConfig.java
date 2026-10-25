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
                        "with the exact before/after item and induced-radiation totals of the open",
                        "menu (slot duplication proof), each radiation inventory pass and every",
                        "gonzo_rad NBT removal with its cause.",
                        "0.3.291 adds: creative slot packets (negative slot = drop), client-side",
                        "setSlot receipts, QUICK_CRAFT phases and every Slot#setByPlayer write",
                        "with component compatibility, ItemEntity create/merge/pickup/remove by",
                        "entity id, radiation-pass component diffs guarded by a count check",
                        "(truncation logs as its own [CLAMP] event). Client parts are enabled by",
                        "this same flag through a login gate packet. Logging only; gameplay is",
                        "never affected.")
                .translation("gonzotech.configuration.radiationInventoryDebug")
                .define("radiationInventoryDebug", false);
        SPEC = builder.build();
    }

    private GonzoServerConfig() {}
}
