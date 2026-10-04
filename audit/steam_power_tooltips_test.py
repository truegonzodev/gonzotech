#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""SteamGen 0.3.142 balance curve, exchanger efficiency, and tooltip contract.

Checks the six author-provided platinum targets, interpolated profiles, material
quality anchors, machine-side headroom, two-decimal nominal costs, and retained turbine/pipe
conversion and throughput contracts.

Run: python3 audit/steam_power_tooltips_test.py
"""
import json
import math
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failures = []


def pin(condition, message):
    if not condition:
        failures.append(message)


def read(relative):
    return (ROOT / relative).read_text(encoding="utf-8")


def integer(source, name, expression=r"([0-9_]+)"):
    match = re.search(rf"\b{name}\s*=\s*{expression}\s*;", source)
    assert match, f"не найдена константа {name}"
    return int(match.group(1).replace("_", ""))


def int_array(source, name):
    match = re.search(rf"\b{name}\s*=\s*\{{([^}}]*)\}}", source, re.S)
    assert match, f"не найден массив {name}"
    return [int(value.replace("_", "")) for value in
            re.findall(r"(?<![A-Za-z0-9_])([0-9][0-9_]*)(?:L)?", match.group(1))]


machine_defs = read("src/main/java/com/gonzotech/machines/energy/MachineDefs.java")
second_defs = read("src/main/java/com/gonzotech/machines/energy/SecondTierDefs.java")
pipe_type = read("src/main/java/com/gonzotech/machines/network/PipeType.java")
pipe_ledger = read("src/main/java/com/gonzotech/machines/network/PipeFlowLedger.java")
universal_node = read("src/main/java/com/gonzotech/machines/network/UniversalNodeBlock.java")
second_universal = read("src/main/java/com/gonzotech/machines/network/SecondUniversalNodeBlock.java")
steam_math = read("src/main/java/com/gonzotech/machines/steamgen/SteamGenMath.java")
steam_be = read("src/main/java/com/gonzotech/machines/block/entity/SteamGenCoreBlockEntity.java")
steam_screen = read("src/main/java/com/gonzotech/machines/client/SteamGenScreen.java")
turbine_math = read("src/main/java/com/gonzotech/machines/turbine/TurbineMath.java")
turbine_screen = read("src/main/java/com/gonzotech/machines/client/TurbineScreen.java")
steam_structure = read("src/main/java/com/gonzotech/machines/steamgen/SteamGenStructure.java")
alloy_catalog = read("src/main/java/com/gonzotech/machines/processing/AlloyMaterialCatalog.java")
heat_exchangers = read("src/main/java/com/gonzotech/machines/steamgen/SteamGenHeatExchangers.java")
units = read("src/main/java/com/gonzotech/core/text/GtUnits.java")

# Heat-port caps and summation semantics are intentionally unchanged.
pin("scaled(carrier.throughputLimit(state, type), carrier, state, type)" in pipe_ledger
    and "Math.floor(limit * f)" in pipe_ledger and "Math.max(1L" in pipe_ledger
    and ": limit;" in pipe_ledger,
    "PipeFlowLedger effective throughput semantics changed; re-check heat-port maxima")
milli = integer(machine_defs, "MILLI")
steamgen_port_cap = integer(machine_defs, "STEAMGEN_GTH_PER_PORT_MILLI",
                            r"([0-9_]+)\s*\*\s*MILLI") * milli
first_heat = int(re.search(
    r'HEAT\("first_heat_pipe"[^\n]*?,\s*([0-9_]+)\s*\*\s*1000,', pipe_type
).group(1).replace("_", ""))
second_heat = integer(second_defs, "HEAT_THROUGHPUT",
                      r"([0-9_]+)L\s*\*\s*MachineDefs\.MILLI") * milli
first_universal_factor = float(re.search(
    r"THROUGHPUT_FACTOR\s*=\s*([0-9.]+);", universal_node
).group(1))
second_universal_factor = float(re.search(
    r"THROUGHPUT_FACTOR\s*=\s*([0-9.]+)D;", second_universal
).group(1))
pin(steamgen_port_cap == 312 * milli, "SteamGen heat-port cap must remain 312 GTH/t")


def port_limit_milli(base, factor):
    node_limit = max(1, math.floor(base * factor)) if factor < 1.0 else base
    return min(node_limit, steamgen_port_cap)


pin(port_limit_milli(first_heat * 1000, 1.0) / milli == 256,
    "T1 heat node should cap a port at 256 GTH/t")
pin(port_limit_milli(second_heat, 1.0) / milli == 312,
    "T2 heat node should cap at 312 GTH/t")
pin(port_limit_milli(first_heat * 1000, first_universal_factor) / milli == 230.4,
    "T1 universal node should still apply throughputFactor 0.9")
pin(port_limit_milli(second_heat, second_universal_factor) / milli == 312,
    "T2 universal node should still be capped at 312 GTH/t")
pin("public static int maxGthIntakeMilli(Level level, long[] heatPorts)" in steam_structure,
    "SteamGenStructure must sum the active heat-port intake limits")
for required in ("carrier.throughputLimit(state, PipeType.HEAT)",
                 "carrier.throughputFactor(state, PipeType.HEAT)",
                 "Math.floor(base * factor)",
                 "MachineDefs.STEAMGEN_GTH_PER_PORT_MILLI"):
    pin(required in steam_structure, f"max GTH intake misses pipe semantics: {required}")
pin("case 9 -> maxGthIntakeMilli;" in steam_be
    and 'tag.putInt("MaxGthIntakeMilli", maxGthIntakeMilli)' in steam_be
    and 'tag.getInt("MaxGthIntakeMilli")' in steam_be,
    "cached max GTH intake must be synchronized and persisted")

# Author-approved platinum control points, keyed by exchanger count.
curve_exchangers = int_array(steam_math, "CURVE_EXCHANGERS")
gth_curve = int_array(steam_math, "PLATINUM_GTH_PER_CYCLE_MILLI")
water_curve = int_array(steam_math, "PLATINUM_WATER_PER_CYCLE")
steam_curve = int_array(steam_math, "PLATINUM_STEAM_PER_CYCLE_MILLI")
expected_exchangers = [0, 2, 5, 12, 22, 26]
expected_gth = [1_273_000, 1_388_000, 1_503_000, 1_081_000, 698_000, 429_000]
expected_water = [863, 911, 921, 719, 498, 233]
expected_steam = [319_000, 384_000, 466_000, 353_000, 237_000, 156_000]
pin(curve_exchangers == expected_exchangers, "platinum curve anchor exchanger counts changed")
pin(gth_curve == expected_gth, "platinum GTH targets do not match the approved six points")
pin(water_curve == expected_water, "platinum water targets do not match the approved six points")
pin(steam_curve == expected_steam, "platinum steam targets do not match the approved six points")


def interpolate(values, exchangers):
    count = max(0, min(26, exchangers))
    for index in range(1, len(curve_exchangers)):
        right = curve_exchangers[index]
        if count <= right:
            left = curve_exchangers[index - 1]
            span = right - left
            offset = count - left
            return (values[index - 1] * (span - offset) + values[index] * offset
                    + span // 2) // span
    return values[-1]


# The source implementation mirrors the rounded integer interpolation and
# completes every target in one cycle/tick.
for exchanger, gth, water, steam in zip(expected_exchangers, expected_gth,
                                        expected_water, expected_steam):
    pin(interpolate(gth_curve, exchanger) == gth,
        f"GTH anchor mismatch at {exchanger} exchangers")
    pin(interpolate(water_curve, exchanger) == water,
        f"water anchor mismatch at {exchanger} exchangers")
    pin(interpolate(steam_curve, exchanger) == steam,
        f"steam anchor mismatch at {exchanger} exchangers")
for exchangers in range(27):
    pin(interpolate(gth_curve, exchangers) > 0
        and interpolate(water_curve, exchangers) > 0
        and interpolate(steam_curve, exchangers) > 0,
        f"interpolated profile must stay positive at {exchangers} exchangers")

# The chosen material-quality curve passes exactly through the requested
# platinum / gold (-30%) / redstone (-50%) anchors.
def efficiency_permille(sum_ch, count):
    if count <= 0:
        return 1000
    average = max(0.0, sum_ch / count)
    if average >= 125.0:
        value = 0.70 + (average - 125.0) / 150.0 * 1.80
    else:
        value = 0.50 + (average - 95.0) / 150.0
    return round(max(0.10, min(1.0, value)) * 1000)


for name, total, expected in (("platinum", 150, 1000),
                              ("gold", 125, 700),
                              ("redstone", 95, 500)):
    pin(efficiency_permille(total, 1) == expected,
        f"{name} output factor does not match its selected anchor")
pin("average >= 125.0D" in steam_math and "0.70D" in steam_math
    and "average - 95.0D" in steam_math and "0.10D" in steam_math,
    "Java exchanger quality curve or its 10% floor changed")
pin("Math.round(platinumOutput * (double) exchangerEfficiencyPermille" in steam_math,
    "fractional material output should accumulate without rounding each cycle down")

# Parse every metal block's actual stats. Vanilla HX stats are intentionally
# overridden in SteamGenHeatExchangers (not the alloy catalog's vanilla stats).
materials = {}
for name, args in re.findall(r'builder\.gonzo\("([^"]+)"\s*,\s*([^)]*)\)', alloy_catalog):
    values = [int(value.replace("_", "")) for value in
              re.findall(r"(?<![A-Za-z0-9_])([0-9][0-9_]*)",
                         args.split("0x", 1)[0])]
    if len(values) >= 7:
        materials[name] = (values[3], values[4])
vanilla_stats = {}
for block, conductivity, heat in re.findall(
    r"VANILLA_EXCHANGERS\.put\(Blocks\.([A-Z_]+)_BLOCK, new Stats\((\d+),\s*(\d+)\)",
    heat_exchangers,
):
    vanilla_stats[block.lower()] = (int(conductivity), int(heat))
for name in ("iron", "copper", "gold", "diamond", "redstone"):
    pin(name in vanilla_stats, f"missing explicit vanilla exchanger stats for {name}")
    materials[name] = vanilla_stats[name]
pin(len(materials) == 52, f"expected 47 mod metals + 5 vanilla exchanger blocks, got {len(materials)}")
pin(materials.get("platinum") == (75, 75), "platinum HX must remain C75/H75")
pin(materials.get("gold") == (95, 30), "SteamGen gold stats should remain C95/H30")
pin(materials.get("redstone") == (55, 40), "SteamGen redstone stats should remain C55/H40")
for name, (conductivity, heat) in materials.items():
    total = conductivity + heat
    factor = efficiency_permille(total, 1)
    if name == "platinum":
        pin(factor == 1000, "platinum must be the unique 100% exchanger")
    else:
        pin(factor < 1000, f"non-platinum exchanger {name} must be worse than platinum")

# Check all six operating points fit the rebalanced machine buffers and
# machine-side I/O caps. GTH ports remain independently capped at 312 GTH/t.
water_capacity_per_core = integer(machine_defs, "STEAMGEN_WATER_CAPACITY_PER_CORE")
steam_capacity_per_core = integer(machine_defs, "STEAMGEN_STEAM_CAPACITY_PER_CORE")
gth_capacity_per_core = integer(machine_defs, "STEAMGEN_GTH_CAPACITY_PER_CORE")
fluid_io_per_core = integer(machine_defs, "STEAMGEN_FLUID_IO_PER_CORE")
fluid_io_min = integer(machine_defs, "STEAMGEN_FLUID_IO_MINIMUM")
pin(water_capacity_per_core == 256 and steam_capacity_per_core == 512
    and gth_capacity_per_core == 1536,
    "rebalanced per-core storage should be 256 W / 512 steam / 1536 GTH")
for cores, exchangers, gth_milli, water, steam_milli in zip(
    [27, 25, 22, 15, 5, 1], expected_exchangers,
    expected_gth, expected_water, expected_steam,
):
    gth = gth_milli // milli
    steam = steam_milli // milli
    fluid_io = max(fluid_io_min, cores * fluid_io_per_core)
    pin(cores * water_capacity_per_core >= water,
        f"water buffer cannot hold one target cycle in {cores}/{exchangers}")
    pin(cores * steam_capacity_per_core >= steam,
        f"steam buffer cannot hold one target cycle in {cores}/{exchangers}")
    pin(cores * gth_capacity_per_core >= gth,
        f"GTH buffer cannot hold one target cycle in {cores}/{exchangers}")
    pin(fluid_io >= water and fluid_io >= steam,
        f"machine fluid-I/O cannot support {cores}/{exchangers}")
    pin(math.ceil(gth / 312) <= 10,
        f"target requires over ten full Tier-2 heat ports in {cores}/{exchangers}")
pin("return Math.max(MachineDefs.STEAMGEN_FLUID_IO_MINIMUM" in steam_math,
    "single-core fluid-I/O should be raised enough to accept the 233 mB/t target")
pin(integer(machine_defs, "STEAMGEN_GTH_LOSS") == 667
    and integer(machine_defs, "STEAMGEN_STEAM_LOSS") == 1,
    "passive loss constants changed without updating the rebalance record")

# Server executes the same single profile cycle that the client rates.
pin("SteamGenMath.waterPerCycle(cores, precious)" in steam_be
    and "SteamGenMath.gthPerCycleMilli(cores, precious)" in steam_be
    and "SteamGenMath.steamPerCycleMilli(cores, sumCH, precious)" in steam_be,
    "SteamGen BE must use the target curve for all three cycle resources")
pin("eventRemainderMilli" not in steam_be and 'tag.getInt("EventRemainder")' not in steam_be,
    "old fractional-event engine should not survive the one-cycle-per-tick rebalance")
pin("SteamGenMath.maxSteamBurstPerTickMilli(menu.cores(), menu.sumCH(), menu.precious())" in steam_screen,
    "rated output tooltip must use the same exchanger-aware profile as the server")

# Nominal costs alone use hundredths; rated throughput remains a whole-mB
# scale. Resource colors, units, and /t suffixes are still assembled by GtUnits.
pin(steam_screen.count('String.format(Locale.ROOT, "%.2f"') == 2,
    "both nominal SteamGen spending tooltips must show two decimal places")
pin('String.format(Locale.ROOT, "%.0f", ratedMilli / 1000.0D)' in steam_screen
    and 'String.format(java.util.Locale.ROOT, "%.0f", ratedMilli / 1000.0D)' in turbine_screen,
    "rated throughput formatting must remain separate from nominal-cost precision")
pin(turbine_screen.count('String.format(Locale.ROOT, "%.2f"') == 1,
    "turbine nominal steam-per-GTU spending must show two decimal places")
pin("ticked(value, GTH)" in units and "return rateLine(\"gui.gonzotech.steamgen.rated\"" in units,
    "GTH-intake and steam-output throughput tooltips must retain their /t suffix")
for required in ("key(U_GTH, GTH)", "key(N_STEAM_GENITIVE, STEAM)", "num(value, GTH)",
                 "key(N_WATER_GENITIVE, WATER)", "num(value, WATER)", "mb(WATER)",
                 "withStyle(ChatFormatting.WHITE)"):
    pin(required in units, f"GtUnits missing color/unit contract: {required}")

# Turbine conversion and its nominal tooltip use the 0.3.147 target: 32 mB/GTU.
reference_mb = integer(machine_defs, "TURBINE_REFERENCE_STEAM_MB")
gtu_reference_milli = integer(machine_defs, "TURBINE_GTU_PER_REFERENCE_STEAM_MILLI")
nominal_mb_per_gtu = reference_mb * milli / gtu_reference_milli
pin("nominalSteamMbPerGtu" in turbine_math
    and "TURBINE_REFERENCE_STEAM_MB" in turbine_math
    and "TURBINE_GTU_PER_REFERENCE_STEAM_MILLI" in turbine_math,
    "TurbineMath must retain its exact nominal steam-per-GTU conversion")
pin(math.isclose(nominal_mb_per_gtu, 32.0, rel_tol=1e-12),
    "0.3.147 turbine conversion should be exactly 32 mB/GTU (48 mB → 1.5 GTU)")
pin("TurbineMath.nominalSteamMbPerGtu()" in turbine_screen
    and "steamConsumed()" not in turbine_screen,
    "turbine tooltip should continue showing nominal, not last-tick, spending")

ru = json.loads(read("src/main/resources/assets/gonzotech/lang/ru_ru.json"))
en = json.loads(read("src/main/resources/assets/gonzotech/lang/en_us.json"))
for key in (
    "gui.gonzotech.steamgen.max_gth_intake",
    "gui.gonzotech.steamgen.nominal_gth_per_steam",
    "gui.gonzotech.steamgen.nominal_water_per_steam",
    "gui.gonzotech.steamgen.exchangers",
    "gui.gonzotech.turbine.nominal_steam_per_gtu",
    "gui.gonzotech.unit.water_genitive",
    "gui.gonzotech.unit.steam_genitive",
):
    pin(key in ru and key in en, f"missing ru/en translation: {key}")
pin("от платины" in ru.get("gui.gonzotech.steamgen.exchangers", "")
    and "platinum" in en.get("gui.gonzotech.steamgen.exchangers", ""),
    "exchanger tooltip should describe output relative to platinum")

if failures:
    print("FAIL — SteamGen balance/tooltip contract:")
    for failure in failures:
        print("  -", failure)
    sys.exit(1)
print("OK: SteamGen six-point platinum curve, 52 exchanger materials, headroom, and two-decimal nominal tooltips")
