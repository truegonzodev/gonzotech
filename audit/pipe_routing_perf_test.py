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

# 0.3.338: work quotas replace reachability truncation; executable scale/churn in pipe_reform_test.
service = (ROOT / "src/main/java/com/gonzotech/machines/network/PipeNetworkService.java").read_text()
assert "MAX_BFS_CELLS_PER_ENTRY" not in r and "MAX_LANES_PER_DRAIN" not in r
assert "NODES_PER_TICK = 4096" in service and "NANOS_PER_TICK" in service
assert '"topology-pending"' in r and 'PipeNetworkService.request' in r

# crossing index replaces the per-round lane rescan
assert "crossers.computeIfAbsent(key, k -> new ArrayList<>()).add(i);" in r
assert "for (Map.Entry<Long, List<Integer>> e : crossers.entrySet())" in r
assert "private static boolean laneHas" not in r          # удалён
assert "remaining -= x * activeCount;" not in r           # старый учёт остатка

# lazy paths: receiver resolved BEFORE buildPath in both lane builders
aml = r[r.index("private static void addMachineLane"):]
aml = aml[:aml.index("private static void addPortLane")]
assert aml.index("if (raw == null) return;") < aml.index("List<PathStep> path = cachedPath")
assert r.index("if (raw == null || !EmbeddedPortMode.allowsIntake") < r.index("cachedPath(level, pipe, pipe, plan)")

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

# ── Пин 0.3.251: симметрия виртуальных приёмников портов (репорт автора) ──
# Раньше только collectLanes знал виртуальные порты: слив из порта многоблока
# (реактор → GTH через drainFromMultiblockPort) не видел приёмник парогена, и
# «реактор не передаёт GTH в пароген» при работающих блочных станках и других
# источниках. Оба обхода обязаны распознавать одни и те же виртуальные порты.
import _ver
_ver.at_least("0.3.251", "multiblock-port drain sees steamgen/turbine virtual receivers")
assert r.count("case HEAT -> SteamGenStructure.gthReceiverAt(level, pipe);") == 1
assert r.count("TurbineStructure.steamReceiverAt(level, pipe)") == 1
assert r.count("SteamGenStructure.waterReceiverAt(level, pipe)") == 1
drain_port_section = r.split("drainFromMultiblockPort(Level level, BlockPos port", 1)[1]
assert "case HEAT -> SteamGenStructure.gthReceiverAt(level, pipe);" in drain_port_section
assert "case WATER -> {" in drain_port_section

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

print("PipeRouting perf pins passed (crossing index, lazy cached paths, resumable quotas, hot-pipe particles; law/leveling semantics unchanged)")

# 0.3.307: исходный #42 — виртуальный endpoint не форсит чанки и не
# доверяет захваченному BE после simulate/перестройки. Это source-контракт,
# не игровой вердикт и не замена Java-harness steamgen_endpoint_test.py.
sg307 = (ROOT / "src/main/java/com/gonzotech/machines/steamgen/SteamGenStructure.java").read_text()
ports307 = sg307.split("public static Transfer.Receiver waterReceiverAt(", 1)[1].split(
    "public static int maxGthIntakeMilli(", 1)[0]
assert "return portReceiverAt(level, pos, false);" in ports307
assert "return portReceiverAt(level, pos, true);" in ports307
assert "BlockPos port = pos.immutable();" in ports307
assert ports307.count("livePortController(server, port, heat)") == 2
assert "if (current == null || current != expected) return 0;" in ports307
assert "controller.isRemoved() || !controller.isFormedController()" in ports307
assert "if (!(heat ? isHeatPort(state) : isWaterPort(state))) return null;" in ports307
assert ports307.count(".getChunkNow(") == 2
assert "level.getBlockEntity(" not in ports307 and "server.getBlockEntity(" not in ports307
assert ".getChunk(" not in ports307
assert ports307.index("if (portChunk == null || rootChunk == null)") < ports307.index("rootChunk.getBlockEntity(root)")
