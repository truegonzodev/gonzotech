#!/usr/bin/env python3
"""Static regression checks for the 0.3.209 noble-moss block family."""
import json
import struct
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
DATA = RES / "data/gonzotech"
FAMILY = (
    "noble_moss",
    "smooth_noble_moss",
    "polished_noble_moss",
    "chiseled_noble_moss",
    "noble_moss_slab",
    "noble_moss_stairs",
    "noble_moss_pillar",
)


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def recipe(name: str):
    return load(DATA / "recipe" / f"{name}.json")


_ver.at_least("0.3.209", "noble-moss content version")

# Registrations, block items and creative-tab visibility.
blocks = (ROOT / "src/main/java/com/gonzotech/core/registry/ModBlocks.java").read_text(encoding="utf-8")
items = (ROOT / "src/main/java/com/gonzotech/core/registry/ModItems.java").read_text(encoding="utf-8")
tabs = (ROOT / "src/main/java/com/gonzotech/core/registry/ModCreativeTabs.java").read_text(encoding="utf-8")
assert "BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)" in blocks
assert ".requiresCorrectToolForDrops();" in blocks[blocks.index("private static BlockBehaviour.Properties nobleMossProperties()"):]
for block_id in FAMILY:
    assert f'"{block_id}"' in blocks, f"block registration missing: {block_id}"
    assert f'ITEMS.registerSimpleBlockItem("{block_id}"' in items, f"block item missing: {block_id}"
    assert f"ModItems.{block_id.upper()}_ITEM.get()" in tabs, f"creative tab entry missing: {block_id}"
assert "new StairBlock(NOBLE_MOSS.get().defaultBlockState(), properties)" in blocks
assert '"noble_moss_slab", SlabBlock::new' in blocks
assert '"noble_moss_pillar", NobleMossPillarBlock::new' in blocks
pillar_class = (ROOT / "src/main/java/com/gonzotech/core/block/NobleMossPillarBlock.java").read_text(encoding="utf-8")
assert "BlockStateProperties.FACING" in pillar_class
assert "context.getNearestLookingDirection().getOpposite()" in pillar_class
assert "rotation.rotate(state.getValue(FACING))" in pillar_class

# Per-variant blockstates, models, item definitions and English/Russian display names.
lang_en = load(ASSETS / "lang/en_us.json")
lang_ru = load(ASSETS / "lang/ru_ru.json")
for block_id in FAMILY:
    assert (ASSETS / f"blockstates/{block_id}.json").is_file(), block_id
    block_model = ASSETS / f"models/block/{block_id}.json"
    assert block_model.is_file(), block_id
    assert (ASSETS / f"models/item/{block_id}.json").is_file(), block_id
    item_def = load(ASSETS / f"items/{block_id}.json")
    assert item_def["model"]["model"] == f"gonzotech:block/{block_id}"
    lang_key = f"block.gonzotech.{block_id}"
    assert lang_key in lang_en and lang_key in lang_ru, lang_key

# Use the user's relocated noble/ textures; the pillar keeps separate side/end art.
textures = (
    "noble_moss",
    "smooth_noble_moss",
    "polished_noble_moss",
    "chiseled_noble_moss",
    "noble_moss_slab",
    "noble_moss_stairs",
    "noble_moss_pillar_side",
    "noble_moss_pillar_topbottom",
)
for texture_id in textures:
    image = (ASSETS / f"textures/block/noble/{texture_id}.png").read_bytes()
    assert image[:8] == b"\x89PNG\r\n\x1a\n", texture_id
    assert struct.unpack(">II", image[16:24]) == (16, 16), texture_id
    assert image[24] == 8 and image[25] == 6, f"{texture_id} must be 32-bit RGBA"

pillar_model = load(ASSETS / "models/block/noble_moss_pillar.json")
assert pillar_model["parent"] == "minecraft:block/cube_column"
assert pillar_model["textures"] == {
    "end": "gonzotech:block/noble/noble_moss_pillar_topbottom",
    "side": "gonzotech:block/noble/noble_moss_pillar_side",
}
pillar_states = load(ASSETS / "blockstates/noble_moss_pillar.json")["variants"]
assert set(pillar_states) == {
    "facing=down", "facing=up", "facing=north", "facing=south", "facing=east", "facing=west"
}
assert all(state["model"] == "gonzotech:block/noble_moss_pillar" for state in pillar_states.values())

# Base conversion: exact shapeless inputs and two output blocks.
base = recipe("noble_moss")
assert base["type"] == "minecraft:crafting_shapeless"
assert set(base["ingredients"]) == {
    "minecraft:mossy_cobblestone", "minecraft:bone_meal", "minecraft:slime_ball"
}
assert base["result"] == {"id": "gonzotech:noble_moss", "count": 2}

# Agreed vanilla-style shaped recipes.
shaped = {
    "polished_noble_moss": (["MM", "MM"], "noble_moss", 4),
    "chiseled_noble_moss": (["M", "M"], "noble_moss_slab", 1),
    "noble_moss_slab": (["MMM"], "noble_moss", 6),
    "noble_moss_stairs": (["M  ", "MM ", "MMM"], "noble_moss", 4),
    "noble_moss_pillar": (["M", "M"], "noble_moss", 2),
}
for output, (pattern, ingredient, count) in shaped.items():
    data = recipe(output)
    assert data["type"] == "minecraft:crafting_shaped", output
    assert data["pattern"] == pattern, output
    assert data["key"] == {"M": f"gonzotech:{ingredient}"}, output
    assert data["result"] == {"id": f"gonzotech:{output}", "count": count}, output

# Smooth stone is smelted; smooth and all crafted derivatives also have stonecutter recipes.
smelting = recipe("smooth_noble_moss_from_smelting")
assert smelting["type"] == "minecraft:smelting"
assert smelting["ingredient"] == "gonzotech:noble_moss"
assert smelting["result"]["id"] == "gonzotech:smooth_noble_moss"
stonecutters = {
    "smooth_noble_moss",
    "polished_noble_moss",
    "chiseled_noble_moss",
    "noble_moss_slab",
    "noble_moss_stairs",
    "noble_moss_pillar",
}
for output in stonecutters:
    data = recipe(f"{output}_from_stonecutter")
    assert data["type"] == "minecraft:stonecutting", output
    assert data["ingredient"] == "gonzotech:noble_moss", output
    assert data["result"]["id"] == f"gonzotech:{output}", output
recipe_names = {path.stem for path in (DATA / "recipe").glob("*noble_moss*.json")}
assert len(recipe_names) == 13 and "noble_moss" in recipe_names

# Loot, recipe advancements and pickaxe mining tags cover the entire family.
for block_id in FAMILY:
    assert (DATA / f"loot_table/blocks/{block_id}.json").is_file(), block_id
    advancement = load(DATA / f"advancement/recipes/{block_id}.json")
    rewards = set(advancement["rewards"]["recipes"])
    if block_id == "smooth_noble_moss":
        expected_rewards = {
            "gonzotech:smooth_noble_moss_from_smelting",
            "gonzotech:smooth_noble_moss_from_stonecutter",
        }
    else:
        expected_rewards = {f"gonzotech:{block_id}"}
        if block_id != "noble_moss":
            expected_rewards.add(f"gonzotech:{block_id}_from_stonecutter")
    assert expected_rewards <= rewards, block_id
pickaxe = load(RES / "data/minecraft/tags/block/mineable/pickaxe.json")["values"]
assert {f"gonzotech:{block_id}" for block_id in FAMILY} <= set(pickaxe)

print("Noble-moss checks passed: 7 blocks, 13 recipes, 8 RGBA texture paths, pillar axes, snow/mining tags")
