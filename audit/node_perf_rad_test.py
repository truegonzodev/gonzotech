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

ROOT = Path(__file__).resolve().parent.parent
NET = ROOT / "src/main/java/com/gonzotech/machines/network"

routing = (NET / "ItemRouting.java").read_text()
firebox = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/FireboxBlockEntity.java").read_text()
rad = (ROOT / "src/main/java/com/gonzotech/radiation/RadiationSystem.java").read_text()

# ── 1) ItemRouting: один BFS на узел/тик + ранний выход + щит пинг-понга ──
assert "hasExtractableItem(src, srcFace)) continue;" in routing      # пустой источник — без BFS
assert "if (faces.isEmpty()) return 0;" in routing
assert "List<Sink> sinks = collectSinks(level, pos);" in routing     # ОДИН BFS
assert "private static List<Sink> collectSinks(Level level, BlockPos startPipe) {" in routing
assert "collectSinks(Level level, BlockPos startPipe, BlockPos srcPos)" not in routing  # старый API умер
assert "LAST_INSERTED" in routing and "insertedThisTick(now, srcPos)" in routing
assert "noteInserted(now, sink.containerPos());" in routing
assert "sink.containerPos().equals(excludeSrc)" in routing
assert "LAST_INSERTED.size() > 256" in routing                       # чистка при записи
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

# ── 4) радиация пола и рамок ──
assert "scanDroppedItemsInto(level, chunkKey)" in rad
assert "ItemEntity.class, box," in rad
assert "ItemFrame.class, box," in rad
assert "RadSources.emissionDeep(drop.getItem())" in rad
assert "RadSources.emissionDeep(frame.getItem())" in rad
assert "level.getMinY(), cz << 4," in rad                            # AABB чанка

# ── 5) portPlaced: один seed вместо 7 (сборка узлов-кандидатов роняла сервер) ──
for name in ("turbine/TurbineStructure.java", "steamgen/SteamGenStructure.java"):
    src = (ROOT / "src/main/java/com/gonzotech/machines" / name).read_text()
    assert "tryFormFrom(server, pos.relative(direction));" not in src, name
    assert "tryFormFrom(server, pos);\n    }" in src, name

print("node perf / ping-pong / hot-node mark / smoke gate / floor radiation / single-seed portPlaced pins passed")
