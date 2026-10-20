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

# ── 4) радиация инвентаря, контейнеров, россыпи и радиоактивных BlockItem ──
assert "scanContainersInto(level, chunkKey, data, nowTick)" in rad
assert "claimContentScan(level, chunkKey, nowTick)" in rad       # несколько игроков не сканируют чанк каждый
assert "private static FloorScan scanDroppedItemsInto(ServerLevel level, long chunkKey," in rad
assert "private static void classifyFloorStack(" in rad
assert "ItemRadioactivity.decayInducedDeepLazily(stack, nowTick)" in rad
assert "double blockEmission = blockEmission(blockItem.getBlock());" in rad
assert "convergePlacedLikeEntitySource(level, chunkKey, floor.blockLikeTarget(), floorElapsed)" in rad
assert "Containment.factor(level, pos)" in rad
assert "INVENTORY_TO_CHUNK_RATE = 0.01" in rad
# 0.3.249: the inventory cannot contaminate the chunk above the strongest
# stack's emission (target is not the sum) and every transfer loses 5%.
assert "INVENTORY_TO_CHUNK_RATE * inventoryContourFactor * INVENTORY_TRANSFER_EFFICIENCY" in rad
assert "double inventoryTarget = Math.min(totalNzt, strongestStackNzt);" in rad
assert "INVENTORY_TRANSFER_EFFICIENCY = 0.95" in rad
assert "double totalNzt = 0.0D;" in rad             # post-tick full sum; no top-level induced double count
assert "CHEST_TO_CHUNK_RATE = 0.01" in rad
assert "CHEST_TO_CHUNK_RATE * contourFactor" in rad # normal containers/final floor items
assert "CONTENT_CHUNK_FACTOR" in rad
assert "effectiveEmission, CHEST_TO_CHUNK_RATE" in rad # exact lead formula: 0.01×max(0.2×M×F−C,0)
assert "blockEmission * stack.getCount() * contourFactor" in rad
assert "elapsedFloorBlockScanTicks" in rad
assert "PLACED_UPDATE_PERIOD_TICKS" in rad
assert "Math.pow(1.0D - PLACED_CONVERGE" in (ROOT / "src/main/java/com/gonzotech/radiation/ChunkRadiationData.java").read_text()
lead_be = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/ThirdLeadChestBlockEntity.java").read_text()
lead_block = (ROOT / "src/main/java/com/gonzotech/machines/block/ThirdLeadChestBlock.java").read_text()
assert "serverTick" not in lead_be and "BlockEntityTicker" not in lead_block
assert "decayInducedDeepLazily" in rad       # same lazy no-source rate as inventory, no peer irradiation
assert "ItemEntity.class, box," in rad
assert "ItemFrame.class, box," in rad
assert "double emission = RadSources.emissionDeep(stack);" in rad
assert "out[0] += transfer;" in rad
assert "level.getMinY(), cz << 4," in rad                            # AABB чанка

# The exact rate laws remain observable and numerical: contour scales finite-source
# transfer, while lead-box contour scales the target before its 1% difference.
def toward(chunk, source, rate):
    return max(source - chunk, 0.0) * rate
assert toward(2.0, 12.0, 0.01 * 0.25) == 0.025
assert abs(toward(0.2, 12.0 * 0.2 * 0.25, 0.01) - 0.004) < 1.0e-12
assert abs(1.0 - (1.0 - 0.05) ** 1 - 0.05) < 1.0e-12
assert 1.0 - (1.0 - 0.05) ** 20 >= 0.64

# ── 5) portPlaced: один seed вместо 7 (сборка узлов-кандидатов роняла сервер) ──
for name in ("turbine/TurbineStructure.java", "steamgen/SteamGenStructure.java"):
    src = (ROOT / "src/main/java/com/gonzotech/machines" / name).read_text()
    assert "tryFormFrom(server, pos.relative(direction));" not in src, name
    assert "tryFormFrom(server, pos);\n    }" in src, name

print("node perf / ping-pong / hot-node mark / smoke gate / floor radiation / single-seed portPlaced pins passed")
