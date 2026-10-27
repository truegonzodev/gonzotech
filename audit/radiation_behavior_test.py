#!/usr/bin/env python3
"""Current radiation wiring + unchanged medicine contracts; production core runs separately."""
from pathlib import Path
import math

import _ver
import _radiation_contracts as current

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
docs = (ROOT / "docs/RAD_ITEMS.md").read_text(encoding="utf-8")
world_docs = (ROOT / "docs/RAD_WORLD.md").read_text(encoding="utf-8")
# 0.3.272: серверный дебаггер — дельта серверной суммы на клик + лог проходов.
cfg = (ROOT / "src/main/java/com/gonzotech/core/config/GonzoServerConfig.java").read_text(encoding="utf-8")
assert "radiationInventoryDebug" in cfg
_mixin272 = (ROOT / "src/main/java/com/gonzotech/mixin/ContainerClickRadiationMixin.java") \
    .read_text(encoding="utf-8")
assert "gonzotech$snapshot" in _mixin272 and 'at = @At(\'RETURN\')'.replace("\'", chr(34)) in _mixin272
assert "RAD_INV_LOG" in system and "debugInv" in system

# The cancelled pause/cursor mechanisms remain forbidden. Dose is now external:
# copy/merge carry it immediately, without held-cache, physical NBT or quiet flush.
assert "player.containerMenu.getCarried()" not in system, "carried опять в проходе (N3!)"
assert "case 3" not in system, "carried-компартмент 273 отменён (N3)"
assert "RadiationClickWatch" not in system and not (ROOT / "src/main/java/com/gonzotech/radiation/RadiationClickWatch.java").exists(), "стража пауз жива"
assert "menuOpen" not in system and "clickFrozen" not in system and "isContainerViewed" not in system
assert "peekInducedAt" not in item_rad and "peekInducedAt" not in system
assert "LAST_PASS_LOG_NS" in system, "нет реалтаймового шлюза лога проходов (flood на 2000 tps)"
current.external_storage()
current.scheduler()
current.flows()
mixin = (ROOT / "src/main/java/com/gonzotech/mixin/ContainerClickRadiationMixin.java").read_text(encoding="utf-8")
# 1.21.4 play-пакеты в protocol.game; цель миксина — по имени (уникально), без хрупкого дескриптора.
assert 'method = "handleContainerClick"' in mixin
assert "protocol/play" not in mixin
assert "ПРИМЕНЁН" in mixin and "markClick" not in mixin
assert "RadiationClickWatch." not in mixin  # javadoc упоминает удаление — вызовов нет

# Approved 314/315 law and cluster flow, not the historical 232/268 fixture.
# Exact numerical behavior is executed in radiation_engine_test.py against real
# production Java (including 5000-step feedback and mutation sensitivity).
_ver.at_least("0.3.315", "physical-owner cluster radiation contracts")
for law in ("UPTAKE_COST = 0.0013", "SELF_DISCHARGE = GROWTH - 1.0",
            "DISCHARGE_ANCHOR = 33.0 * RadUnits.MILLI", "CLEANUP_FLOOR = RadUnits.MILLI",
            "INDUCED_REEMIT = 0.75", "seeded / RadUnits.MILLI", "restDecay(current, steps)"):
    assert law in item_rad, law
assert "plateauDischarge" not in item_rad
assert "Math.pow(1.0D / GROWTH" not in item_rad
assert "serverTick" not in lead_be and "getTicker" not in lead_block
# Placed-like BlockItems keep their separate 5%/20s convergence path.
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

# Current profiles distinguish direct dose removal, prevention, and chunk shed.
for phrase in ("ДТПА", "Пентасин", "Цистеамин", "абсорбент", "RadCleanse"):
    assert phrase in world_docs, phrase
for phrase in ("0.0013", "presetPerItem + 0.75 × inducedPerItem", "0.01 × max(0.2 × T × F − C, 0)"):
    assert phrase in docs, phrase
print("current radiation wiring/storage/flows and unchanged medication contracts passed")
