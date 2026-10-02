#!/usr/bin/env python3
"""PCFSOZ centrifuge (0.3.89, author 04.10.2026): clone of Tsf1UR with the
author's stats; yellow cake item + filler synthesis + isotope split.
Compile-free pin suite (MC-dependent code; the sandbox ECJ gate does not
see Minecraft classes). Author stats: storage 5202 GTU, intake 64/t,
water/steam 388 mB/t, hot water 9000 + hidden 8000, heating 32 mB/t at
3.2 GTU/t, separation 480 t at (5.32 GTU + 32 mB)/tick."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "src/main/java"
RES = ROOT / "src/main/resources"

# ── числа автора в MachineDefs ──
defs = (MAIN / "com/gonzotech/machines/energy/MachineDefs.java").read_text()
pins = {
    "PCFSOZ_GTU_CAPACITY = 5_202 * MILLI": "стор ГТУ 5202",
    "PCFSOZ_GTU_INTAKE = 64 * MILLI": "приём ГТУ 64/т",
    "PCFSOZ_HOT_WATER_CAPACITY = 9_000": "кипяток 9000",
    "PCFSOZ_WATER_CAPACITY = 8_000": "скрытая вода 8000",
    "PCFSOZ_WATER_INTAKE = 388": "приём воды 388/т",
    "PCFSOZ_STEAM_INTAKE = 388": "приём пара 388/т",
    "PCFSOZ_WATER_TO_HOT_WATER_PER_TICK = 32": "нагрев воды 32 mB/т",
    "PCFSOZ_WATER_HEAT_GTU_MILLI_PER_TICK = 3_200": "нагрев 3.2 GTU/т",
    "PCFSOZ_SEPARATION_TICKS = 480": "разделение 480 т",
    "PCFSOZ_HOT_WATER_PER_TICK = 32": "32 mB кипятка/т",
    "PCFSOZ_GTU_MILLI_PER_TICK = 5_320": "5.32 GTU/т",
}
for pin, what in pins.items():
    assert pin in defs, what

# ── машина: регистрации и проводка ──
be = (MAIN / "com/gonzotech/machines/block/entity/PcfsozBlockEntity.java").read_text()
assert "ModBlockEntities.PCFSOZ.get()" in be
assert "PCFSOZRecipes.find(items.get(SLOT_INPUT))" in be
assert "MachineDefs.PCFSOZ_SEPARATION_TICKS" in be
assert '"PendingOutput" + i' in be                       # персист операций, как у ЦФ1УР
assert 'Component.translatable("block.gonzotech.pcfsoz")' in be
blk = (MAIN / "com/gonzotech/machines/block/PcfsozBlock.java").read_text()
assert "PcfsozBlockEntity::serverTick" in blk and "dropPendingOutputsForBreak" in blk
mm = (MAIN / "com/gonzotech/machines/registry/ModMachines.java").read_text()
assert 'BLOCKS.registerBlock("pcfsoz", com.gonzotech.machines.block.PcfsozBlock::new, machineMetal())' in mm
assert 'ITEMS.registerSimpleBlockItem("pcfsoz", PCFSOZ)' in mm
mbe = (MAIN / "com/gonzotech/machines/registry/ModBlockEntities.java").read_text()
assert 'BLOCK_ENTITIES.register("pcfsoz"' in mbe
mmenu = (MAIN / "com/gonzotech/machines/registry/ModMenus.java").read_text()
assert 'MENUS.register("pcfsoz"' in mmenu
tabs = (MAIN / "com/gonzotech/core/registry/ModCreativeTabs.java").read_text()
assert "ModMachines.PCFSOZ_ITEM.get()" in tabs
menu = (MAIN / "com/gonzotech/machines/menu/PcfsozMenu.java").read_text()
assert "ModMenus.PCFSOZ.get()" in menu and "OutputOnlySlot" in menu
screen = (MAIN / "com/gonzotech/machines/client/PcfsozScreen.java").read_text()
assert 'gui("pcfsoz_gui.png")' in screen and "gui.gonzotech.pcfsoz.separation_progress" in screen
assert "PCFSOZ_HOT_WATER_CAPACITY" in screen and "PCFSOZ_GTU_CAPACITY" in screen

# ── рецепты разделения: жёлтый кек -> U-238 + 10 % U-235 ──
rec = (MAIN / "com/gonzotech/machines/processing/PCFSOZRecipes.java").read_text()
assert "YELLOW_CAKE.get()" in rec and "URANIUM_238.get()" in rec
assert "new Byproduct(() -> ModItems.URANIUM_235.get(), 100)" in rec  # 10 %
assert "random.nextInt(CHANCE_SCALE) < chancePermille" in rec        # единственный бросок

# ── жёлтый кек: предмет + пресет радиации ──
items = (MAIN / "com/gonzotech/core/registry/ModItems.java").read_text()
assert 'ITEMS.registerSimpleItem("yellow_cake")' in items
rads = (MAIN / "com/gonzotech/radiation/RadSources.java").read_text()
assert 'Map.entry("yellow_cake", 0.02 * RadUnits.MILLI)' in rads

# ── наполнитель: рецепт №8 ──
filler = (MAIN / "com/gonzotech/machines/block/entity/FillerBlockEntity.java").read_text()
assert "YELLOW_CAKE_TICKS = 240" in filler and "YELLOW_CAKE_ACID_TOTAL = 512" in filler
assert "activeRecipe = 8;" in filler and "case 8 ->" in filler
assert "findGridUranium()" in filler and 'RAW_ORE_ITEMS.get("uranium")' in filler
assert 'INGOT_ITEMS.get("uranium_ingot")' in filler and 'DUST_ITEMS.get("uranium_dust")' in filler
assert "addOutputItem(targetTankIsRight ? 3 : 1, new ItemStack(ModItems.YELLOW_CAKE.get()))" in filler

# ── ассеты ──
assert json.loads((RES / "assets/gonzotech/blockstates/pcfsoz.json").read_text())
assert json.loads((RES / "data/gonzotech/loot_table/blocks/pcfsoz.json").read_text())["type"] == "minecraft:block"
craft = json.loads((RES / "data/gonzotech/recipe/pcfsoz.json").read_text())
assert craft["pattern"] == ["ECT", "AFS", "MNM"] and craft["result"]["id"] == "gonzotech:pcfsoz"
assert craft["key"]["E"] == "gonzotech:energy_module" and craft["key"]["N"] == "gonzotech:nickel_plate"
assert (RES / "assets/gonzotech/items/yellow_cake.json").is_file()
assert (RES / "assets/gonzotech/textures/item/yellow_cake.png").is_file()
assert (RES / "assets/gonzotech/textures/block/pcfsoz/side.png").is_file()
assert (RES / "assets/gonzotech/textures/gui/pcfsoz_gui.png").is_file()
assert (RES / "assets/gonzotech/textures/gui/pcfsoz_gui_bg.png").is_file()
for lang in ("ru_ru", "en_us"):
    data = json.loads((RES / f"assets/gonzotech/lang/{lang}.json").read_text())
    assert data["block.gonzotech.pcfsoz"] and data["item.gonzotech.yellow_cake"]
    assert data["gui.gonzotech.pcfsoz.separation_progress"]

print("PCFSOZ pins passed (5202/64/388, 9000+8000, 32 mB/t @3.2 GTU, 480 t @5.32+32; cake -> U238 + 10% U235)")
