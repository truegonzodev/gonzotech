# -*- coding: utf-8 -*-
"""Форматы тултипов шкал (раунд 14, автор 03.10.2026).

Эпохи 1–2: throughput-шкалы целые («X GTU», «X mB/t» — без дроби).
Исключение 0.3.142: номинальные расходы SteamGen и турбины показываются
до сотых; это не throughput-шкалы. Эпоха 3: ровно один знак («X.Y»).

Запуск: python3 audit/scale_format_test.py
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
failures = []


def pin(cond, msg):
    if not cond:
        failures.append(msg)


def rd(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


gtu = rd("src/main/java/com/gonzotech/core/text/GtUnits.java")
pin("public static String x1(Number value)" in gtu, "GtUnits.x1 отсутствует")
pin('%.1f", value.doubleValue())' in gtu, "GtUnits.x1 не формат X.Y")

# Эпоха 3: все значения шкал завёрнуты в GtUnits.x1(...)
THIRD = {
    "FermentationVatScreen": 2, "WortKettleScreen": 3, "DistillerScreen": 4,
    "RectifierScreen": 4, "SnaketypeCondenserScreen": 2, "ChemicalPlantScreen": 1,
    "SiliconFactoryScreen": 1, "PcfsozScreen": 2, "DispensingTapScreen": 2,
    "FillerScreen": 2,
}
for name, wraps in THIRD.items():
    src = rd(f"src/main/java/com/gonzotech/machines/client/{name}.java")
    got = src.count("GtUnits.x1(")
    pin(got == wraps * 2, f"{name}: GtUnits.x1 {got} раз, ожидается {wraps * 2}")

# Тысячные/сотые больше не просачиваются (SiliconFactory был источником .345)
sf = rd("src/main/java/com/gonzotech/machines/client/SiliconFactoryScreen.java")
pin("BigDecimal.valueOf" not in sf, "SiliconFactory: BigDecimal-тысячные вернулись")

# Throughput-шкалы эпох 1–2 остаются целыми; номинальные расходы — X.XX.
for name in ("TurbineScreen", "SteamGenScreen"):
    src = rd(f"src/main/java/com/gonzotech/machines/client/{name}.java")
    pin('%.0f", ratedMilli / 1000.0D)' in src, f"{name}: rated throughput больше не целый")
expected_nominal_two_decimals = {"SteamGenScreen.java": 2, "TurbineScreen.java": 1}
for name, expected in expected_nominal_two_decimals.items():
    src = rd(f"src/main/java/com/gonzotech/machines/client/{name}")
    pin(src.count('String.format(Locale.ROOT, "%.2f"') == expected,
        f"{name}: номинальные расходы должны показываться до сотых")

# Дробные форматы допустимы только в номинальных расходах и потерях трубы HUD.
for f in Path(ROOT / "src/main/java/com/gonzotech/machines/client").glob("*.java"):
    hits = re.findall(r"%\.[234]f", f.read_text(encoding="utf-8"))
    if f.name in expected_nominal_two_decimals:
        pin(len(hits) == expected_nominal_two_decimals[f.name]
            and all(hit == "%.2f" for hit in hits),
            f"{f.name}: unexpected fractional scale format {hits}")
    elif f.name != "WrenchHud.java":  # потери трубы — не шкала
        pin(not hits, f"{f.name}: дробные форматы {hits}")

if failures:
    print("FAIL:")
    for m in failures:
        print("  -", m)
    sys.exit(1)
print("OK: throughput эпох 1–2 целый, эпоха 3 X.Y, номинальные расходы X.XX")
