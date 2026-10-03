# -*- coding: utf-8 -*-
"""Форматы тултипов шкал (раунд 14, автор 03.10.2026).

Эпохи 1–2: ТОЛЬКО целые («X GTU», «X mB/t» — без дроби).
Эпоха 3: ровно один десятичный знак («X.Y») — никакой связи с
тысячными/сотыми и целыми без дроби. Формат един для тултипов.

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
}
for name, wraps in THIRD.items():
    src = rd(f"src/main/java/com/gonzotech/machines/client/{name}.java")
    got = src.count("GtUnits.x1(")
    pin(got == wraps * 2, f"{name}: GtUnits.x1 {got} раз, ожидается {wraps * 2}")

# Тысячные/сотые больше не просачиваются (SiliconFactory был источником .345)
sf = rd("src/main/java/com/gonzotech/machines/client/SiliconFactoryScreen.java")
pin("BigDecimal.valueOf" not in sf, "SiliconFactory: BigDecimal-тысячные вернулись")

# Эпохи 1–2: номиналы целыми
for name in ("TurbineScreen", "SteamGenScreen"):
    src = rd(f"src/main/java/com/gonzotech/machines/client/{name}.java")
    pin('%.0f", ratedMilli / 1000.0D)' in src, f"{name}: номинал не целым")

# В клиенте машин не осталось %.2f/%.3f (потери трубы в WrenchHud — исключение)
for f in Path(ROOT / "src/main/java/com/gonzotech/machines/client").glob("*.java"):
    hits = re.findall(r"%\.[234]f", f.read_text(encoding="utf-8"))
    allowed = f.name == "WrenchHud.java"  # потери 0.08/0.09 GTU — не шкала
    if not allowed:
        pin(not hits, f"{f.name}: дробные форматы {hits}")

if failures:
    print("FAIL:")
    for m in failures:
        print("  -", m)
    sys.exit(1)
print("OK: эпохи 1–2 целые, эпоха 3 X.Y, тысячных/сотых нет")
