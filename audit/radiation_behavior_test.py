#!/usr/bin/env python3
"""Static and numerical contracts for 0.3.232 radiation flows and medicines."""
from pathlib import Path
import math

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"


def read(relative: str) -> str:
    return (JAVA / relative).read_text(encoding="utf-8")


system = read("radiation/RadiationSystem.java")
item_rad = read("radiation/ItemRadioactivity.java")
chunk = read("radiation/ChunkRadiationData.java")
lead_be = read("machines/block/entity/ThirdLeadChestBlockEntity.java")
lead_block = read("machines/block/ThirdLeadChestBlock.java")
rad_cleanse = read("radiation/RadCleanse.java")
rad_dose = read("radiation/RadDose.java")
dtpa = read("radiation/DtpaItem.java")
pentacin = read("radiation/PentacinItem.java")
cysteamine = read("radiation/CysteamineItem.java")
absorbent = read("radiation/RadAbsorbentItem.java")
docs = (ROOT / "docs/RADIATION-BEHAVIOR-0.3.232.md").read_text(encoding="utf-8")
version = (ROOT / "gradle.properties").read_text(encoding="utf-8")

# Inventory dose is rebuilt after the tick, so direct induced activity is counted once.
assert "double totalNzt = 0.0D;" in system
assert "totalNzt, INVENTORY_TO_CHUNK_RATE * inventoryContourFactor" in system
assert "double inventoryContourFactor = tracing || totalNzt > contam" in system
assert "RadSources.emissionDeep(stack);" in system

# Ordinary containers and final finite items use their contour factor on the 1% transfer.
assert "claimContentScan(level, chunkKey, nowTick)" in system
assert "decayInducedDeepLazily(stack, nowTick)" in system
assert "CHEST_TO_CHUNK_RATE * contourFactor" in system
assert "double transfer = transferTowardChunk(" in system
assert "out[0] += transfer;" in system
assert "container.setChanged()" in system
assert "decayInducedSteps(stack, steps);" in item_rad
assert "Math.pow(1.0D / GROWTH, steps)" in item_rad
assert "ItemContainerContents.fromItems(slots)" in item_rad
assert "`BUNDLE_CONTENTS` входит в расчёт эмиссии" in docs
assert "их наведёнка не затухает" in docs
assert "serverTick" not in lead_be and "getTicker" not in lead_block

# Lead formula is source ×0.2 × contour first, then 1% of max(target-C, 0).
assert "preContourEmission = mine" in system
assert "effectiveEmission = preContourEmission * contourFactor" in system
assert "effectiveEmission, CHEST_TO_CHUNK_RATE" in system
assert "CONTENT_CHUNK_FACTOR = 0.2" in lead_block

# Finite floor/frame stacks: 1% × contour × max(M-C, 0); infinite BlockItems converge like placed blocks.
assert "ItemEntity.class, box," in system and "ItemFrame.class, box," in system
assert "CHEST_TO_CHUNK_RATE * contourFactor" in system
assert "convergePlacedLikeEntitySource(level, chunkKey, floor.blockLikeTarget(), floorElapsed)" in system
assert "blockEmission * stack.getCount() * contourFactor" in system
assert "PLACED_UPDATE_PERIOD_TICKS" in chunk and "PLACED_CONVERGE = 0.05" in chunk
assert "Math.pow(1.0D - PLACED_CONVERGE" in chunk

# Medicines are intentionally distinct: current-dose removal vs prevention of new dose.
assert "DOSE_REMAINING = 0.98" in dtpa
assert "COURSE_DOSE_REMAINING = 0.2" in dtpa
assert "BONUS_CLEANSE_AMPLIFIER = 3" in dtpa
assert "DOSE_REMAINING = 0.8" in pentacin
assert "CLEANSE_AMPLIFIER = 1" in pentacin
assert "ModEffects.DOSE_ABSORPTION" in cysteamine and "ABSORPTION_AMPLIFIER = 1" in cysteamine
assert "ABSORPTION_SECONDS = 8 * 60" in cysteamine
assert "ModEffects.RAD_CLEANSE" in absorbent
assert "psyche.setRadiation(dose - applied)" in rad_cleanse
assert "return applied;" in rad_cleanse
assert "cleansed * NZT_PER_PERMILLE * SHED_TO_CHUNK" in system
assert "SHED_CEILING = 100.0 * RadUnits.MILLI" in system
assert "cleanseContamination = data.contaminationOf(chunkKey)" in system
assert "absorptionFactor = (100 - cut) / 100.0" in system
assert "data.addContamination(level, chunkKey, transfer, \"Очищение/RadCleanse игрока → чанк\")" in system

# Numerical reference values for the implemented multiplicative current-dose cleanse.
def cleanse_fraction(amplifier: int, seconds: int) -> float:
    per_second = 200 * (amplifier + 1) / (1000 * 30)
    return 1.0 - (1.0 - per_second) ** seconds

assert math.isclose(0.98 * 0.2, 0.196)
assert 0.18 < cleanse_fraction(0, 30) < 0.19  # absorbent I
assert 0.12 < cleanse_fraction(1, 10) < 0.13  # Pentacin II
assert 0.23 < cleanse_fraction(3, 10) < 0.24  # sixth DTPA dose, cleanse IV

# Human-readable table and explicit transfer distinctions stay part of the release.
for phrase in ("ДТПА — отдельно", "Пентасин", "Цистеамин", "Антирадиновый абсорбент",
               "текущую дозу не уменьшает", "не передаются в чанк",
               "RadCleanse", "0.01 × max(0.2 × M × F_ящика − C, 0)"):
    assert phrase in docs, phrase
assert "mod_version=0.3.232" in version

print("radiation 0.3.232 flows, convergence, medication table and chunk-shed contracts passed")
