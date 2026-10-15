#!/usr/bin/env python3
"""Static contracts for the client machine-GUI selector and solid-red NEO sheets."""
import json
import struct
import zlib
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
ASSETS = ROOT / "src/main/resources/assets/gonzotech"
GUI_DIR = ASSETS / "textures/gui"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}"
    return struct.unpack(">II", data[16:24])


def assert_solid_red_rgba(path: Path) -> None:
    data = path.read_bytes()
    assert dimensions(path) == (512, 512), f"wrong NEO dimensions: {path}"
    assert data[24] == 8 and data[25] == 6, f"NEO texture is not 8-bit RGBA: {path}"
    chunks = []
    cursor = 8
    while cursor < len(data):
        size = struct.unpack(">I", data[cursor:cursor + 4])[0]
        kind = data[cursor + 4:cursor + 8]
        payload = data[cursor + 8:cursor + 8 + size]
        if kind == b"IDAT":
            chunks.append(payload)
        cursor += 12 + size
        if kind == b"IEND":
            break
    raw = zlib.decompress(b"".join(chunks))
    expected_row = bytes((255, 0, 0, 255)) * 512
    stride = 1 + len(expected_row)
    assert len(raw) == stride * 512, f"wrong decompressed pixel count: {path}"
    for y in range(512):
        row = raw[y * stride:(y + 1) * stride]
        assert row[0] == 0 and row[1:] == expected_row, f"non-red or transparent pixel in {path}, row {y}"


_ver.at_least("0.3.228", "client machine GUI theme selector and scoped radiation trace")

# Only the 512×512 machine GUI sandwich sheets get NEO copies; the two smaller
# item-filter canvases are not part of this switch.
source_sheets = {
    path for path in GUI_DIR.glob("*.png")
    if path.name.endswith(("_gui.png", "_gui_bg.png")) and dimensions(path) == (512, 512)
}
assert len(source_sheets) == 62, f"expected 62 source sheets, found {len(source_sheets)}"
expected_neo = {path.stem + "_neo.png" for path in source_sheets}
actual_neo = {
    path.name for path in GUI_DIR.glob("*_neo.png")
    if path.name.endswith(("_gui_neo.png", "_gui_bg_neo.png"))
}
assert actual_neo == expected_neo, f"NEO sheet set mismatch: missing={expected_neo - actual_neo}, extra={actual_neo - expected_neo}"
for source in source_sheets:
    neo = source.with_name(source.stem + "_neo.png")
    assert neo.is_file(), f"missing NEO copy for {source.name}"
    assert_solid_red_rgba(neo)

for name in ("item_filter_gui.png", "second_item_filter_gui.png"):
    source = GUI_DIR / name
    assert dimensions(source) == (176, 184), f"expected filter screen texture dimensions: {source}"
    assert not source.with_name(source.stem + "_neo.png").exists(), f"filter UI must remain unchanged: {name}"

config = read(JAVA / "core/config/GonzoClientConfig.java")
screen = read(JAVA / "machines/client/MachineScreen.java")
silicon = read(JAVA / "machines/client/SiliconFactoryScreen.java")
assert 'define("useClassicMachineGuis", false)' in config, "NEO should be the default"
assert 'translation("gonzotech.configuration.useClassicMachineGuis")' in config
assert "isMachineGuiSheet(file) && !usesClassicMachineGuis()" in screen
assert 'file.substring(0, file.length() - ".png".length()) + "_neo.png"' in screen
assert "GonzoClientConfig.USE_CLASSIC_MACHINE_GUIS.get()" in screen
assert "resolvedClassic == classic" in silicon, "cached dynamic GUI sheets must notice config changes"

for locale in ("en_us", "ru_ru"):
    translations = json.loads((ASSETS / f"lang/{locale}.json").read_text(encoding="utf-8"))
    assert "gonzotech.configuration.useClassicMachineGuis" in translations
    assert "gonzotech.configuration.useClassicMachineGuis.tooltip" in translations
assert json.loads((ASSETS / "lang/ru_ru.json").read_text(encoding="utf-8"))[
    "gonzotech.configuration.useClassicMachineGuis"
] == "Использовать классические ГУИ станков"

# JEI deliberately builds ResourceLocations from the original names, not MachineScreen.gui().
jei_category = read(JAVA / "integration/jei/MachineJeiRecipeCategory.java")
jei_recipes = read(JAVA / "integration/jei/MachineJeiRecipes.java")
assert 'definition.texture() + "_gui_bg.png"' in jei_category
assert 'definition.texture() + "_gui.png"' in jei_category
assert '"textures/gui/" + name + ".png"' in jei_recipes
assert "_neo" not in jei_category and "_neo" not in jei_recipes
filter_screen = read(JAVA / "machines/client/ItemFilterScreen.java")
assert "extends AbstractContainerScreen<ItemFilterMenu>" in filter_screen
assert "MachineScreen.gui" not in filter_screen and "_neo" not in filter_screen

print("machine GUI theme selector + NEO assets: PASS (62 solid-red 512×512 sheets; JEI unchanged)")
