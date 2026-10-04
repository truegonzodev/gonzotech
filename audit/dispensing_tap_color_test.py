#!/usr/bin/env python3
"""Ensure the dispensing tap uses the same liquid text colors as the source machines."""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
client = (ROOT / "src/main/java/com/gonzotech/machines/client")
tap = (client / "DispensingTapScreen.java").read_text(encoding="utf-8")
wort_kettle = (client / "WortKettleScreen.java").read_text(encoding="utf-8")
distiller = (client / "DistillerScreen.java").read_text(encoding="utf-8")
units = (ROOT / "src/main/java/com/gonzotech/core/text/GtUnits.java").read_text(encoding="utf-8")

assert "GtUnits.DISTILLATE)" in tap
assert "GtUnits.WORT)" in tap
assert "ChatFormatting.AQUA" not in tap
assert "ChatFormatting.GOLD" not in tap
assert "int color)" in tap and "Style.EMPTY.withColor(color)" in tap
assert "GtUnits.distillateTooltip" in distiller
assert "GtUnits.wortTooltip" in wort_kettle
assert "public static final int DISTILLATE = 0x8BD3FC;" in units
assert "public static final int WORT = 0xFFD582;" in units

print("OK: dispensing-tap wort/distillate use the GtUnits colors shared with their source-machine tooltips")
