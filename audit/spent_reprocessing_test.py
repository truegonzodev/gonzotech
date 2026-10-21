#!/usr/bin/env python3
"""Static contracts: отработка ТВЭЛов и Измельчитель II (0.3.253)."""
import json
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"


def read(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")


items = read("core/registry/ModItems.java")
grinder = read("machines/processing/GrinderRecipes.java")
centrifuge = read("machines/processing/CentrifugeRecipes.java")
pcfsoz = read("machines/processing/PCFSOZRecipes.java")
reprocessing = read("machines/processing/ReprocessingRecipes.java")
be = read("machines/block/entity/ReprocessingGrinderBlockEntity.java")
menu = read("machines/menu/ReprocessingGrinderMenu.java")
screen = read("machines/client/ReprocessingGrinderScreen.java")
machines = read("machines/registry/ModMachines.java")
block_entities = read("machines/registry/ModBlockEntities.java")
menus = read("machines/registry/ModMenus.java")
rad_sources = read("radiation/RadSources.java")
defs = read("machines/energy/SecondTierDefs.java")
wrench = read("machines/item/WrenchItem.java")
sandpaper = read("machines/item/SandpaperItem.java")
tier_two = read("machines/crafting/TierTwoCrafting.java")
tier_three = read("machines/crafting/TierThreeCrafting.java")
unlocks = read("chalkboard/advancement/RecipeUnlocks.java")
flags = read("chalkboard/notes/ScholarNoteFlags.java")
adv = read("chalkboard/advancement/ModAdvancements.java")
flow = read("machines/network/PipeFlowNetwork.java")
hud = read("machines/client/WrenchHud.java")

# ── chain: spent TVEL -> slags -> zirconium oxide / reactor plutonium / U-238 ──
assert "EXTREMELY_DANGEROUS_SLAG" in items and "MODERATELY_DANGEROUS_SLAG" in items
assert "ZIRCONIUM_OXIDE" in items and "EMPTY_HULL" in items
assert "ModItems.DEPRESSED_UF_TVEL.get(), ModItems.EXTREMELY_DANGEROUS_SLAG.get()" in grinder

# 0.3.253 (автор): разделение шлаков живёт в ПЦФСОЗ (НЕ в центрифуге) и с
# диапазонами количеств.
assert "EXTREMELY_DANGEROUS_SLAG.get())" in pcfsoz
assert "Byproduct.ranged(() -> ModItems.URANIUM_238.get(), CHANCE_SCALE, 1, 2)" in pcfsoz
assert "MODERATELY_DANGEROUS_SLAG.get())" in pcfsoz
assert "Byproduct.of(() -> ModItems.DUST_ITEMS.get(\"plutonium_dust\").get(), 150)" in pcfsoz
assert "public static Byproduct ranged(ItemRef item, int chancePermille, int minCount, int maxCount)" in pcfsoz
assert "EXTREMELY_DANGEROUS_SLAG" not in centrifuge
assert "MODERATELY_DANGEROUS_SLAG" not in centrifuge

# The reprocessing helpers keep the TVEL and hull contracts.
assert "acceptsTvel" in reprocessing and "acceptsHull" in reprocessing

# Radioactivity presets: extreme slag strongly active, moderate slag mild.
assert 'Map.entry("extremely_dangerous_slag", 15.0 * RadUnits.MILLI)' in rad_sources
assert 'Map.entry("moderately_dangerous_slag", 1.5 * RadUnits.MILLI)' in rad_sources

# ── Измельчитель II: корпус опционален, статы ×1.66, вся таблица измельчителя ──
assert "SLOT_TVEL_INPUT = 0" in be and "SLOT_HULL_INPUT = 1" in be
assert "SLOT_SLAG_OUTPUT = 2" in be and "SLOT_BARREL_OUTPUT = 3" in be
assert "SecondTierDefs.GRINDER2_TICKS" in be and "SecondTierDefs.GRINDER2_GTU_MILLI_PER_TICK" in be
assert "GrinderRecipes.find(tvel)" in be and "GrinderRecipes.accepts(stack)" in be
assert "boolean withHull" in be and "WASTE_BARREL_ITEM" in be
assert "dropPendingOutputForBreak" in be and "PendingSlag" in be and "PendingBarrel" in be
assert "SLOT_TVEL_INPUT, 35, 26" in menu and "SLOT_HULL_INPUT, 35, 44" in menu
assert "SLOT_SLAG_OUTPUT, 116, 26" in menu and "SLOT_BARREL_OUTPUT, 116, 44" in menu
assert 'machineGui("third_grinder_gui_bg.png", "third_grinder_gui_bg_neo.png")' in screen
assert "GRINDER2_GTU_CAPACITY" in defs and "GRINDER2_TICKS" in defs
assert "GRINDER2_GTU_MILLI_PER_TICK = (int) Math.round(900 * 1.66)" in defs

# Id и ассеты: third_grinder (gen-3), старого id нигде не осталось.
assert 'registerSimpleBlockItem("third_grinder"' in machines
assert 'BLOCK_ENTITIES.register("third_grinder"' in block_entities
assert 'MENUS.register("third_grinder"' in menus
assert json.loads((RES / "assets/gonzotech/blockstates/third_grinder.json").read_text())
assert (RES / "assets/gonzotech/textures/gui/classic/third_grinder_gui_bg.png").is_file()
assert (RES / "assets/gonzotech/textures/gui/neo/third_grinder_gui_bg_neo.png").is_file()
assert json.loads((RES / "data/gonzotech/loot_table/blocks/third_grinder.json").read_text())["type"] == "minecraft:block"

# ── рецепты измельчителя (0.3.253, автор) ──
assert "RAW_IRON" in grinder and "CORUNDUM_GRAIN" in grinder
assert "FLINT" in grinder and "SILICON_CRUMBS" in grinder
assert "Items.DIAMOND" in grinder and "DIAMOND_DUST" in grinder  # ванильный алмаз
assert "BREAD" in grinder and "THE_PROTO_MASH" in grinder
assert "PACKED_ICE" in grinder and "SNOWBALL" in grinder
assert "GLOWSTONE" in grinder and "SOUL_SAND" in grinder
assert "BLAZE_ROD" in grinder and "BLAZE_POWDER" in grinder
assert "record Output(Item item, int count)" in grinder

# ── наждачкис: обтёсывание + полировка, бесконечный ──
assert "AXE_STRIP" in sandpaper and "POLISH" in sandpaper
assert "registerItem(\"sandpapy\", com.gonzotech.machines.item.SandpaperItem::new)" in items
assert "CORUNDUM_GRAIN" in items and "SILICON_CRUMBS" in items and "DIAMOND_DUST" in items

# ── гейты: машине — Открытие 3, наждачкис — фулл-гейт 2, корпус остаётся на 2 ──
assert "third_grinder" in tier_three and "THIRD_GRINDER_ITEM" in tier_three
assert "sandpapy" in tier_two and "SANDPAPY" in tier_two
assert "reprocessing_grinder" not in tier_two
assert "empty_hull" in tier_two and "EMPTY_HULL" in tier_two
assert "gonzotech:sandpapy" in unlocks

# ── поедание ключа и мастерство (0.3.253, автор) ──
assert "USE_TICKS = 3600" in wrench
assert "WRENCH_EATEN" in flags and "awardWrenchEaten" in adv
assert "WrenchMasteryPayload" in flow and "sendWrenchMastery" in flow
assert "WrenchItem.isActive(player)" in hud
assert "WrenchItem.hasMastery(player)" in read("machines/network/PipeBlock.java")
assert "WrenchItem.hasMastery(player)" in read("machines/network/CompositePipeBlock.java")

# ── data JSON: крафты и ачивмент ──
assert json.loads((RES / "data/gonzotech/recipe/third_grinder.json").read_text())["result"]["id"] == "gonzotech:third_grinder"
assert json.loads((RES / "data/gonzotech/recipe/corundum_abrasive.json").read_text())["type"] == "minecraft:crafting_shapeless"
assert json.loads((RES / "data/gonzotech/recipe/sandpapy.json").read_text())["result"]["id"] == "gonzotech:sandpapy"
adv_json = json.loads((RES / "data/gonzotech/advancement/wrench_eaten.json").read_text())
assert adv_json["criteria"]["wrench_eaten"]["trigger"] == "minecraft:impossible"

# ── lang ──
for lang in ("ru_ru.json", "en_us.json"):
    data = json.loads((RES / "assets/gonzotech/lang" / lang).read_text())
    for key in ("block.gonzotech.third_grinder", "item.gonzotech.sandpapy",
                "item.gonzotech.corundum_grain", "item.gonzotech.corundum_abrasive",
                "item.gonzotech.silicon_crumbs", "item.gonzotech.diamond_dust",
                "advancements.gonzotech.wrench_eaten.title",
                "advancements.gonzotech.wrench_eaten.description"):
        assert key in data, f"{lang}: {key}"
    assert "block.gonzotech.reprocessing_grinder" not in data

assert _ver.version() == "0.3.257", _ver.version()
print("spent reprocessing + Grinder II contracts OK")
