#!/usr/bin/env python3
"""Static regression checks for the 0.3.171 worldgen and sedative patch."""
import json
import re
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
JAVA = ROOT / "src/main/java/com/gonzotech"


def read(path):
    return path.read_text(encoding="utf-8")


def load(path):
    return json.loads(read(path))


def require(condition, message):
    if not condition:
        raise AssertionError(message)


expected = {
    # max vein size, average ore blocks per chunk, expected starts per chunk
    "osmium": (1, 0.33, 0.33),
    "iridium": (2, 0.70, 0.35),
    "cesium": (3, 4.0, 2.0),
    "palladium": (3, 2.75, 1.1),
    "platinum": (3, 2.75, 1.1),
    "tellurium": (3, 3.75, 1.5),
    "thorium": (4, 12.0, 3.0),
    "zirconium": (4, 4.0, 1.0),
    "tungsten": (5, 5.0, 1.0),
    "mercury": (4, 3.52, 0.88),
}

version = read(ROOT / "gradle.properties")
require("mod_version=0.3.171" in version, "version should be 0.3.171")
ore_source = read(JAVA / "core/ore/OreDefinition.java")
found = {}
for match in re.finditer(
    r'new OreDefinition\("([a-z]+)",\s*-?\d+,\s*-?\d+,\s*-?\d+,\s*(\d+),\s*'
    r'ToolTier\.[A-Z]+,\s*([0-9.]+)f',
    ore_source,
):
    found[match.group(1)] = (int(match.group(2)), float(match.group(3)))
for ore, (size, average_ore, _) in expected.items():
    require(found.get(ore) == (size, average_ore), f"OreDefinition values wrong for {ore}")

worldgen = RES / "data/gonzotech/worldgen"
for ore, (max_size, _, starts) in expected.items():
    placed = load(worldgen / "placed_feature" / f"{ore}_ore_placed.json")
    count_modifiers = [m for m in placed["placement"] if m["type"] == "gonzotech:expected_count"]
    require(len(count_modifiers) == 1, f"{ore}: expected exactly one fractional-count modifier")
    require(abs(count_modifiers[0]["count"] - starts) < 1e-9, f"{ore}: wrong expected start count")
    require(placed["placement"].index(count_modifiers[0]) < next(
        i for i, m in enumerate(placed["placement"]) if m["type"] == "minecraft:in_square"
    ), f"{ore}: expected-count modifier must precede in-square placement")

    configured = load(worldgen / "configured_feature" / f"{ore}_ore.json")
    if ore in {"cesium", "palladium", "platinum", "tellurium"}:
        require(configured["type"] == "minecraft:random_selector", f"{ore}: expected a random size selector")
        config = configured["config"]
        sizes = []
        for choice in config["features"]:
            require(choice["feature"].get("placement") == [], f"{ore}: selector feature must be a placed feature")
            sizes.append(choice["feature"]["feature"]["config"]["size"])
        require(config["default"].get("placement") == [], f"{ore}: selector default must be a placed feature")
        sizes.append(config["default"]["feature"]["config"]["size"])
        wanted = [1, 2, 3] if ore == "cesium" else [2, 3]
        require(sizes == wanted, f"{ore}: expected random vein sizes {wanted}, got {sizes}")
        chances = [choice["chance"] for choice in config["features"]]
        wanted_chances = [1 / 3, 0.5] if ore == "cesium" else [0.5]
        require(all(abs(a - b) < 1e-5 for a, b in zip(chances, wanted_chances)),
                f"{ore}: size probabilities are not balanced")
    else:
        require(configured["type"] == "minecraft:ore", f"{ore}: expected fixed ore feature")
        require(configured["config"]["size"] == max_size, f"{ore}: wrong configured vein size")

# Thorium has a separate Nether placed feature for its Nether host.
nether_thorium = load(worldgen / "placed_feature/nether_thorium_ore_placed.json")
require(any(m["type"] == "gonzotech:expected_count" and m["count"] == 3.0
            for m in nether_thorium["placement"]), "Nether thorium expected count should be 3")
require(load(worldgen / "configured_feature/nether_thorium_ore.json")["config"]["size"] == 4,
        "Nether thorium size should be four")

placement_source = read(JAVA / "core/worldgen/ExpectedCountPlacement.java")
require("Math.floor(count)" in placement_source and "random.nextDouble() < count - starts" in placement_source,
        "fractional expected-count placement must use floor plus Bernoulli remainder")
require("ModPlacementModifiers.EXPECTED_COUNT.get()" in placement_source,
        "custom expected-count placement type is not wired")

replacement_source = read(JAVA / "core/worldgen/MineralReplacementFeature.java")
require("DEEPSLATE_AT_OSMIUM_CHANCE = 0.30F" in replacement_source,
        "deepslate iridium replacement chance should be 30 percent")
require("state.is(Blocks.DEEPSLATE)" in replacement_source
        and "hasDeepslateOsmiumNeighbor(level, pos)" in replacement_source
        and "random.nextFloat() < DEEPSLATE_AT_OSMIUM_CHANCE" in replacement_source,
        "deepslate neighbors must be tested independently")
require("Direction.values()" in replacement_source and 'ORE_BLOCKS.get("osmium")' in replacement_source,
        "osmium adjacency check must inspect six faces")

item = read(JAVA / "core/item/SedativeItem.java")
items = read(JAVA / "core/registry/ModItems.java")
tabs = read(JAVA / "core/registry/ModCreativeTabs.java")
effects = read(JAVA / "core/registry/ModEffects.java")
psyche = read(JAVA / "core/psyche/PsycheStressEffects.java")
require("ItemUseAnimation.DRINK" in item and "props.stacksTo(4)" in items,
        "sedative must drink and stack to four")
require("ModItems.RAD_ABSORBENT.get()" in tabs
        and "ModItems.SEDATIVE.get()" in tabs
        and tabs.index("ModItems.RAD_ABSORBENT.get()") < tabs.index("ModItems.SEDATIVE.get()"),
        "sedative should be adjacent after the absorbent in Equipment")
require('MOB_EFFECTS.register("relaxation"' in effects, "relaxation effect is not registered")
require("RELAXATION_DURATION_TICKS = 6_000" in item and "FADE_TICKS = 100" in item,
        "relaxation should last five minutes and fade over five seconds")
require("PsycheStress.relieve(player, STRESS_RELIEF)" in item
        and "PsycheStress.addict(player, ADDICTION_INCREASE)" in item
        and "player.removeEffect(ModEffects.TREMOR)" in item,
        "sedative consumption must change the psyche scales and remove tremor")
require("player.hasEffect(ModEffects.RELAXATION)" in psyche
        and "private static void applyTremor" in psyche,
        "active relaxation must block tremor generation")

recipe = load(RES / "data/gonzotech/recipe/sedative.json")
require(recipe["type"] == "minecraft:crafting_shapeless", "sedative recipe must be shapeless")
require(set(recipe["ingredients"]) == {
    "minecraft:sugar", "minecraft:fermented_spider_eye", "minecraft:glowstone_dust",
    "minecraft:red_mushroom", "minecraft:allium", "minecraft:pitcher_pod",
    "minecraft:cocoa_beans", "minecraft:pumpkin_seeds", "minecraft:bowl",
}, "sedative recipe ingredients are incomplete or unexpected")
require(recipe["result"] == {"id": "gonzotech:sedative", "count": 1},
        "sedative recipe should return one item")

post = load(RES / "assets/gonzotech/post_effect/relaxation.json")
require(len(post["passes"]) == 2 and post["passes"][0]["inputs"][0]["target"] == "minecraft:main",
        "relaxation should process the main screen and blit the result back")
shader = load(RES / "assets/gonzotech/shaders/post/relaxation.json")
require(shader["vertex"] == "minecraft:post/blit" and shader["fragment"] == "gonzotech:post/relaxation",
        "relaxation shader program paths are invalid")
shader_source = read(RES / "assets/gonzotech/shaders/post/relaxation.fsh")
program_uniforms = {uniform["name"]: uniform["values"]
                    for uniform in shader["uniforms"]}
require("ContrastScale" in shader_source and "WhiteOverlay" in shader_source
        and program_uniforms["ContrastScale"] == [0.77]
        and program_uniforms["WhiteOverlay"] == [0.15],
        "relaxation shader must expose 23 percent contrast reduction and 15 percent white overlay")
require(any(uniform["name"] == "EffectStrength"
            for uniform in post["passes"][0]["uniforms"]),
        "relaxation fade uniform is not configured")

for path in (
    RES / "assets/gonzotech/textures/item/sedative.png",
    RES / "assets/gonzotech/textures/mob_effect/relaxation.png",
):
    data = path.read_bytes()
    require(data[:8] == b"\x89PNG\r\n\x1a\n", f"invalid PNG: {path}")
    width, height = struct.unpack(">II", data[16:24])
    require(width > 0 and height > 0, f"empty PNG: {path}")

for locale, item_name, effect_name in (
    ("ru_ru", "Седативное средство", "Расслабление I"),
    ("en_us", "Sedative", "Relaxation I"),
):
    lang = load(RES / f"assets/gonzotech/lang/{locale}.json")
    require(lang["item.gonzotech.sedative"] == item_name, f"wrong {locale} sedative name")
    require(lang["effect.gonzotech.relaxation"] == effect_name, f"wrong {locale} effect name")

print("0.3.171 sedative/worldgen checks passed")
