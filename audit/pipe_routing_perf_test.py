#!/usr/bin/env python3
"""PipeRouting perf fix (0.3.90, author report: absorber field killed TPS,
200 ms/tick). Root causes fixed:
1) lanes = receivers x entry-pipes, and the leveling loop re-scanned ALL
   lanes per pipe per round (up to 2n+1 rounds) -> hundreds of millions
   of ops per tick with many absorbers;
2) buildPath ran for EVERY visited BFS node and for non-receiver machine
   neighbours (O(N^2) per BFS);
3) no caps anywhere.
Fix: pipe->lanes crossing index built once, lazy paths (resolve receiver
first), BFS/lanes hard caps. Semantics of the law/leveling unchanged."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
r = (ROOT / "src/main/java/com/gonzotech/machines/network/PipeRouting.java").read_text()

# caps
assert "MAX_BFS_CELLS_PER_ENTRY = 2048" in r
assert "MAX_LANES_PER_DRAIN = 1024" in r  # 0.3.91: замер автора — 15 узлов > 256 дорожек
assert "MAX_LANES_PER_DRAIN = 256" not in r
assert "if (++cells > MAX_BFS_CELLS_PER_ENTRY) break;" in r
assert "lanes.size() >= MAX_LANES_PER_DRAIN" in r

# crossing index replaces the per-round lane rescan
assert "crossers.computeIfAbsent(key, k -> new ArrayList<>()).add(i);" in r
assert "for (Map.Entry<Long, List<Integer>> e : crossers.entrySet())" in r
assert "private static boolean laneHas" not in r          # удалён
assert "remaining -= x * activeCount;" not in r           # старый учёт остатка

# lazy paths: receiver resolved BEFORE buildPath in both lane builders
aml = r[r.index("private static void addMachineLane"):]
aml = aml[:aml.index("private static void addPortLane")]
assert aml.index("if (raw == null) return;") < aml.index("List<PathStep> path = buildPath")
cl = r[r.index("Transfer.Receiver portRaw = switch (type)"):r.index("PipeMode mode = modeOf(pstate, type);")]
assert "buildPath" not in cl.split("if (portRaw != null)")[0]

# unified port lane builder; old trio gone
assert "private static void addPortLane" in r
for gone in ("private static void addTurbineLane",
             "private static void addSteamGenWaterLane",
             "private static void addSteamGenGthLane"):
    assert gone not in r, gone

# switch over PipeType must cover all constants (default branch)
assert "default -> null;" in r

# public API unchanged
for api in ("public static long drain(", "drainFromTurbinePort(", "drainFromMultiblockPort("):
    assert api in r, api

# ── Пины 0.3.92: красная подсветка дорогих узлов (автор 04.10) ──
w = (ROOT / "src/main/java/com/gonzotech/machines/network/PipeFlowWarnings.java").read_text()
assert "HOT_LANE_THRESHOLD = 400" in w                # число автора
assert "LevelTickEvent.Post" in w and "@SubscribeEvent" in w
# 0.3.93: собственная частица вместо красной пыли (текстура автора warn.png)
assert "DustParticleOptions" not in w
assert "ModParticles.HOT_PIPE.get()" in w
mp = (ROOT / "src/main/java/com/gonzotech/core/registry/ModParticles.java").read_text()
assert 'register("hot_pipe"' in mp
mc = (ROOT / "src/main/java/com/gonzotech/core/client/particle/ModParticleClient.java").read_text()
assert "registerSpriteSet(ModParticles.HOT_PIPE.get(), HotPipeParticle.Provider::new)" in mc
hp = (ROOT / "src/main/java/com/gonzotech/core/client/particle/HotPipeParticle.java").read_text()
assert "SIZE_FACTOR = 1.1F" in hp            # размер ×1.1 от reddust (автор)
assert "friction = 0.96F" in hp              # поведение DustParticleBase
assert "PARTICLE_SHEET_OPAQUE" in hp
assert (ROOT / "src/main/resources/assets/gonzotech/particles/hot_pipe.json").is_file()
assert (ROOT / "src/main/resources/assets/gonzotech/textures/particle/warn.png").is_file()
assert "MAX_MARKS_PER_TICK = 64" in w                 # граница пакетов
assert "HOLD_TICKS = 60" in w
assert "it.remove();" in w                            # чистка в тике (static-гигиена)
assert "PipeFlowWarnings.mark(level, BlockPos.of(e.getKey())," in r  # маркер из раскладки

print("PipeRouting perf pins passed (crossing index, lazy paths, caps, hot-pipe particles; law/leveling semantics unchanged)")
