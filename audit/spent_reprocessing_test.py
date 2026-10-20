#!/usr/bin/env python3
"""Static contracts for 0.3.250 spent-TVEL reprocessing and the reprocessing grinder."""
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
reprocessing = read("machines/processing/ReprocessingRecipes.java")
be = read("machines/block/entity/ReprocessingGrinderBlockEntity.java")
menu = read("machines/menu/ReprocessingGrinderMenu.java")
screen = read("machines/client/ReprocessingGrinderScreen.java")
machines = read("machines/registry/ModMachines.java")
block_entities = read("machines/registry/ModBlockEntities.java")
menus = read("machines/registry/ModMenus.java")
rad_sources = read("radiation/RadSources.java")
defs = read("machines/energy/MachineDefs.java")
structure = read("machines/reactor/ReactorStructure.java")

# ── chain: spent TVEL -> slags -> zirconium oxide / reactor plutonium / U-238 ──
assert "EXTREMELY_DANGEROUS_SLAG" in items and "MODERATELY_DANGEROUS_SLAG" in items
assert "ZIRCONIUM_OXIDE" in items and "EMPTY_HULL" in items
assert "ModItems.DEPRESSED_UF_TVEL.get(), ModItems.EXTREMELY_DANGEROUS_SLAG.get()" in grinder

# Extreme slag separates into U-238 x2 guaranteed plus plutonium and zirconium
# oxide byproducts; the moderate slag yields less and never zirconium.
assert 'explicit(main(modItem(ModItems.URANIUM_238), 2), List.of(modItem(ModItems.EXTREMELY_DANGEROUS_SLAG))' in centrifuge
assert 'b(dust("plutonium_dust"), 450), b(modItem(ModItems.ZIRCONIUM_OXIDE), 350), none())' in centrifuge
assert 'explicit(main(modItem(ModItems.URANIUM_238)), List.of(modItem(ModItems.MODERATELY_DANGEROUS_SLAG))' in centrifuge
assert 'b(dust("plutonium_dust"), 250), none(), none())' in centrifuge

# The reprocessing grinder: spent TVEL + empty hull -> moderate slag + waste barrel.
assert "acceptsTvel" in reprocessing and "acceptsHull" in reprocessing
assert "MODERATELY_DANGEROUS_SLAG" in reprocessing and "WASTE_BARREL_ITEM" in reprocessing
assert reprocessing.index("DEPRESSED_UF_TVEL") < reprocessing.index("EMPTY_HULL")

# Radioactivity presets: extreme slag strongly active, moderate slag mild.
assert 'Map.entry("extremely_dangerous_slag", 15.0 * RadUnits.MILLI)' in rad_sources
assert 'Map.entry("moderately_dangerous_slag", 1.5 * RadUnits.MILLI)' in rad_sources

# ── the machine: two inputs, two protected outputs, grinder pacing ──
assert "SLOT_TVEL_INPUT = 0" in be and "SLOT_HULL_INPUT = 1" in be
assert "SLOT_SLAG_OUTPUT = 2" in be and "SLOT_BARREL_OUTPUT = 3" in be
assert "SecondTierDefs.GRINDER_TICKS" in be and "SecondTierDefs.GRINDER_GTU_MILLI_PER_TICK" in be
assert "ReprocessingRecipes.find(items.get(SLOT_TVEL_INPUT), items.get(SLOT_HULL_INPUT))" in be
assert "dropPendingOutputForBreak" in be and "PendingSlag" in be and "PendingBarrel" in be
assert "SLOT_TVEL_INPUT, 35, 26" in menu and "SLOT_HULL_INPUT, 35, 44" in menu
assert "SLOT_SLAG_OUTPUT, 116, 26" in menu and "SLOT_BARREL_OUTPUT, 116, 44" in menu
assert 'machineGui("reprocessing_grinder_gui_bg.png", "reprocessing_grinder_gui_bg_neo.png")' in screen
assert 'machineGui("reprocessing_grinder_gui.png", "reprocessing_grinder_gui_neo.png")' in screen

# Wiring: block/item/BE/menu/screen all registered under one id.
assert 'registerBlock("reprocessing_grinder"' in machines
assert 'registerSimpleBlockItem("reprocessing_grinder"' in machines
assert 'register("reprocessing_grinder"' in block_entities
assert 'MENUS.register("reprocessing_grinder"' in menus
assert "REPROCESSING_GRINDER.get(), ReprocessingGrinderScreen::new" in read("machines/client/MachineClient.java")

# Empty hull is crafted from zirconium plates and lead; gated behind Discovery 2.
hull_recipe = json.loads((RES / "data/gonzotech/recipe/empty_hull.json").read_text(encoding="utf-8"))
assert hull_recipe["result"]["id"] == "gonzotech:empty_hull"
assert set(hull_recipe["key"].values()) == {"gonzotech:zirconium_plate", "gonzotech:lead_ingot"}
tier_two = read("machines/crafting/TierTwoCrafting.java")
assert '"gonzotech:reprocessing_grinder"' in tier_two and '"gonzotech:empty_hull"' in tier_two

# ── 0.3.250: reactor generation-I gives at most 16,364 GTH/t per single port ──
assert "REACTOR_GTH_PER_PORT_MILLI = 16_364 * MILLI" in defs
assert "return com.gonzotech.machines.energy.MachineDefs.REACTOR_GTH_PER_PORT_MILLI;" in structure
assert "throughputLimit(state, PipeType.HEAT)" not in structure.split("gthPortLimit", 1)[1][:600]

_ver.at_least("0.3.250", "spent-TVEL reprocessing chain, reprocessing grinder, 16,364 GTH/t reactor port cap")

print("spent reprocessing 0.3.250: slag chain, reprocessing grinder, hull recipe and port cap contracts passed")
