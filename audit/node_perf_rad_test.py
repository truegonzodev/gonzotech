#!/usr/bin/env python3
"""
0.3.96: (1) перф универсальных узлов — ItemRouting гонял collectSinks-BFS по
всей сети на КАЖДУЮ грань КАЖДОГО тика даже без предметов (куча узлов автора =
лаг-вейвы: тысячи чтений + setChanged-шторм пинг-понга предметов между топками);
(2) партикл предупреждения теперь достижим и в узловых сетях (BFS ≥ 128);
(3) дым-столб только у доменной печи (автор); (4) россыпь на полу и в рамках
заражает чанк (автор: «радиация не уходит... если предмет лежит на полу»).
"""
from pathlib import Path
import _radiation_contracts as current

ROOT = Path(__file__).resolve().parent.parent
NET = ROOT / "src/main/java/com/gonzotech/machines/network"

routing = (NET / "ItemRouting.java").read_text()
firebox = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/FireboxBlockEntity.java").read_text()
rad = (ROOT / "src/main/java/com/gonzotech/radiation/RadiationSystem.java").read_text()

# ── 1) ItemRouting: один BFS на узел/тик + ранний выход + щит пинг-понга ──
assert "if (!hasExtractableItem(src, srcFace)) {" in routing      # пустой источник — без BFS
assert "if (faces.isEmpty()) return 0;" in routing
# 0.3.299 (N13): единый BFS на узел теперь ещё и кэшируется (sinksCached)
assert "List<Sink> sinks = sinksCached(level, pos);" in routing     # ОДИН BFS
assert "private static List<Sink> collectSinks(Level level, BlockPos startPipe, int[] visitedOut, int maxNodes) {" in routing  # 0.3.299: счётчик для бюджета; 0.3.306: сам бюджет обхода
assert "if (visited.size() > maxNodes) { visitedOut[0] = 2; break; }" in routing  # 0.3.306: усечение не в кэш
assert "collectSinks(Level level, BlockPos startPipe, BlockPos srcPos)" not in routing  # старый API умер
assert "Map<Level, ItemTransferGuard> TRANSFER_GUARDS" in routing
assert "guard.allows(now, sourceOwners, targetOwners)" in routing
assert "guard.record(now, sourceOwners, targetOwners);" in routing
assert "sink.containerPos().equals(excludeSrc)" in routing
assert "insertedThisTick" not in routing  # receiving must not veto all further inputs/output
assert "private record CachedSinks(List<Sink> sinks, long builtTick, long version)" in routing  # N13
assert "public static void onWorldEdit(Level level)" in routing      # N13: инвалидация от миксина
assert "Map<Level, ItemTransferGuard> TRANSFER_GUARDS" in routing  # per-world, tick-cleared primitive history
assert "record Sink(Container container, Direction face, List<BlockPos> path, BlockPos containerPos)" in routing

# ── 2) горячая метка узловых сетей ──
assert "HOT_NODE_BFS = 128" in routing
assert "PipeFlowWarnings.mark(level, startPipe," in routing
assert "visited.size() >= HOT_NODE_BFS" in routing

# ── 3) дым-столб: только blastFormed ──
assert "if (be.blastFormed) {" in firebox
assert firebox.index("if (be.blastFormed) {") < firebox.index("CAMPFIRE_COSY_SMOKE")
# litTime-- остался вне гейта (горение не зависит от дыма)
assert "be.litTime--;" in firebox

# ── 4) physical-owner radiation, independent of players/open menus ──
# Production Java behavior/flow arithmetic is exercised by radiation_engine_test;
# these checks cover integration into loaded ticking chunk/entity scheduling.
current.scheduler()
current.flows()
chunk = (ROOT / "src/main/java/com/gonzotech/radiation/ChunkRadiationData.java").read_text()
assert "PLACED_UPDATE_PERIOD_TICKS" in rad
assert "Math.pow(1.0D - PLACED_CONVERGE" in chunk
assert "level.getMinY(), cz << 4," in rad  # entity AABB remains one chunk

# ── 5) portPlaced: один seed вместо 7 (сборка узлов-кандидатов роняла сервер) ──
for name in ("turbine/TurbineStructure.java", "steamgen/SteamGenStructure.java"):
    src = (ROOT / "src/main/java/com/gonzotech/machines" / name).read_text()
    assert "tryFormFrom(server, pos.relative(direction));" not in src, name
    assert "tryFormFrom(server, pos);\n    }" in src, name

print("node perf / ping-pong / hot-node mark / smoke gate / floor radiation / single-seed portPlaced pins passed")
