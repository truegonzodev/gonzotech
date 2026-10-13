#!/usr/bin/env python3
"""Regression checks for all 26 compressed raw-ore blocks and processing recipes."""
import json
import re
import struct
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
DATA = RES / "data/gonzotech"

EXPECTED_ORES = [
    "calcium", "aluminum", "magnesium", "sulfur", "manganese", "titanium",
    "barium", "zinc", "tin", "boron", "chromium", "nickel", "cobalt",
    "silver", "iodine", "tungsten", "mercury", "uranium", "zirconium",
    "thorium", "platinum", "tellurium", "palladium", "cesium", "iridium", "osmium",
]


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


_ver.at_least("0.3.209", "raw-ore block content version")
source = (ROOT / "src/main/java/com/gonzotech/core/ore/OreDefinition.java").read_text(encoding="utf-8")
ores = re.findall(r'new OreDefinition\("([a-z0-9_]+)"', source)
assert ores == EXPECTED_ORES, f"OreDefinition.ALL order changed: {ores}"
raw_ids = {ore: "raw_calcite" if ore == "calcium" else f"raw_{ore}" for ore in ores}
raw_blocks = {ore: f"{raw_ids[ore]}_block" for ore in ores}

blocks_java = (ROOT / "src/main/java/com/gonzotech/core/registry/ModBlocks.java").read_text(encoding="utf-8")
items_java = (ROOT / "src/main/java/com/gonzotech/core/registry/ModItems.java").read_text(encoding="utf-8")
tabs_java = (ROOT / "src/main/java/com/gonzotech/core/registry/ModCreativeTabs.java").read_text(encoding="utf-8")
assert "RAW_ORE_BLOCKS.put(ore.id(), BLOCKS.registerSimpleBlock(rawBlockId, rawOreBlockProperties()))" in blocks_java
assert "RAW_ORE_BLOCK_ITEMS.put(ore.id(), ITEMS.registerSimpleBlockItem(" in items_java
assert "ModBlocks.RAW_ORE_BLOCKS.get(ore.id())" in items_java
properties_start = blocks_java.index("private static BlockBehaviour.Properties rawOreBlockProperties()")
properties_end = blocks_java.index("\n    }", properties_start)
properties = blocks_java[properties_start:properties_end]
assert ".sound(SoundType.METAL)" in properties
assert ".strength(4.5f, 10.8f)" in properties
assert ".requiresCorrectToolForDrops()" in properties
raw_items_index = tabs_java.index("ModItems.RAW_ORE_ITEMS.values()")
raw_blocks_index = tabs_java.index("ModItems.RAW_ORE_BLOCK_ITEMS.values()")
ingots_index = tabs_java.index("ModItems.INGOT_ITEMS.values()")
assert raw_items_index < raw_blocks_index < ingots_index, "resource tab order must be raw items, raw blocks, ingots"

mineable = set(load(RES / "data/minecraft/tags/block/mineable/pickaxe.json")["values"])
needs_stone = set(load(RES / "data/minecraft/tags/block/needs_stone_tool.json")["values"])
needs_iron = set(load(RES / "data/minecraft/tags/block/needs_iron_tool.json")["values"])
needs_diamond = set(load(RES / "data/minecraft/tags/block/needs_diamond_tool.json")["values"])
lang_en = load(ASSETS / "lang/en_us.json")
lang_ru = load(ASSETS / "lang/ru_ru.json")

for ore in ores:
    raw_id = raw_ids[ore]
    block_id = raw_blocks[ore]
    metal_block_id = f"{ore}_block"
    blockstate = load(ASSETS / f"blockstates/{block_id}.json")
    assert blockstate["variants"][""]["model"] == f"gonzotech:block/{block_id}", block_id

    model = load(ASSETS / f"models/block/{block_id}.json")
    assert model["parent"] == "minecraft:block/cube_all", block_id
    texture_id = f"raw_{ore}"
    texture_ref = model["textures"]["all"]
    assert texture_ref == f"gonzotech:block/raw_ore/{texture_id}", (block_id, texture_ref)
    texture = ASSETS / f"textures/block/raw_ore/{texture_id}.png"
    png = texture.read_bytes()
    assert png[:8] == b"\x89PNG\r\n\x1a\n", texture
    assert struct.unpack(">II", png[16:24]) == (16, 16), texture
    assert png[24] == 8 and png[25] == 6, f"{texture} must be 32-bit RGBA"

    item_def = load(ASSETS / f"items/{block_id}.json")
    assert item_def["model"]["model"] == f"gonzotech:block/{block_id}", block_id
    assert (DATA / f"loot_table/blocks/{block_id}.json").is_file(), block_id
    loot = load(DATA / f"loot_table/blocks/{block_id}.json")
    assert len(loot["pools"]) == 1, block_id
    loot_pool = loot["pools"][0]
    assert loot_pool["rolls"] == 1.0, block_id
    assert len(loot_pool["entries"]) == 1, block_id
    assert loot_pool["entries"][0] == {"type": "minecraft:item", "name": f"gonzotech:{block_id}"}, block_id
    assert "functions" not in loot_pool and "conditions" not in loot_pool["entries"][0], block_id

    craft = load(DATA / f"recipe/{block_id}.json")
    assert craft["type"] == "minecraft:crafting_shaped", block_id
    assert craft["pattern"] == ["RRR", "RRR", "RRR"], block_id
    assert craft["key"] == {"R": f"gonzotech:{raw_id}"}, block_id
    assert craft["result"] == {"id": f"gonzotech:{block_id}", "count": 1}, block_id
    craft_adv = load(DATA / f"advancement/recipes/{block_id}.json")
    assert craft_adv["rewards"]["recipes"] == [f"gonzotech:{block_id}"], block_id

    stem = f"{metal_block_id}_from_{block_id}"
    blast = load(DATA / f"recipe/{stem}_blasting.json")
    assert blast["type"] == "minecraft:blasting", block_id
    assert blast["ingredient"] == f"gonzotech:{block_id}", block_id
    assert blast["result"]["id"] == f"gonzotech:{metal_block_id}", block_id
    assert blast["cookingtime"] == 900, block_id
    furnace_recipes = [f"gonzotech:{stem}_blasting"]
    if ore == "cesium":
        assert not (DATA / f"recipe/{stem}_smelting.json").exists(), "compressed cesium is blast-furnace only"
    else:
        smelt = load(DATA / f"recipe/{stem}_smelting.json")
        assert smelt["type"] == "minecraft:smelting", block_id
        assert smelt["ingredient"] == f"gonzotech:{block_id}", block_id
        assert smelt["result"]["id"] == f"gonzotech:{metal_block_id}", block_id
        assert smelt["cookingtime"] == 1800, block_id
        furnace_recipes.insert(0, f"gonzotech:{stem}_smelting")
    smelt_adv = load(DATA / f"advancement/recipes/{stem}.json")
    assert smelt_adv["rewards"]["recipes"] == furnace_recipes, block_id

    namespaced_block = f"gonzotech:{block_id}"
    assert namespaced_block in mineable, f"{block_id} is not tagged mineable/pickaxe"
    assert namespaced_block in needs_stone, f"{block_id} must drop with a stone-tier pickaxe"
    assert namespaced_block not in needs_iron and namespaced_block not in needs_diamond, block_id
    assert f"block.gonzotech.{block_id}" in lang_en
    assert f"block.gonzotech.{block_id}" in lang_ru


mixin = (ROOT / "src/main/java/com/gonzotech/mixin/AbstractFurnaceBlockEntityMixin.java").read_text(encoding="utf-8")
assert 'ModItems.METAL_BLOCK_ITEMS.get("cesium_block").get()' in mixin
machine_effects = (ROOT / "src/main/java/com/gonzotech/machines/block/entity/SmeltSideEffects.java").read_text(encoding="utf-8")
assert 'ModItems.METAL_BLOCK_ITEMS.get("cesium_block").get()' in machine_effects
water_events = (ROOT / "src/main/java/com/gonzotech/core/event/Phase3Events.java").read_text(encoding="utf-8")
assert 'ModItems.RAW_ORE_BLOCK_ITEMS.get("cesium").get()' in water_events

print("PASS: 26 raw-ore blocks, original RGBA ore textures, one-item self-drops, stone-tier pickaxe drops, shared stats/sounds, recipes, creative order, and cesium blast handling")
