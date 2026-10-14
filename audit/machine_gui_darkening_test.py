#!/usr/bin/env python3
"""Keep Gonzo machine screens at one vanilla-opacity world-darkening layer."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/com/gonzotech/machines/client/MachineScreen.java").read_text(encoding="utf-8")
assert "WORLD_DARKENING_ARGB = 0xA0000000" in source
background = re.search(r"public void renderBackground\(GuiGraphics g, int mouseX, int mouseY, float partialTick\) \{(.*?)\n    \}", source, re.S)
assert background, "machine screens need an explicit fixed-alpha world overlay"
assert "g.fill(0, 0, this.width, this.height, WORLD_DARKENING_ARGB);" in background.group(1)
render = re.search(r"public void render\(GuiGraphics g, int mouseX, int mouseY, float partial\) \{(.*?)\n    \}", source, re.S)
assert render, "MachineScreen.render must remain the shared machine-screen entry point"
body = render.group(1)
assert "super.render(g, mouseX, mouseY, partial);" in body
assert "this.renderBackground(" not in body, "do not composite a second black overlay"
assert "this.renderTooltip(g, mouseX, mouseY);" in body
print("Machine GUI darkening check passed: one 0xA0 black overlay (nearest 8-bit form of 62.8%)")
