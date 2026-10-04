#!/usr/bin/env python3
"""Opt-in, behavior-neutral diagnostics for pipe routing and node-clump rebuilds."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java"
config = (JAVA / "com/gonzotech/core/config/GonzoServerConfig.java").read_text()
mod = (JAVA / "com/gonzotech/GonzoTechMod.java").read_text()
routing = (JAVA / "com/gonzotech/machines/network/PipeRouting.java").read_text()
diag = (JAVA / "com/gonzotech/machines/network/PipeRoutingDiagnostics.java").read_text()
clumps = (JAVA / "com/gonzotech/machines/network/NodeClumpIndex.java").read_text()

# Server-side switch is easy to disable and off by default.
assert '.define("pipeRoutingDiagnostics", false)' in config
assert "ModConfig.Type.SERVER" in mod
assert "GonzoServerConfig.SPEC" in mod
assert "warnIfEnabled()" in mod

# Per-drain records cover both standard and built-in multiblock ports.
assert '"machine"' in routing and '"multiblock-port"' in routing
assert "PipeRoutingDiagnostics.begin" in routing
for metric in (
    "bfsVisited", "levelRounds", "remAtTail", "tailPasses",
    "tailLaneChecks", "tailPipeChecks", "tailBlockedLanes",
    "sourceTaken", "sourceBlock", "crossingIndex", "residual", "levelRoundDetail", "pipeCapacity", "lanesDetail", "offeredToReceiver",
):
    assert metric in diag, f"missing route diagnostic: {metric}"
assert "accepted=" in diag and "sourceCharge=" in diag
assert "MAX_BFS_CELLS_PER_ENTRY = 2048" in routing
assert "MAX_LANES_PER_DRAIN = 1024" in routing

# Topology reconstruction is timed separately and remains entirely opt-in.
assert "[GONZOTECH PIPE-DIAG][CLUMP]" in diag
assert '"node-change"' in clumps
assert '"node-remove"' in clumps
assert '"detach-port"' in clumps
assert "elapsedUs=" in diag

# Diagnostics are logger output; the default/off path does not log.
assert "LOGGER.info" in diag and "LOGGER.warn" in diag
assert "if (!isEnabled()) return null;" in diag
print("pipe routing diagnostics pins passed (server toggle, route/tail phases, clump rebuild timings)")
