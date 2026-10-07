#!/usr/bin/env python3
"""Regression pins for the gunpowder block and industrial TNT content."""
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
DATA = RES / "data/gonzotech"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


blocks = (JAVA / "core/registry/ModBlocks.java").read_text(encoding="utf-8")
items = (JAVA / "core/registry/ModItems.java").read_text(encoding="utf-8")
tabs = (JAVA / "core/registry/ModCreativeTabs.java").read_text(encoding="utf-8")
custom_tnt = (JAVA / "core/block/CustomTntBlock.java").read_text(encoding="utf-8")
gunpowder = (JAVA / "core/block/GunpowderBlock.java").read_text(encoding="utf-8")
industrial = (JAVA / "core/block/IndustrialTntBlock.java").read_text(encoding="utf-8")
primed = (JAVA / "core/block/IndustrialPrimedTnt.java").read_text(encoding="utf-8")

# Registrations and requested creative-tab placement.
pin('"gunpowder_block", GunpowderBlock::new' in blocks, "gunpowder block is registered")
pin('"industrial_tnt", IndustrialTntBlock::new' in blocks, "industrial TNT block is registered")
pin('"gunpowder_block", ModBlocks.GUNPOWDER_BLOCK' in items, "gunpowder BlockItem is registered")
pin('"industrial_tnt", ModBlocks.INDUSTRIAL_TNT' in items, "industrial TNT BlockItem is registered")
components = tabs.split('"components"', 1)[1].split('.build()', 1)[0]
component_items = re.findall(r"output\.accept\(([^;]+)\);", components)
graphite_block_index = component_items.index("ModItems.GRAPHITE_BLOCK_ITEM.get()")
pin(component_items[graphite_block_index + 1] == "ModItems.GUNPOWDER_BLOCK_ITEM.get()",
    "gunpowder block immediately follows graphite in Components")
adaptations = tabs.split('"adaptations"', 1)[1].split('.build()', 1)[0]
accepted_items = re.findall(r"output\.accept\(([^;]+)\);", adaptations)
pin(accepted_items[-1] == "ModItems.INDUSTRIAL_TNT_ITEM.get()",
    "industrial TNT is the last Adaptations tab item")

# Powder block: instant strength-6 blast without fire, plus full-grid and reverse recipes.
pin("EXPLOSION_STRENGTH = 6.0F" in gunpowder, "gunpowder blast power is 6")
pin("EXPLOSION_STRENGTH, false, Level.ExplosionInteraction.BLOCK" in gunpowder,
    "gunpowder blast is immediate, block-breaking, and fire-free")
forward = load(DATA / "recipe/gunpowder_block.json")
pin(forward["type"] == "minecraft:crafting_shaped", "gunpowder block requires a shaped recipe")
pin(forward["pattern"] == ["GGG", "GGG", "GGG"], "gunpowder block uses all nine crafting slots")
pin(forward["key"] == {"G": "minecraft:gunpowder"}, "gunpowder block uses vanilla gunpowder")
pin(forward["result"] == {"id": "gonzotech:gunpowder_block", "count": 1},
    "gunpowder block recipe output")
reverse = load(DATA / "recipe/gunpowder_from_gunpowder_block.json")
pin(reverse["type"] == "minecraft:crafting_shapeless", "reverse recipe is shapeless")
pin(reverse["ingredients"] == ["gonzotech:gunpowder_block"], "reverse recipe consumes one block")
pin(reverse["result"] == {"id": "minecraft:gunpowder", "count": 9},
    "reverse recipe returns nine gunpowder")

# Industrial TNT: exact recipe, doubled fuse, power 14, and native TNT explosion behavior.
recipe = load(DATA / "recipe/industrial_tnt.json")
pin(recipe["pattern"] == ["GPI", "PRP", "IPG"], "industrial TNT pattern matches the requested matrix")
pin(recipe["key"] == {
    "G": "minecraft:gravel",
    "P": "gonzotech:gunpowder_block",
    "I": "minecraft:iron_pickaxe",
    "R": "minecraft:redstone_block",
}, "industrial TNT ingredients match the requested matrix")
pin(recipe["result"] == {"id": "gonzotech:industrial_tnt", "count": 1},
    "industrial TNT recipe output")
pin("FUSE_TICKS = 160" in industrial, "industrial TNT fuse is 160 ticks")
pin("EXPLOSION_STRENGTH = 14.0F" in industrial, "industrial TNT strength is 14")
pin("extends PrimedTnt" in primed and "setFuse(IndustrialTntBlock.FUSE_TICKS)" in primed,
    "industrial TNT uses the vanilla primed TNT entity and extended fuse")
pin('"explosion_power"' in primed and "IndustrialTntBlock.EXPLOSION_STRENGTH" in primed,
    "industrial blast power is persisted through vanilla primed TNT data")
pin("serverLevel.addFreshEntity(primedTnt)" in industrial,
    "industrial block primes an entity rather than exploding immediately")
for hook in ("onPlace", "neighborChanged", "onCaughtFire", "useItemOn", "onProjectileHit", "playerWillDestroy", "wasExploded"):
    pin(hook in custom_tnt, f"TNT ignition path is handled: {hook}")

# Assets, loot, and both localizations are present for each placeable block.
en = load(ASSETS / "lang/en_us.json")
ru = load(ASSETS / "lang/ru_ru.json")
for block_id in ("gunpowder_block", "industrial_tnt"):
    state = load(ASSETS / f"blockstates/{block_id}.json")
    pin(set(state["variants"]) == {"unstable=false", "unstable=true"},
        f"blockstate covers both TNT unstable states for {block_id}")
    pin((ASSETS / f"items/{block_id}.json").exists(), f"item definition exists for {block_id}")
    pin((DATA / f"loot_table/blocks/{block_id}.json").exists(), f"loot table exists for {block_id}")
    pin(f"block.gonzotech.{block_id}" in en and f"block.gonzotech.{block_id}" in ru,
        f"English and Russian names exist for {block_id}")

version = (ROOT / "gradle.properties").read_text(encoding="utf-8")
pin("mod_version=0.3.174" in version, "version is bumped to 0.3.174")

print(f"OK: {checks} explosives-content checks passed")
