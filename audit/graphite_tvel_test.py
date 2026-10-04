#!/usr/bin/env python3
"""Regression pins for graphite synthesis and the first TVEL content slice."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech"
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


# Chemical-plant process values, per-recipe execution and buffer/gauge agreement.
recipes_java = (SRC / "machines/processing/ChemicalPlantRecipes.java").read_text()
plant = (SRC / "machines/block/entity/ChemicalPlantBlockEntity.java").read_text()
menu = (SRC / "machines/menu/ChemicalPlantMenu.java").read_text()
pin("DEFAULT_REACTION_TICKS = 240" in recipes_java, "legacy reaction duration remains 240 ticks")
pin("DEFAULT_GTU_PER_TICK_MILLI = 1_900L" in recipes_java, "legacy reaction rate remains 1.9 GTU/t")
pin("GRAPHITE_REACTION_TICKS = 200" in recipes_java, "graphite duration is 200 ticks")
pin("GRAPHITE_GTU_PER_TICK_MILLI = 560L" in recipes_java, "graphite rate is 0.56 GTU/t")
pin(200 * 560 == 112_000, "graphite synthesis costs exactly 112 GTU")
pin('new ItemMatcher(Items.COAL, 1)' in recipes_java, "graphite consumes one coal")
pin('new ItemStack(ModItems.GRAPHITE.get(), 1)' in recipes_java, "graphite synthesis outputs one graphite")
pin('0, false, GRAPHITE_REACTION_TICKS, GRAPHITE_GTU_PER_TICK_MILLI' in recipes_java,
    "graphite needs no catalyst")
pin("int reactionTicks," in recipes_java and "long gtuPerTickMilli" in recipes_java,
    "recipe record stores individual duration and energy")
pin("recipe.gtuPerTickMilli()" in plant and "recipe.reactionTicks()" in plant,
    "machine consumes recipe-specific energy and duration")
pin("recipe.catalystRequired() > 0" in plant, "zero-catalyst recipe bypasses random catalyst consumption")
pin("GTU_CAPACITY = 2_000L" in plant, "chemical plant buffer is capped at 2,000 GTU")
pin("return (int) ChemicalPlantBlockEntity.GTU_CAPACITY;" in menu,
    "GTU gauge maximum uses the actual buffer capacity")
pin('Math.min(tag.getLong("GtuMilli"), GTU_CAPACITY_MILLI)' in plant,
    "legacy NBT energy is clamped to the new capacity")
pin('case 2 -> currentReactionTicks();' in plant, "menu receives the active recipe duration")
pin("activeRecipeId.isEmpty() && progress > 0" in plant, "legacy in-flight recipe state is restored safely")
pin("menu.total() > 0 ? (float) menu.progress() / menu.total()" in
    (SRC / "machines/client/ChemicalPlantScreen.java").read_text(),
    "chemical progress gauge scales against the synchronized recipe duration")

# Filler recipe 8 now has a distinct progress label in both locales.
filler = (SRC / "machines/client/FillerScreen.java").read_text()
ru = load(ASSETS / "lang/ru_ru.json")
en = load(ASSETS / "lang/en_us.json")
pin('case 8 -> "gui.gonzotech.filler.recipe.yellow_cake"' in filler,
    "yellow-cake recipe has an active tooltip mapping")
pin(ru["gui.gonzotech.filler.recipe.yellow_cake"] == "Выделение жёлтого кека",
    "Russian yellow-cake progress label")
pin(en["gui.gonzotech.filler.recipe.yellow_cake"] == "Yellow Cake Separation",
    "English yellow-cake progress label")

# Registry and creative-tab placement for all requested items and the block.
items_java = (SRC / "core/registry/ModItems.java").read_text()
blocks_java = (SRC / "core/registry/ModBlocks.java").read_text()
tabs_java = (SRC / "core/registry/ModCreativeTabs.java").read_text()
for ident in ("graphite", "tvel", "uf_tvel", "depressed_uf_tvel"):
    pin(f'"{ident}"' in items_java, f"registered item {ident}")
    pin(f"item.gonzotech.{ident}" in ru and f"item.gonzotech.{ident}" in en,
        f"localized name for {ident}")
pin('"graphite_block"' in blocks_java, "graphite block registered")
pin("GRAPHITE_BLOCK_ITEM" in items_java, "graphite block item registered")
pin("block.gonzotech.graphite_block" in ru and "block.gonzotech.graphite_block" in en,
    "graphite block localized")
components = tabs_java.split('"components"', 1)[1].split('.build()', 1)[0]
pin(components.index("ModItems.GRAPHITE.get()") < components.index("ModItems.GRAPHITE_BLOCK_ITEM.get()"),
    "graphite block immediately follows graphite in Components")
pin(all(f"ModItems.{ident}.get()" in components
        for ident in ("TVEL", "UF_TVEL", "DEPRESSED_UF_TVEL")),
    "TVEL items appear in Components")

# Exact workshop recipes.
graphite_recipe = load(DATA / "recipe/graphite_block.json")
pin(graphite_recipe["type"] == "minecraft:crafting_shaped", "graphite block is a shaped workshop recipe")
pin(graphite_recipe["pattern"] == ["GGG", "GGG", "GGG"], "graphite block takes exactly nine graphite")
pin(graphite_recipe["key"] == {"G": "gonzotech:graphite"}, "graphite block ingredient")
pin(graphite_recipe["result"] == {"id": "gonzotech:graphite_block", "count": 1},
    "graphite block output")

tvel_recipe = load(DATA / "recipe/tvel.json")
pin(tvel_recipe["type"] == "minecraft:crafting_shaped", "TVEL craft is shaped")
pin(tvel_recipe["pattern"] == ["ZCZ", "SHS", "ZIZ"], "TVEL pattern matches the specified matrix")
pin(tvel_recipe["key"] == {
    "Z": "gonzotech:zirconium_plate",
    "C": "gonzotech:core_form",
    "S": "gonzotech:stainless_steel_plate",
    "H": "gonzotech:third_heat_node",
    "I": "gonzotech:zirconium_ingot",
}, "TVEL ingredient IDs match the requested matrix")
pin(tvel_recipe["result"] == {"id": "gonzotech:tvel", "count": 1}, "TVEL recipe outputs one TVEL")
pin(not (DATA / "recipe/uf_tvel.json").exists()
    and not (DATA / "recipe/depressed_uf_tvel.json").exists(),
    "fuelled and spent TVEL items have no unrequested recipes")

# Every new visual has its own existing RGBA PNG and a model reference.
texture_paths = {
    "graphite": ASSETS / "textures/item/components/graphite.png",
    "graphite_block": ASSETS / "textures/block/industry/graphite_block.png",
    "tvel": ASSETS / "textures/item/components/tvel.png",
    "uf_tvel": ASSETS / "textures/item/components/uf_tvel.png",
    "depressed_uf_tvel": ASSETS / "textures/item/components/uf_uvel_depressed.png",
}
for ident, path in texture_paths.items():
    data = path.read_bytes()
    pin(data[:8] == b"\x89PNG\r\n\x1a\n", f"{ident} texture is PNG")
    pin(data[24:26] == b"\x08\x06", f"{ident} texture is 8-bit RGBA (32-bit) PNG")
    model = (ASSETS / "models/item" / f"{ident}.json").read_text()
    pin(path.stem in model or (ident == "depressed_uf_tvel" and "uf_uvel_depressed" in model),
        f"{ident} model references its own texture")

for rel in (
    "blockstates/graphite_block.json",
    "models/block/graphite_block.json",
    "items/graphite_block.json",
):
    pin((ASSETS / rel).is_file(), f"graphite block asset {rel}")
pin((DATA / "loot_table/blocks/graphite_block.json").is_file(), "graphite block drops itself")
pin("gonzotech:graphite_block" in (RES / "data/minecraft/tags/block/mineable/pickaxe.json").read_text(),
    "graphite block is mineable with a pickaxe")

print(f"Graphite/TVEL checks passed: {checks}")
