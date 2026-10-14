#!/usr/bin/env python3
"""Use vanilla world darkening and draw only the machine GUI sandwich above it."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/com/gonzotech/machines/client/MachineScreen.java").read_text(encoding="utf-8")
assert "WORLD_DARKENING_ARGB" not in source, "do not add a second artificial darkening layer"
assert "public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick)" not in source, \
    "let AbstractContainerScreen use its vanilla background implementation"
render_start = source.index("public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {")
render_end = source.index("\n    }", render_start)
render_body = source[render_start:render_end]
assert "super.render(g, mouseX, mouseY, partial);" in render_body
assert "this.renderBackground(" not in render_body, "do not draw another background pass"
assert "this.renderTooltip(g, mouseX, mouseY);" in render_body
render_bg_start = source.index("protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {")
render_bg_end = source.index("\n    }", render_bg_start)
render_bg_body = source[render_bg_start:render_bg_end]
assert render_bg_body.index("backgroundTexture()") < render_bg_body.index("drawMachine(") \
    < render_bg_body.index("foregroundTexture()"), "GUI sandwich order must stay background → bars → foreground"
print("Machine GUI background check passed: vanilla darkening + background/bar/foreground sandwich")
