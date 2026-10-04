#!/usr/bin/env python3
"""Pin third_filler's exact milli sync, tenth-unit labels, and user-selected capacity."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/gonzotech"
be = (SRC / "machines/block/entity/FillerBlockEntity.java").read_text(encoding="utf-8")
menu = (SRC / "machines/menu/FillerMenu.java").read_text(encoding="utf-8")
screen = (SRC / "machines/client/FillerScreen.java").read_text(encoding="utf-8")

assert "public static final int GTH_CAPACITY = 2_508;" in be
assert "public static final int GTU_CAPACITY = 8_808;" in be
assert "public static final int TANK_CAPACITY = 9_000;" in be
assert "case 0 -> energyMilliForMenu(currentGthMilli, GTH_CAPACITY);" in be
assert "case 1 -> energyMilliForMenu(currentGtuMilli, GTU_CAPACITY);" in be
assert "(int) clamped" in be and "capacity * MachineDefs.MILLI" in be
assert "public double gth()" in menu and "data.get(0) / (double) MachineDefs.MILLI" in menu
assert "public double gtu()" in menu and "data.get(1) / (double) MachineDefs.MILLI" in menu
assert "menu.gth() / menu.maxGth()" in screen
assert "menu.gtu() / menu.maxGtu()" in screen
assert "GtUnits.x1(menu.gth())" in screen and "GtUnits.x1(menu.maxGth())" in screen
assert "GtUnits.x1(menu.gtu())" in screen and "GtUnits.x1(menu.maxGtu())" in screen
assert 2_508 * 1_000 < 2**31 and 8_808 * 1_000 < 2**31

print("OK: filler sends exact milli-energy to its menu; bars use actual values, labels use X.Y, GTH capacity is 2,508")
