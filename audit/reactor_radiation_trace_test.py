#!/usr/bin/env python3
"""Static contracts for the first reactor components and the five-second dose trace."""
import json
import struct
from pathlib import Path

import _ver
import _radiation_contracts as current

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
DATA = RES / "data/gonzotech"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def load(path: Path):
    return json.loads(read(path))


_ver.at_least("0.3.234", "reactor structure initialization, textures, and highlighted contour radiation trace")

# Reactor components now have an event-driven formed-state initializer, but not a fuel/thermal runtime.
machines = read(JAVA / "com/gonzotech/machines/registry/ModMachines.java")
tabs = read(JAVA / "com/gonzotech/core/registry/ModCreativeTabs.java")
blocks = (
    "third_uranium_reactor_casing",
    "third_uranium_reactor_control_panel",
    "third_uranium_reactor_fuel_sheath",
)
for name in blocks:
    assert f'"{name}"' in machines, f"block registration missing: {name}"
    assert f"{name.upper()}_ITEM.get()" in tabs, f"creative-tab item missing: {name}"
    assert load(ASSETS / f"blockstates/{name}.json")
    model = load(ASSETS / f"models/block/{name}.json")
    assert model
    item_definition = load(ASSETS / f"items/{name}.json")
    assert item_definition["model"]["model"] == f"gonzotech:block/{name}"
    loot = load(DATA / f"loot_table/blocks/{name}.json")
    assert loot["pools"][0]["entries"][0]["name"] == f"gonzotech:{name}"

panel = read(JAVA / "com/gonzotech/core/block/ReactorControlPanelBlock.java")
assert "implements EntityBlock" in panel
assert "BlockStateProperties.FACING" in panel
assert "context.getNearestLookingDirection().getOpposite()" in panel
assert "new ReactorControlPanelBlockEntity(pos, state)" in panel
assert "protected InteractionResult useWithoutItem(" in panel
assert "protected InteractionResult useItemOn(ItemStack stack" in panel
assert "ReactorStructure.debugReportComponents(level, pos)" in panel
panel_states = load(ASSETS / "blockstates/third_uranium_reactor_control_panel.json")["variants"]
assert set(panel_states) == {f"facing={direction}" for direction in ("down", "up", "north", "south", "east", "west")}
assert all(entry["model"] == "gonzotech:block/third_uranium_reactor_control_panel" for entry in panel_states.values())

sheath = read(JAVA / "com/gonzotech/core/block/ReactorFuelSheathBlock.java")
assert "extends RotatedPillarBlock" in sheath
assert "setValue(AXIS, Direction.Axis.Y)" in sheath
sheath_states = load(ASSETS / "blockstates/third_uranium_reactor_fuel_sheath.json")["variants"]
assert set(sheath_states) == {"axis=x", "axis=y", "axis=z"}

# The dynamic cell is an exact-composition result, never a static anonymous ingredient/output.
items_java = read(JAVA / "com/gonzotech/core/registry/ModItems.java")
serializers = read(JAVA / "com/gonzotech/core/registry/ModRecipeSerializers.java")
recipe_java = read(JAVA / "com/gonzotech/core/recipe/HeatExchangerCellRecipe.java")
cell_item = read(JAVA / "com/gonzotech/core/item/HeatExchangerCellItem.java")
assert 'registerItem("heat_exchanger_cell", HeatExchangerCellItem::new)' in items_java
assert 'register("heat_exchanger_cell"' in serializers
assert 'private static final String[] PATTERN = { "AAA", "A A", "AAA" }' in recipe_java
assert "composition.equals(candidate)" in recipe_java
assert "ModItems.CUSTOM_ALLOY.get()" in recipe_java
assert "HeatExchangerCellItem.fromComposition(composition)" in recipe_java
assert "AlloyEquipmentStats.isSupported(composition)" in recipe_java
assert "ALLOY_COMPOSITION" in cell_item and "ALLOY_TINT" in cell_item
assert "AlloyProperties.from(composition)" in cell_item
assert "CustomAlloyItem.appendCompositionTooltip(stack, tooltip)" in cell_item
assert load(DATA / "recipe/heat_exchanger_cell.json") == {
    "type": "gonzotech:heat_exchanger_cell",
    "category": "misc",
}
advancement = load(DATA / "advancement/recipes/heat_exchanger_cell.json")
assert advancement["rewards"]["recipes"] == ["gonzotech:heat_exchanger_cell"]
cell_item_model = load(ASSETS / "items/heat_exchanger_cell.json")["model"]
assert cell_item_model["model"] == "gonzotech:item/heat_exchanger_cell"
assert cell_item_model["tints"][0]["type"] == "gonzotech:alloy_tint"
assert "ModItems.HEAT_EXCHANGER_CELL.get()" in tabs

# All new display names and the blank-item tooltip exist in both shipped languages.
lang_en = load(ASSETS / "lang/en_us.json")
lang_ru = load(ASSETS / "lang/ru_ru.json")
for key in (
    *(f"block.gonzotech.{name}" for name in blocks),
    "item.gonzotech.heat_exchanger_cell",
    "tooltip.gonzotech.heat_exchanger_cell.blank",
):
    assert key in lang_en and key in lang_ru, key

# New 16×16 sprites are real RGBA PNGs, not missing placeholder paths.
for texture in (
    ASSETS / "textures/block/third_1reactor/third_1reactor_casting.png",
    ASSETS / "textures/block/third_1reactor/third_1reactor_casting_formed.png",
    ASSETS / "textures/block/third_1reactor/third_1reactor_control_panel.png",
    ASSETS / "textures/block/third_1reactor/third_1reactor_fuel_sheath.png",
    ASSETS / "textures/item/heat_exchanger_cell.png",
):
    image = texture.read_bytes()
    assert image[:8] == b"\x89PNG\r\n\x1a\n", texture
    assert struct.unpack(">II", image[16:24]) == (16, 16), texture
    assert image[24] == 8 and image[25] == 6, f"{texture} must be 32-bit RGBA"

# `getdose` opens a 100-server-tick trace; actual radiation code owns event logging and shutdown.
trace = read(JAVA / "com/gonzotech/radiation/RadiationTrace.java")
command = read(JAVA / "com/gonzotech/radiation/RadiationDebugCommand.java")
system = read(JAVA / "com/gonzotech/radiation/RadiationSystem.java")
chunk = read(JAVA / "com/gonzotech/radiation/ChunkRadiationData.java")
containment = read(JAVA / "com/gonzotech/radiation/Containment.java")
assert "DURATION_TICKS = 5 * 20" in trace
assert "CHUNK_RADIUS = 1" in trace
assert "centerChunkX" in trace and "centerChunkZ" in trace
assert "dimension.equals(level.dimension().location())" in trace
assert "Math.abs((long) chunkX - centerChunkX) <= CHUNK_RADIUS" in trace
assert "isActiveChunkTransfer" in chunk and "logChunkTransfer" in chunk
assert '"БЛОК В КОНТУРЕ → ЧАНК"' in chunk
assert "logHighlighted" in chunk and "ChatFormatting.GOLD" in trace
assert "sourceTargetContribution=" in chunk and "contourFactor=" in chunk
assert "tracePlacedSources(level, chunk, RadiationTrace.CHUNK_RADIUS)" in command
assert "enclosed() ? \"closed\" : \"open/unsealed\"" in chunk
assert "все измерения сервера" not in trace
assert "RadiationTrace.start(player)" in command
assert "RadiationTrace.tick(event.getServer())" in system
assert "RadiationTrace.clearAll()" in system
# Per-item cluster trace supersedes old compartment/strongest-stack fields.
current.trace()
current.flows()
assert '"ИНВЕНТАРЬ ИГРОКА → ЧАНК"' in system
assert "inventoryDoseEmission=" in system
assert '"ДОЗА ИГРОКА"' in system
assert '"ДИФФУЗИЯ ЧАНК → ЧАНК"' in chunk
assert '"ИСТОЧНИКИ БЛОКОВ → ЗАРАЖЕНИЕ ЧАНКА"' in chunk
assert '"КЛАСТЕР → ЧАНК"' in system
assert '"БЛОК В КОНТУРЕ → ДОЗА ИГРОКА"' in containment
assert "RadiationTrace.playerDoseChange" in system
assert "data.addContamination(level, chunkKey" in system
print("first reactor + radiation trace static contracts: PASS")
