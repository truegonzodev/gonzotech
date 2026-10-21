#!/usr/bin/env python3
"""Static contracts: «Улучшение ванили» и компоненты 0.3.254 (автор)."""
import json
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"


def read(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")


polish = read("core/event/VanillaPolishEvents.java")
watch = read("core/world/CalciteWatchData.java")
grinder = read("machines/processing/GrinderRecipes.java")
filler = read("machines/block/entity/FillerBlockEntity.java")
plant = read("machines/processing/ChemicalPlantRecipes.java")
alloy = read("machines/processing/AlloyFoundryRecipes.java")
alloy_be = read("machines/block/entity/AlloyFoundryBlockEntity.java")
items = read("core/registry/ModItems.java")
tier_three = read("machines/crafting/TierThreeCrafting.java")
jei = read("integration/jei/MachineJeiRecipes.java")

# ── 1. чистый кремень гонзо: gonzotech:silicon -> кремниевая крошка (кремень тоже) ──
assert "ModItems.SILICON.get(), ModItems.SILICON_CRUMBS.get()" in grinder
assert "Items.FLINT, ModItems.SILICON_CRUMBS.get()" in grinder

# ── 2. генератор туфа: лава + ВСПЕНЕННАЯ вода -> туф вместо булыжника/камня ──
assert "FluidPlaceBlockEvent" in polish
assert "Blocks.COBBLESTONE" in polish and "Blocks.STONE" in polish
assert "Blocks.TUFF" in polish
assert "Blocks.BUBBLE_COLUMN" in polish and "Blocks.SOUL_SAND" in polish
assert "setNewState" in polish

# ── 3. кальцит: 240 с контакта воды с костным блоком + наполнитель + химзавод ──
assert "CALCITE_DELAY_SECONDS = 240" in polish
assert "Blocks.BONE_BLOCK" in polish and "Blocks.CALCITE" in watch
assert "NeighborNotifyEvent" in polish and "EntityPlaceEvent" in polish
assert "LevelTickEvent" in polish
assert "CalciteWatchData" in watch and "setDirty" in watch  # таймеры персистентны
# наполнитель: 1-6 блоков x (128 mB воды + 128 GTH) = 1-6 кальцита
assert "CALCITE_FROM_BONE_WATER_MB = 128" in filler
assert "CALCITE_FROM_BONE_GTH_PER_BLOCK = 128" in filler
assert "CALCITE_FROM_BONE_TICKS_PER_BLOCK = 32" in filler
assert "activeRecipe = 9" in filler and "case 9 ->" in filler
assert "new ItemStack(Items.CALCITE, batch)" in filler
# химзавод: тот же кальцит «под нагревом»
assert '"calcite_from_bone"' in plant
assert "new ItemMatcher(Items.BONE_BLOCK, 1)" in plant

# ── 4. глубинный сланец: 4 монолита + ведро воды = 16, ведро возвращается ──
deepslate = json.loads((RES / "data/gonzotech/recipe/deepslate_from_monolith.json").read_text())
assert deepslate["type"] == "minecraft:crafting_shapeless"
assert deepslate["result"] == {"id": "minecraft:deepslate", "count": 16}
ing = [i["item"] for i in deepslate["ingredients"]]
assert ing.count("gonzotech:monolith") == 4 and ing.count("minecraft:water_bucket") == 1

# ── 5. цирконий из оксида: завод сплавов (кратно) + химзавод (x1.2 GTU) ──
assert "findZirconiumFromOxide" in alloy
assert "new Batch(output, byItem, 112 * oxideCount)" in alloy  # 3 слитка в 3 сплава = 112 т x 2.8 GTU
assert "record Batch(ItemStack output, Map<Item, Integer> ingredients, int workTicks)" in alloy
assert "batch.workTicks()" in alloy_be
assert '"zirconium_from_oxide"' in plant
assert "1_568L" in plant  # 240 т x 1.568 GTU/т = 376.32 GTU = 313.6 x 1.2
assert "ZIRCONIUM_OXIDE" in plant and "AnyFormMatcher" in plant

# ── 6. материнская плата: фулл-гейт Открытия 3 ──
assert "MOTHERBOARD" in items and '"motherboard"' in items
assert '"gonzotech:motherboard"' in tier_three
assert "ModItems.MOTHERBOARD.get()" in tier_three
mother = json.loads((RES / "data/gonzotech/recipe/motherboard.json").read_text())
assert mother["type"] == "minecraft:crafting_shapeless"
assert mother["result"]["id"] == "gonzotech:motherboard"
names = [i["item"] for i in mother["ingredients"]]
for need in ("gonzotech:chip_1", "gonzotech:chip_2", "gonzotech:chip_3",
             "gonzotech:energy_module", "gonzotech:transistor",
             "gonzotech:rubber", "gonzotech:celluloid"):
    assert names.count(need) >= 1, need
assert names.count("gonzotech:energy_module") == 2

# ── ассеты и lang ──
assert json.loads((RES / "assets/gonzotech/items/motherboard.json").read_text())
assert (RES / "assets/gonzotech/textures/item/motherboard.png").is_file()
for lang in ("ru_ru.json", "en_us.json"):
    data = json.loads((RES / "assets/gonzotech/lang" / lang).read_text())
    assert "item.gonzotech.motherboard" in data, lang

# ── JEI: наполнитель показывает рецепт кальцита ──
assert '"reaction/bone_calcite"' in jei
assert '"bone_calcite"' in jei

assert _ver.version() == "0.3.257", _ver.version()
print("vanilla polish + component contracts OK")
