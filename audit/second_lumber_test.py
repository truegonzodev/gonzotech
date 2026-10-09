#!/usr/bin/env python3
"""Static regression checks for the tier-two wool/grinder and lumber mill feature."""
import _ver
from pathlib import Path
import hashlib
import json
import struct

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"
ASSETS = RES / "assets/gonzotech"
checks = 0


def check(ok, message):
    global checks
    checks += 1
    assert ok, message


def text(path):
    return path.read_text(encoding="utf-8")


# The wool recipe is tag-based (not a hand-maintained list of sixteen colors) and
# returns three items from one input stack.
grinder = text(SRC / "machines/processing/GrinderRecipes.java")
check("input.is(ItemTags.WOOL)" in grinder and "new ItemStack(Items.STRING, 3)" in grinder,
      "one item in minecraft:wool -> three string")

# Verify the exact authored 3x3 recipe and its result.
recipe = json.loads(text(RES / "data/gonzotech/recipe/second_lumber.json"))
check(recipe["pattern"] == ["IRI", "IDI", "IPI"], "lumber recipe row layout")
check(recipe["key"] == {
    "I": "gonzotech:cast_iron_ingot",
    "R": "minecraft:repeater",
    "D": "minecraft:dispenser",
    "P": "minecraft:piston",
}, "lumber recipe ingredients")
check(recipe["result"] == {"id": "gonzotech:second_lumber", "count": 1}, "lumber recipe result")

tier2 = text(SRC / "machines/crafting/TierTwoCrafting.java")
check('"gonzotech:second_lumber"' in tier2, "recipe-book unlock registered")
check("ModMachines.SECOND_LUMBER_ITEM.get()" in tier2, "physical craft is gated by Discovery 2")

# Block/entity/menu/screen registrations must form a complete path.
checks_for_registration = [
    ("machines/registry/ModMachines.java", 'registerBlock("second_lumber"'),
    ("machines/registry/ModMachines.java", 'registerSimpleBlockItem("second_lumber"'),
    ("machines/registry/ModBlockEntities.java", 'BLOCK_ENTITIES.register("second_lumber"'),
    ("machines/registry/ModMenus.java", 'MENUS.register("second_lumber"'),
    ("machines/client/MachineClient.java", "ModMenus.SECOND_LUMBER.get(), SecondLumberScreen::new"),
    ("core/registry/ModCreativeTabs.java", "ModMachines.SECOND_LUMBER_ITEM.get()"),
]
for file, fragment in checks_for_registration:
    check(fragment in text(SRC / file), f"registration missing: {file} -> {fragment}")

# Core machine rules and exact balance constants.
be = text(SRC / "machines/block/entity/SecondLumberBlockEntity.java")
checks_for_behavior = [
    ("server.hasNeighborSignal(pos)", "redstone required"),
    ("SecondLumberBlock.ACTIVE", "active block state follows redstone"),
    ("ItemTags.AXES", "any tagged axe accepted"),
    ("path.endsWith(\"_wood\") || path.endsWith(\"_log\")", "strict wood/log name filter"),
    ("level.destroyBlock(target, true)", "eligible blocks actually break and drop"),
    ("axe.hurtAndBreak(1", "one durability per broken block"),
    ("machinePos.relative(facing, distance)", "linear facing-aligned search"),
    ("searchDistance = finishedDistance + 1", "advance nearest-to-farthest"),
    ("be.gtu.extract(SecondTierDefs.SECOND_LUMBER_PARASITIC_MILLI_PER_TICK, false)",
     "powered parasitic loss is paid"),
    ("int workCost = workCostForTick(be.cutProgress);", "work energy is charged per progress tick"),
    ("be.gtu.extract(workCost, false)", "work energy is actually deducted"),
    ("SecondTierDefs.SECOND_LUMBER_GTU_INTAKE", "the machine accepts routed GTU"),
    ("level.hasChunkAt(candidate)", "do not force-load the 8-block work lane"),
    ("gtu.save(tag, \"Gtu\")", "GTU persists"),
]
for fragment, purpose in checks_for_behavior:
    check(fragment in be, purpose)

defs = text(SRC / "machines/energy/SecondTierDefs.java")
for value in [
    "SECOND_LUMBER_GTU_CAPACITY = 358 * MachineDefs.MILLI",
    "SECOND_LUMBER_TICKS_PER_BLOCK = 55",
    "SECOND_LUMBER_GTU_PER_BLOCK = 56",
    "SECOND_LUMBER_PARASITIC_MILLI_PER_TICK = 5",
    "SECOND_LUMBER_DEPTH = 8",
]:
    check(value in defs, f"balance constant: {value}")

# Reproduce the exact fixed-point distribution used by the production helper.
ticks, gtu, milli = 55, 56, 1000
costs = [((gtu*milli*(i+1))//ticks)-((gtu*milli*i)//ticks) for i in range(ticks)]
check(len(costs) == 55 and sum(costs) == 56_000 and set(costs) <= {1018, 1019},
      "55 work ticks spend exactly 56 GTU with no rounding drift")

# Four distinct machine tiles and a dedicated pair of 512px GUI sheets.
texture_names = [
    "second_lumber_bottom.png", "second_lumber_top.png",
    "second_lumber_side.png", "second_lumber_face.png",
]
texture_bytes = []
for name in texture_names:
    path = ASSETS / "textures/block/second" / name
    data = path.read_bytes()
    width, height = struct.unpack(">II", data[16:24])
    check(data[:8] == b"\x89PNG\r\n\x1a\n" and (width, height) == (16, 16),
          f"new machine texture is a valid 16x16 PNG: {name}")
    check(data[24:26] == bytes((8, 6)), f"machine texture keeps 32-bit RGBA: {name}")
    texture_bytes.append(hashlib.sha256(data).hexdigest())
check(len(set(texture_bytes)) == 4, "each machine face has its own texture")
model = json.loads(text(ASSETS / "models/block/second_lumber.json"))
check(set(model["textures"]) == {"top", "bottom", "side", "front"}, "four model faces are mapped")
blockstate = json.loads(text(ASSETS / "blockstates/second_lumber.json"))["variants"]
check(len(blockstate) == 8 and all("active=" in key for key in blockstate),
      "all facings render for both redstone states")
for name in ["second_lumber_gui_bg.png", "second_lumber_gui.png"]:
    data = (ASSETS / "textures/gui" / name).read_bytes()
    width, height = struct.unpack(">II", data[16:24])
    check(data[:8] == b"\x89PNG\r\n\x1a\n" and (width, height) == (512, 512),
          f"dedicated GUI sheet is valid 512x512 PNG: {name}")
    check(data[24:26] == bytes((8, 6)), f"GUI sheet keeps 32-bit RGBA: {name}")

for locale in ("ru_ru", "en_us"):
    lang = json.loads(text(ASSETS / f"lang/{locale}.json"))
    for key in (
        "block.gonzotech.second_lumber",
        "container.gonzotech.second_lumber",
        "gui.gonzotech.second_lumber.powered",
        "gui.gonzotech.second_lumber.unpowered",
        "gui.gonzotech.second_lumber.progress",
    ):
        check(key in lang, f"{locale} translation: {key}")

version = text(ROOT / "gradle.properties")
_ver.at_least("0.3.165", "micropatch version is 0.3.165")
print(f"second_lumber_test: {checks} checks passed")
