#!/usr/bin/env python3
"""Regression checks for the 0.3.148 filler, capacity, tooltip and litho fixes."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/main/java/com/gonzotech"
checks = 0


def pin(condition, message):
    global checks
    assert condition, f"PIN FAIL: {message}"
    checks += 1


def read(path):
    return (SRC / path).read_text(encoding="utf-8")


def method_body(source, signature):
    start = source.index(signature)
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1:index]
    raise AssertionError(f"unclosed method: {signature}")


# A canister moves up to 128 mB per active tick, stays in its input until empty,
# and an early-full tank therefore leaves all undelivered liquid in that canister.
filler = read("machines/block/entity/FillerBlockEntity.java")
pin("CANISTER_DRAIN_PER_TICK = 128" in filler, "canister transfer rate remains 128 mB/t")
start = filler.index("} else if (in.is(ModItems.CANISTER.get())) {")
end = filler.index("} else if (com.gonzotech.core.item.AmpouleItem.isFilledAmpoule(in))", start)
drain = filler[start:end]
pin("Math.min(Math.min(canAmount, tankSpace), CANISTER_DRAIN_PER_TICK)" in drain,
    "transfer is limited by canister contents, free tank space and per-tick rate")
pin("remaining > 0 || canAcceptItem(out, ModItems.CANISTER.get(), 1)" in drain,
    "output availability is required only when the last liquid is transferred")
pin("items.set(inSlot, resultCan);" in drain and "addOutputItem(outSlot, resultCan);" in drain,
    "partial canister stays above and empty canister moves below")
pin('remaining > 0 ? canFluid : "empty"' in drain,
    "canister is changed to empty only at zero remaining fluid")
pin("out.isEmpty()" not in drain, "occupied output does not block partial draining")

tank, capacity, canister = 4_000, 9_000, 7_000
transferred = 0
while tank < capacity and canister > 0:
    amount = min(canister, capacity - tank, 128)
    tank += amount
    canister -= amount
    transferred += amount
pin((tank, canister, transferred) == (9_000, 2_000, 5_000),
    "4000/9000 mB tank plus 7000 mB canister leaves 2000 mB in the upper canister")

# Chemical-plant storage, packet data, NBT cap and GUI maximum share one capacity.
plant = read("machines/block/entity/ChemicalPlantBlockEntity.java")
plant_menu = read("machines/menu/ChemicalPlantMenu.java")
pin("GTU_CAPACITY = 2_560L" in plant, "chemical plant stores 2560 GTU")
pin("GTU_CAPACITY_MILLI = GTU_CAPACITY * MachineDefs.MILLI" in plant,
    "milli-GTU capacity derives from the 2560-GTU limit")
pin('Math.min(tag.getLong("GtuMilli"), GTU_CAPACITY_MILLI)' in plant,
    "NBT load clamps stored energy to the new limit")
pin("return (int) ChemicalPlantBlockEntity.GTU_CAPACITY;" in plant_menu,
    "chemical-plant gauge maximum uses the storage constant")

# Third air-filter values are integer hundredths on the wire and display as X.Y.
air_screen = read("machines/client/AirFilterScreen.java")
pin(air_screen.count("GtUnits.x1(") == 3, "fuel, catalyst and air-quality tooltips use one decimal")
for field in ("coalUsedHundredths", "catalystUsedHundredths", "qualityHundredths"):
    pin(f"menu.{field}() / 100.0D" in air_screen, f"{field} is scaled from hundredths")
pin("Math.round(menu.coalUsedHundredths() / 100f)" not in air_screen
    and "Math.round(menu.catalystUsedHundredths() / 100f)" not in air_screen
    and "Math.round(menu.qualityHundredths() / 100f)" not in air_screen,
    "tooltips do not round hundredths to whole percentages")

# Prove why the old restoration path lost variants 2 and 3: after forming, every
# non-root location is a shell joker, so layout discovery always reports variant 1.
litho = read("machines/litho/SiliconFactoryStructure.java")
layout_source = litho.split("public static final char[][] LAYOUTS = {", 1)[1].split("};", 1)[0]
layouts = [re.findall(r"'([DRCBS])'", line) for line in layout_source.splitlines() if "{'" in line]
pin(len(layouts) == 3 and all(len(layout) == 18 for layout in layouts),
    "all three saved lithography layouts are present")
root_slot = 13
for selected_variant in (1, 2, 3):
    matching_variants = [index + 1 for index, layout in enumerate(layouts)
                         if all((slot == root_slot and layout[slot] == "S")
                                or (slot != root_slot and layout[slot] != "S")
                                for slot in range(18))]
    pin(selected_variant in matching_variants and matching_variants[0] == 1,
        f"shell joker makes saved variant {selected_variant} look like variant 1")

saved_layout = method_body(litho, "private static boolean hasValidSavedLayout(")
restore = method_body(litho, "static boolean restoreController(")
pin("controller.variant()" in saved_layout and "controller.originalState(member).getBlock()" in saved_layout,
    "restore validates the saved variant against original block data")
pin("hasValidSavedLayout(controller, origin)" in restore and "validateBox(level, origin)" not in restore,
    "restore no longer guesses the variant from shell blocks")
pin("SiliconFactoryShellBlock.SLICE, slot" in restore
    and "SiliconFactoryShellBlock.VARIANT, controller.variant()" in restore,
    "restored shell states receive their saved slice and variant")

litho_be = read("machines/litho/SiliconFactoryBlockEntity.java")
save = method_body(litho_be, "protected void saveAdditional(")
load = method_body(litho_be, "protected void loadAdditional(")
pin('tag.putLong("Gtu", gtu.amountAsLong())' in save
    and "ContainerHelper.saveAllItems(tag, items, registries)" in save,
    "formed lithography inventory and GTU are serialized")
pin('tag.putBoolean("Formed", true)' in save and 'tag.getBoolean("Formed")' in load
    and "restorePending = true;" in load,
    "formed metadata schedules recovery on world load")
tick = method_body(litho_be, "public static void serverTick(")
pin(tick.index("restoreController(server, be)") < tick.index("IDLE_MILLI_PER_TICK"),
    "recovery waits for loaded chunks before leakage or production ticks")

print(f"0.3.148 regression checks passed: {checks}")
