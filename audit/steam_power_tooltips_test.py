#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Контракт тултипов парогенератора и турбины (0.3.141).

Проверяет подписи/порядок экранных строк, расчёт номинальных отношений и сумму
лимитов GTH-портов, не затрагивая фактические потоки и баланс машин.

Запуск: python3 audit/steam_power_tooltips_test.py
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


machine_defs = read("src/main/java/com/gonzotech/machines/energy/MachineDefs.java")
second_defs = read("src/main/java/com/gonzotech/machines/energy/SecondTierDefs.java")
pipe_type = read("src/main/java/com/gonzotech/machines/network/PipeType.java")
pipe_ledger = read("src/main/java/com/gonzotech/machines/network/PipeFlowLedger.java")
universal_node = read("src/main/java/com/gonzotech/machines/network/UniversalNodeBlock.java")
second_universal = read("src/main/java/com/gonzotech/machines/network/SecondUniversalNodeBlock.java")
steam_math = read("src/main/java/com/gonzotech/machines/steamgen/SteamGenMath.java")
steam_be = read("src/main/java/com/gonzotech/machines/block/entity/SteamGenCoreBlockEntity.java")
steam_structure = read("src/main/java/com/gonzotech/machines/steamgen/SteamGenStructure.java")
turbine_math = read("src/main/java/com/gonzotech/machines/turbine/TurbineMath.java")
steam_screen = read("src/main/java/com/gonzotech/machines/client/SteamGenScreen.java")
turbine_screen = read("src/main/java/com/gonzotech/machines/client/TurbineScreen.java")
steam_menu = read("src/main/java/com/gonzotech/machines/menu/SteamGenMenu.java")
turbine_menu = read("src/main/java/com/gonzotech/machines/menu/TurbineMenu.java")
units = read("src/main/java/com/gonzotech/core/text/GtUnits.java")

# The heat-port summary uses the same effective carrier cap as PipeFlowLedger,
# capped by the boiler's authored per-port limit; multiple ports add together.
pin("scaled(carrier.throughputLimit(state, type), carrier, state, type)" in pipe_ledger
    and "Math.floor(limit * f)" in pipe_ledger and "Math.max(1L" in pipe_ledger
    and ": limit;" in pipe_ledger,
    "PipeFlowLedger effective throughput semantics changed; re-check heat-port maxima")
milli = integer(machine_defs, "MILLI")
steamgen_port_cap = integer(machine_defs, "STEAMGEN_GTH_PER_PORT_MILLI", r"([0-9_]+)\s*\*\s*MILLI") * milli
first_heat = int(re.search(r'HEAT\("first_heat_pipe"[^\n]*?,\s*([0-9_]+)\s*\*\s*1000,', pipe_type).group(1).replace("_", ""))
second_heat = integer(second_defs, "HEAT_THROUGHPUT", r"([0-9_]+)L\s*\*\s*MachineDefs\.MILLI") * milli
first_universal_factor = float(re.search(r"THROUGHPUT_FACTOR\s*=\s*([0-9.]+);", universal_node).group(1))
second_universal_factor = float(re.search(r"THROUGHPUT_FACTOR\s*=\s*([0-9.]+)D;", second_universal).group(1))
pin(steamgen_port_cap == 312 * milli, "пароген: per-port cap больше не 312 GTH/t")


def port_limit_milli(base, factor):
    node_limit = max(1, math.floor(base * factor)) if factor < 1.0 else base
    return min(node_limit, steamgen_port_cap)


pin(port_limit_milli(first_heat * 1000, 1.0) / milli == 256,
    "T1 heat-node должен ограничивать порт значением 256 GTH/t")
pin(port_limit_milli(second_heat, 1.0) / milli == 312,
    "T2 heat-node должен упираться в 312 GTH/t парогена")
pin(port_limit_milli(first_heat * 1000, first_universal_factor) / milli == 230.4,
    "T1 universal-node должен учитывать throughputFactor 0.9")
pin(port_limit_milli(second_heat, second_universal_factor) / milli == 312,
    "T2 universal-node должен упираться в per-port cap 312 GTH/t")
pin((2 * port_limit_milli(second_heat, 1.0)) / milli == 624,
    "два T2 heat-порта должны давать 624 GTH/t суммарно")
pin("public static int maxGthIntakeMilli(Level level, long[] heatPorts)" in steam_structure,
    "SteamGenStructure не рассчитывает сумму приёмов heat-портов")
for required in ("carrier.throughputLimit(state, PipeType.HEAT)",
                 "carrier.throughputFactor(state, PipeType.HEAT)",
                 "Math.floor(base * factor)",
                 "MachineDefs.STEAMGEN_GTH_PER_PORT_MILLI"):
    pin(required in steam_structure, f"max GTH intake не повторяет лимит сети: {required}")
pin("case 9 -> maxGthIntakeMilli;" in steam_be
    and "this.maxGthIntakeMilli = SteamGenStructure.maxGthIntakeMilli(level, this.heatPorts);" in steam_be
    and 'tag.putInt("MaxGthIntakeMilli", maxGthIntakeMilli)' in steam_be
    and 'tag.getInt("MaxGthIntakeMilli")' in steam_be,
    "max GTH intake должен кешироваться, сохраняться и синхронизироваться для выгруженных портов")
pin("return 10;" in steam_be and "new SimpleContainerData(10)" in steam_menu,
    "SteamGen ContainerData count/размер клиента не совпадают")
pin("public int maxGthIntakeMilli()" in steam_menu and "data.get(9)" in steam_menu,
    "SteamGenMenu не читает max GTH intake")

# Nominal costs are resource use divided by the actual M-adjusted steam output
# per event: 193 GTH / (44 M mB), 114 mB water / (44 M mB).
water_per_event = integer(machine_defs, "STEAMGEN_WATER_PER_UNIT")
gth_per_event_milli = integer(machine_defs, "STEAMGEN_GTH_PER_UNIT_MILLI", r"([0-9_]+)\s*\*\s*MILLI") * milli
steam_per_event = integer(machine_defs, "STEAMGEN_STEAM_PER_UNIT")
steam_base_rate = integer(machine_defs, "STEAMGEN_STEAM_PER_TICK_PER_CORE")
exchanger_divisor = integer(machine_defs, "STEAMGEN_EXCHANGER_DIVISOR")
exchanger_step = integer(machine_defs, "STEAMGEN_EXCHANGER_STEP")
efficiency = float(re.search(r"STEAMGEN_EXCHANGER_EFFICIENCY\s*=\s*([0-9.]+)D", machine_defs).group(1))
pin(water_per_event == 114 and gth_per_event_milli == 193 * milli and steam_per_event == 44,
    "пароген: рецептный цикл не совпадает с 114 W + 193 GTH → 44 mB × M")
pin("nominalGthPerSteamMb" in steam_math and "nominalWaterPerSteamMb" in steam_math,
    "SteamGenMath не предоставляет обе номинальные удельные траты")
pin("STEAMGEN_GTH_PER_UNIT_MILLI / (double) MachineDefs.MILLI" in steam_math
    and "MachineDefs.STEAMGEN_WATER_PER_UNIT / steamPerEvent" in steam_math,
    "удельные траты не делятся на M-adjusted output одного цикла")


def exchanger_multiplier(sum_ch, count):
    if count <= 0 or sum_ch <= 0:
        return 1.0
    avg_efficiency = sum_ch / (exchanger_divisor * count) * efficiency
    growth = 1.0 + (count - 1) / exchanger_step
    return 1.0 + avg_efficiency * growth


for sum_ch, count in ((0, 0), (400, 4), (3_900, 26)):
    mult = exchanger_multiplier(sum_ch, count)
    gth_per_mB = (gth_per_event_milli / milli) / (steam_per_event * mult)
    water_per_mB = water_per_event / (steam_per_event * mult)
    pin(gth_per_mB > 0 and water_per_mB > 0,
        f"удельные расходы должны быть положительными: M={mult}")
    pin(math.isclose(gth_per_mB * steam_per_event * mult, gth_per_event_milli / milli,
                     rel_tol=1e-12), f"GTH ratio не пересчитывается по M={mult}")
    pin(math.isclose(water_per_mB * steam_per_event * mult, water_per_event,
                     rel_tol=1e-12), f"water ratio не пересчитывается по M={mult}")

# The tooltip promises the maximum integer mB made during one tick. burnSteam
# batches fractional cycles; this burst may exceed the long-run 34 × cores × M.
pin("return cores * MachineDefs.STEAMGEN_STEAM_PER_TICK_PER_CORE * multiplier(sumCH, count);" in steam_math,
    "SteamGenMath average output is not 34 × cores × M")
pin("maxSteamBurstPerTickMilli" in steam_math and "steamRemainderBefore" in steam_math
    and "remainderGcd" in steam_math and "cycleCapMilli" in steam_math,
    "SteamGenMath does not account for both cycle remainders in the max-tick output")
pin("SteamGenMath.maxSteamBurstPerTickMilli(menu.cores(), menu.sumCH(), menu.precious())" in steam_screen,
    "rated tooltip is not derived from the exact max single-tick output")
pin("GtUnits.steamGenRated(" in steam_screen and "steamGenSteamMade" not in steam_screen
    and "steamGenSteamOut" not in steam_screen,
    "steam tooltip must retain rated output only, not actual made/out lines")


def gcd(a, b):
    while b:
        a, b = b, a % b
    return a


def max_burst_by_remainder_cycle(cores, mult):
    cap = cores * steam_base_rate * milli // steam_per_event
    period = milli // gcd(cap % milli, milli)
    event_remainder = steam_remainder = 0
    production = []
    remainder_before = []
    total_production = 0
    for _ in range(period):
        event_budget = cap + event_remainder
        events, event_remainder = divmod(event_budget, milli)
        produced = math.floor(steam_per_event * mult * events * milli)
        production.append(produced)
        remainder_before.append(steam_remainder)
        steam_remainder = (produced + steam_remainder) % milli
        total_production += produced
    remainder_gcd = gcd(total_production % milli, milli)
    return max((produced + before + (milli - 1 - before) // remainder_gcd * remainder_gcd) // milli
               for produced, before in zip(production, remainder_before))


def brute_burst(cores, mult):
    cap = cores * steam_base_rate * milli // steam_per_event
    event_remainder = steam_remainder = 0
    seen = set()
    maximum = 0
    while (event_remainder, steam_remainder) not in seen:
        seen.add((event_remainder, steam_remainder))
        event_budget = cap + event_remainder
        events, event_remainder = divmod(event_budget, milli)
        produced = math.floor(steam_per_event * mult * events * milli)
        whole, steam_remainder = divmod(produced + steam_remainder, milli)
        maximum = max(maximum, whole)
    return maximum


for cores, sum_ch, count in ((1, 0, 0), (1, 400, 4), (4, 400, 4), (27, 3_900, 26)):
    mult = exchanger_multiplier(sum_ch, count)
    burst = max_burst_by_remainder_cycle(cores, mult)
    pin(burst == brute_burst(cores, mult),
        f"max per-tick output disagrees with burnSteam remainder simulation (cores={cores}, M={mult})")
    pin(burst >= cores * steam_base_rate * mult,
        f"max single-tick output is below average full-supply rate (cores={cores}, M={mult})")
pin(max_burst_by_remainder_cycle(1, 1.0) == 44,
    "one core with M=1 can produce one 44 mB event in a single tick (not the 34 mB average)")

# Turbine's exact long-run conversion is fixed at 56 mB per 1.5 GTU; rotor
# count scales throughput but not this per-GTU ratio. Do not use last-tick intake.
reference_mb = integer(machine_defs, "TURBINE_REFERENCE_STEAM_MB")
gtu_reference_milli = integer(machine_defs, "TURBINE_GTU_PER_REFERENCE_STEAM_MILLI")
nominal_mb_per_gtu = reference_mb * milli / gtu_reference_milli
pin("nominalSteamMbPerGtu" in turbine_math
    and "TURBINE_REFERENCE_STEAM_MB" in turbine_math
    and "TURBINE_GTU_PER_REFERENCE_STEAM_MILLI" in turbine_math,
    "TurbineMath не вычисляет точное удельное потребление по конверсии")
pin(math.isclose(nominal_mb_per_gtu, 56 / 1.5, rel_tol=1e-12),
    "ожидается 37.333… mB на 1 GTU по конверсии 56 mB → 1.5 GTU")
pin("TurbineMath.nominalSteamMbPerGtu()" in turbine_screen
    and "steamConsumed()" not in turbine_screen,
    "паровая шкала турбины должна показывать норму, не last-tick расход")
pin('String.format(Locale.ROOT, "%.0f"' in steam_screen
    and 'String.format(Locale.ROOT, "%.0f"' in turbine_screen,
    "экраны эпохи 2 должны показывать номиналы целыми по общему формату шкал")

# Screen contract, bilingual translations, and color/units for each resource.
pin("GtUnits.steamGenMaxGthIntake(maxIntake)" in steam_screen
    and "GtUnits.steamGenGthPerSteam(gthPerSteam)" in steam_screen,
    "GTH tooltip rows missing or reordered")
pin("GtUnits.steamGenWaterPerSteam(waterPerSteam)" in steam_screen,
    "water tooltip does not show nominal per-mB use")
pin(steam_screen.index("steamGenMaxGthIntake") < steam_screen.index("steamGenGthPerSteam"),
    "GTH rows: nominal per-steam line must follow max-intake line")
pin("GtUnits.turbineSteamPerGtu" in turbine_screen,
    "turbine steam tooltip missing nominal steam-per-GTU row")
for required in ("key(U_GTH, GTH)", "key(N_STEAM_GENITIVE, STEAM)", "num(value, GTH)",
                 "key(N_WATER_GENITIVE, WATER)", "num(value, WATER)", "mb(WATER)",
                 "gtu(), num(value, STEAM), mb(STEAM)", "withStyle(ChatFormatting.WHITE)"):
    pin(required in units, f"GtUnits missing label color/unit contract: {required}")

ru = json.loads(read("src/main/resources/assets/gonzotech/lang/ru_ru.json"))
en = json.loads(read("src/main/resources/assets/gonzotech/lang/en_us.json"))
for key in (
    "gui.gonzotech.steamgen.max_gth_intake",
    "gui.gonzotech.steamgen.nominal_gth_per_steam",
    "gui.gonzotech.steamgen.nominal_water_per_steam",
    "gui.gonzotech.turbine.nominal_steam_per_gtu",
    "gui.gonzotech.unit.water_genitive",
    "gui.gonzotech.unit.steam_genitive",
):
    pin(key in ru and key in en, f"нет ru/en перевода: {key}")
for stale in (
    "gui.gonzotech.steamgen.gth_in",
    "gui.gonzotech.steamgen.water_in",
    "gui.gonzotech.steamgen.steam_made",
    "gui.gonzotech.steamgen.steam_out",
    "gui.gonzotech.turbine.steam_rate",
):
    pin(stale not in ru and stale not in en, f"устаревший misleading lang key остался: {stale}")

if failures:
    print("FAIL — tooltip calculation/contract:")
    for failure in failures:
        print("  -", failure)
    sys.exit(1)
print("OK: пароген/турбина — max-intake и удельные расходы совпадают с формулами, тултипы без last-tick шумов")
