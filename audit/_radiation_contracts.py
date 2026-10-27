"""Current integration contracts (author's 0.3.313–315 engine).

These source checks prove wiring, NOT gameplay. Executable production-core and
extracted-flow regressions live in radiation_engine_test.py. Historical 232/294/
300 implementations are not the authority for the current storage or item law.
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"


def read(path):
    return (JAVA / path).read_text(encoding="utf-8")


def method(source, signature):
    """Extract an unchanged Java method, ignoring braces in comments/literals."""
    masked = re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'',
                    lambda m: " " * len(m[0]), source, flags=re.S)
    start = source.index(signature)
    brace = masked.index("{", start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (masked[end] == "{") - (masked[end] == "}")
        end += 1
    return source[start:end]


def compact(text):
    return re.sub(r"\s+", "", text)


def external_storage():
    item = read("radiation/ItemRadioactivity.java")
    external = read("radiation/engine/ExternalRadiation.java")
    cluster = read("radiation/engine/RadiationCluster.java")
    owners = read("radiation/engine/RadiationOwners.java")
    copies = read("radiation/engine/RadiationCopies.java")
    config = json.loads((ROOT / "src/main/resources/gonzotech.mixins.json").read_text())
    for dead in ("radiation/RadDoseIndex.java", "radiation/client/RadReequipGate.java",
                 "mixin/client/ItemInHandRendererMixin.java", "radiation/RadiationClickWatch.java"):
        assert not (JAVA / dead).exists(), f"removed mechanism restored: {dead}"
    assert "client.ItemInHandRendererMixin" not in config["client"]
    assert "ItemRadiationCopyMixin" in config["mixins"]
    copy_hook = read("mixin/ItemRadiationCopyMixin.java")
    assert "RadiationCopies.detachNested" in copy_hook and "ExternalRadiation.copy" in copy_hook
    assert copy_hook.index("ExternalRadiation.copy(") < copy_hook.index("RadiationCopies.detachNested(")
    assert "WeakHashMap<>" in external and "MenuDoseTransaction.copied(from, to)" in external
    assert "ExternalRadiation.read(stack).rate()" in item
    assert "ExternalRadiation.rate(stack," in item
    for text in (item, external, cluster):
        for mutation in ("stack.setCount(", "stack.shrink(", "stack.grow(", "stack.set(", "stack.remove("):
            assert mutation not in text, f"radiation pass mutates live items: {mutation}"
    assert 'KEY = "GonzoItemRadiationV2"' in owners
    assert "depth < 4" in owners and '"Contents"' in owners
    assert "ContainerContentsAccess" in copies and "child.copy()" in copies
    assert "getSelectedItem()" in copies and "finally" in copies
    system = read("radiation/RadiationSystem.java")
    for dead in ("RadDoseIndex.", "HELD_STATES", "[RAD-INV][CLAMP]", "RadiationClickWatch."):
        assert dead not in system, f"old write-back/quantity repair restored: {dead}"
    assert 'throw new IllegalStateException("Radiation changed item count")' in cluster


def scheduler():
    system = read("radiation/RadiationSystem.java")
    player = method(system, "public static List<ItemStack> playerSlots(")
    for field in (".items", ".armor", ".offhand"):
        assert field in player
    for view in ("containerMenu", "getCarried", "getEnderChestInventory"):
        assert view not in player, f"nonphysical player slot: {view}"
    server = method(system, "public static void onServerTick(")
    assert "ACTIVE_CHUNKS.get(level)" in server
    assert "Math.floorMod(level.getGameTime(), PLAYER_PERIOD_TICKS)" in server
    assert "level.shouldTickBlocksAt(key)" in server
    assert "scanContainersInto(level, key, ChunkRadiationData.get(level), level.getGameTime())" in server
    scan = method(system, "private static void scanContainersInto(")
    assert "claimContentScan(level, chunkKey, nowTick)" in scan
    assert "getChunkNow(" in scan and "if (chunk == null) return" in scan
    assert "chestOwners(level, be)" in scan and "origin.equals(be.getBlockPos())" in scan
    assert "RadiationOwners.slots(owner)" in scan and "owner.setChanged()" in scan
    assert "Policy.LEAD" in scan and "Policy.NORMAL" in scan
    assert "ordinaryTransfer = Math.min(" in scan and "maxTarget - contamination" in scan
    unload = method(system, "public static void onChunkUnload(")
    assert "LAST_CONTENT_SCAN.get(level)" in unload and ".remove(key)" in unload
    assert "level.getChunk(" not in system, "radiation must not force-load chunks"


def flows():
    system = read("radiation/RadiationSystem.java")
    player = method(system, "public static void onPlayerTick(")
    assert "RadiationCluster.inspect(roots)" in player
    assert "stepCluster(level, player.blockPosition(), roots" in player
    assert "double totalNzt = result.total()" in player
    assert "double inventoryTarget = result.chunkTarget()" in player
    assert "INVENTORY_TO_CHUNK_RATE * inventoryContourFactor * INVENTORY_TRANSFER_EFFICIENCY" in player
    for law in ("INVENTORY_TO_CHUNK_RATE = 0.01", "INVENTORY_TRANSFER_EFFICIENCY = 0.95",
                "CHEST_TO_CHUNK_RATE = 0.01"):
        assert law in system
    scan = compact(method(system, "private static void scanContainersInto("))
    # Separate lead target attenuation and normal transfer attenuation; subtraction
    # of existing contamination must not be moved outside the lead target.
    assert "?transferTowardChunk(contamination,target*com.gonzotech.machines.block.ThirdLeadChestBlock.CONTENT_CHUNK_FACTOR*factor,CHEST_TO_CHUNK_RATE)" in scan
    assert ":transferTowardChunk(contamination,target,CHEST_TO_CHUNK_RATE*factor)" in scan
    floor = method(system, "private static FloorScan scanDroppedItemsInto(")
    assert "instanceof net.minecraft.world.entity.item.ItemEntity" in floor and "instanceof net.minecraft.world.entity.decoration.ItemFrame" in floor
    assert "RadiationOwners.slots(entity)" in floor
    final = method(system, "private static void classifyFloorStack(")
    assert "RadiationCluster.inspect(List.of(stack)).chunkTarget()" in final
    assert "CHEST_TO_CHUNK_RATE * contourFactor" in final
    assert "blockEmission * stack.getCount() * contourFactor" in final
    assert "convergePlacedLikeEntitySource(level, chunkKey, floor.blockLikeTarget(), elapsed)" in system
    lead = read("machines/block/ThirdLeadChestBlock.java")
    assert "CONTENT_CHUNK_FACTOR = 0.2" in lead
    assert "getTicker" not in lead
    assert "serverTick" not in read("machines/block/entity/ThirdLeadChestBlockEntity.java")


def trace():
    system = read("radiation/RadiationSystem.java")
    step = method(system, "private static RadiationCluster.Summary stepCluster(")
    for field in ("path=", "count=", "induced/item=", "strongestOther/item=", "chunk=",
                  "target/item=", "material=", "internal="):
        assert field in step, f"missing per-item trace field: {field}"
    for accessor in ("step.before()", "step.after()", "step.otherSource()", "step.chunkSource()", "step.target()"):
        assert accessor in step
    for label in ("КЛАСТЕР: ПРЕДМЕТ ↔ ИСТОЧНИК", "КЛАСТЕР → ЧАНК", "ИНВЕНТАРЬ ИГРОКА → ЧАНК", "ДОЗА ИГРОКА"):
        assert label in system
    for field in ("inducedInventoryBeforeTick=", "inducedInventoryAfterTick=", "inventoryDoseEmission=",
                  "strongestItem=", "target=", "transfer="):
        assert field in system
