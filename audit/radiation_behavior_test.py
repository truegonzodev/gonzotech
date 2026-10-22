#!/usr/bin/env python3
"""Static and numerical contracts for 0.3.232 radiation flows and medicines."""
from pathlib import Path
import math

import _ver

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

# 0.3.271 (dupe guard): physical writes freeze while a player clicks a container;
# dose math continues from virtual values.
watch = read("radiation/RadiationClickWatch.java")
assert "FREEZE_TICKS" in watch and "markClick" in watch
mixin = (ROOT / "src/main/java/com/gonzotech/mixin/ContainerClickRadiationMixin.java").read_text(encoding="utf-8")
assert "handleContainerClick(Lnet/minecraft/network/protocol/play/ServerboundContainerClickPacket;)V" in mixin
assert "clickFrozen = RadiationClickWatch.recentlyClicked(player, nowTick)" in system
assert "clickFrozen = RadiationClickWatch.worldFrozen(level, nowTick)" in system
assert "if (!clickFrozen) changed |=" in system, "BE-контейнеры пишут в окне клика"
assert "!frozen && (periodicFlush || !hardUse && !inSoftCd)" in system
assert "peekInducedAt" in item_rad and "clickFrozen\n                            ? ItemRadioactivity.peekInducedAt" in system

# Inventory dose is rebuilt after the tick, so direct induced activity is counted once.
assert "double totalNzt = 0.0D;" in system
# 0.3.249 (idea of the author): the inventory cannot contaminate the chunk
# above the strongest stack's own emission — the "items -> chunk -> items" loop
# no longer amplifies. The author's report: a chunk above 30 mZt plus 2+
# occupied slots used to be a 100% infinite dupe.  0.3.268 (author's
# rebalance): the hard 10 mZt plateau knife is replaced by one smooth
# "of current" law — gain costs (1-0.00097)^L per stack dose and the loss
# fraction is 25%*tanh(L/33).
_ver.at_least("0.3.249", "induced self-discharge and strongest-stack chunk cap")
assert "INVENTORY_TO_CHUNK_RATE * inventoryContourFactor * INVENTORY_TRANSFER_EFFICIENCY" in system
assert "double inventoryTarget = Math.min(totalNzt, strongestStackNzt);" in system
assert "UPTAKE_COST = 0.00097" in item_rad
assert "SELF_DISCHARGE = GROWTH - 1.0" in item_rad
assert "DISCHARGE_ANCHOR = 33.0" in item_rad
assert "1.0 - UPTAKE_COST, seeded" in item_rad
assert "plateauDischarge" not in item_rad
assert "restDecay(current, steps)" in item_rad
assert "Math.pow(1.0D / GROWTH" not in item_rad

# Reference numbers of the new law (mirrors the Java formulas, values in mZt).
def _discharge(l):
    return l * (1.0 - 0.25 * math.tanh(l / 33.0))

def _step(l, src):
    if l > src or src < 1.0:
        return _discharge(l)
    seed = max(l, 1.0)
    return _discharge(min(seed + seed * 0.25 * (1.0 - seed / src) * (1.0 - 0.00097) ** seed, src))

# Author's cooling row: 5 Zt -> ~3.75 -> ~2.81 -> ~2.11 within three seconds.
assert 3600 < _discharge(5000.0) < 3900
assert 2700 < _discharge(_discharge(5000.0)) < 2950
# Mutual 64-stack hold converges to the author's "~33 mZt average plateau"
# instead of running away to the cap.
a = b = 5000.0
for _ in range(40000):
    a, b = _step(a, min(b * 64.0, 5000.0)), _step(b, min(a * 64.0, 5000.0))
assert 15.0 < a < 60.0, f"mutual 64-stack hold no longer saturates: {a}"
# Single radium billet (75 mZt) holds neighbours near 20 mZt, not hundreds.
c = 1.0
for _ in range(40000):
    c = _step(c, 75.0)
assert 10.0 < c < 40.0, 'radium-adjacent equilibrium off the author scale: %s' % c
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
_ver.at_least("0.3.232", "radiation behavior contracts")

print("radiation 0.3.232 flows, convergence, medication table and chunk-shed contracts passed")
