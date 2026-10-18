#!/usr/bin/env python3
"""Static contracts for machine GUI paths, theme selection, and per-machine NEO art."""
import json
import re
import struct
from collections import defaultdict
from pathlib import Path

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
ASSETS = ROOT / "src/main/resources/assets/gonzotech"
GUI_DIR = ASSETS / "textures/gui"
CLASSIC_DIR = GUI_DIR / "classic"
NEO_DIR = GUI_DIR / "neo"


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def dimensions(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", f"not a PNG: {path}"
    return struct.unpack(">II", data[16:24])


_ver.at_least("0.3.241", "relocated machine GUI paths and one dedicated NEO sheet per machine")

# Classic resources may be shared; this directory retains the existing 32 large
# classic pairs, two 176x184 filters, and the reactor's 18x18 overlay.
classic_sheets = {
    path for path in CLASSIC_DIR.glob("*.png")
    if path.name.endswith(("_gui.png", "_gui_bg.png")) and dimensions(path) == (512, 512)
}
assert len(classic_sheets) == 64, f"expected 64 512px classic sheets, found {len(classic_sheets)}"
filter_classic = {
    "item_filter_gui.png": "item_filter_gui_neo.png",
    "second_item_filter_gui.png": "second_item_filter_gui_neo.png",
}
for classic_name in filter_classic:
    assert dimensions(CLASSIC_DIR / classic_name) == (176, 184), f"wrong filter canvas: {classic_name}"
assert dimensions(CLASSIC_DIR / "third_uranium_reactor_gui_slot.png") == (18, 18)

# Every literal machineGui(classic, neo) reference resolves, dimensions match,
# and a NEO file cannot be shared by different machine screens.
pair_pattern = re.compile(
    r'(?:MachineScreen\.)?machineGui\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*\)'
)
neo_owners: dict[str, str] = {}
referenced_neo: set[str] = set()
per_screen: dict[str, set[str]] = defaultdict(set)
for source in sorted((JAVA / "machines/client").glob("*.java")):
    text = read(source)
    for classic_name, neo_name in pair_pattern.findall(text):
        # Variant-specific silicon sheets are constructed at runtime and fall back
        # to the explicitly referenced silicon_factory pair when absent.
        if "+" in classic_name or "+" in neo_name:
            continue
        classic_path = CLASSIC_DIR / classic_name
        neo_path = NEO_DIR / neo_name
        assert classic_path.is_file(), f"missing classic GUI path in {source.name}: {classic_path}"
        assert neo_path.is_file(), f"missing NEO GUI path in {source.name}: {neo_path}"
        assert dimensions(classic_path) == dimensions(neo_path), f"theme canvas mismatch: {source.name}/{neo_name}"
        previous_owner = neo_owners.setdefault(neo_name, source.name)
        assert previous_owner == source.name, f"NEO sheet {neo_name} is shared by {previous_owner} and {source.name}"
        referenced_neo.add(neo_name)
        per_screen[source.name].add(neo_name)

# Every regular machine screen explicitly provides its own foreground/background NEO files.
for source in sorted((JAVA / "machines/client").glob("*Screen.java")):
    text = read(source)
    if "extends MachineScreen<" in text:
        assert len(per_screen[source.name]) >= 2, f"machine screen lacks its own NEO sheet pair: {source.name}"

# Filters are a screen outside MachineScreen, but still follow the same explicit pair contract.
filter_screen = read(JAVA / "machines/client/ItemFilterScreen.java")
assert 'MachineScreen.machineGui(classicFile, neoFile)' in filter_screen
filter_constants = dict(re.findall(r'private static final String (\w+) = "([^"]+)";', filter_screen))
filter_pairs = (
    (filter_constants["FIRST_TIER_BG"], filter_constants["FIRST_TIER_NEO_BG"]),
    (filter_constants["SECOND_TIER_BG"], filter_constants["SECOND_TIER_NEO_BG"]),
)
for classic_name, neo_name in filter_pairs:
    assert (CLASSIC_DIR / classic_name).is_file()
    assert (NEO_DIR / neo_name).is_file()
    assert dimensions(CLASSIC_DIR / classic_name) == dimensions(NEO_DIR / neo_name) == (176, 184)
    previous_owner = neo_owners.setdefault(neo_name, "ItemFilterScreen")
    assert previous_owner == "ItemFilterScreen", f"filter NEO asset is shared: {neo_name}"
    referenced_neo.add(neo_name)

# The two accumulator blocks intentionally reuse the same classic pair but have
# distinct NEO files. The tap may reuse the classic pump art, never its NEO art.
first_accumulator = read(JAVA / "machines/client/AccumulatorScreen.java")
second_accumulator = read(JAVA / "machines/client/SecondAccumulatorScreen.java")
pump = read(JAVA / "machines/client/PumpScreen.java")
tap = read(JAVA / "machines/client/DispensingTapScreen.java")
assert 'machineGui("accumulator_gui.png", "first_accumulator_gui_neo.png")' in first_accumulator
assert 'machineGui("accumulator_gui_bg.png", "first_accumulator_gui_bg_neo.png")' in first_accumulator
assert 'machineGui("accumulator_gui.png", "second_accumulator_gui_neo.png")' in second_accumulator
assert 'machineGui("accumulator_gui_bg.png", "second_accumulator_gui_bg_neo.png")' in second_accumulator
assert 'machineGui("pump_gui.png", "pump_gui_neo.png")' in pump
assert 'machineGui("pump_gui.png", "third_dispensing_tap_gui_neo.png")' in tap
assert 'machineGui("pump_gui_bg.png", "third_dispensing_tap_gui_bg_neo.png")' in tap

# Ensure all NEO GUI resources are addressed by a screen and all machine GUI
# sheets have left the old flat directory. Utility bars/overlays remain there.
referenced_neo.update(filter_classic.values())
actual_neo = {path.name for path in NEO_DIR.glob("*_gui*.png")}
assert actual_neo == referenced_neo, (
    f"NEO sheet set differs from screen wiring: missing={referenced_neo - actual_neo}, "
    f"unreferenced={actual_neo - referenced_neo}"
)
legacy_machine_sheets = [p.name for p in GUI_DIR.glob("*_gui*.png")]
assert not legacy_machine_sheets, f"machine GUI sheets remain outside theme directories: {legacy_machine_sheets}"

# Keep every supplied NEO sheet as a real image; this audit validates paths and
# canvas contracts without rewriting or imposing a color/style on authored art.
for name in actual_neo:
    path = NEO_DIR / name
    assert path.stat().st_size > 32, f"empty NEO GUI asset: {name}"
    assert dimensions(path)[0] > 0 and dimensions(path)[1] > 0

config = read(JAVA / "core/config/GonzoClientConfig.java")
screen = read(JAVA / "machines/client/MachineScreen.java")
silicon = read(JAVA / "machines/client/SiliconFactoryScreen.java")
assert 'define("useClassicMachineGuis", false)' in config, "NEO should be the default"
assert 'translation("gonzotech.configuration.useClassicMachineGuis")' in config
assert 'GUI_DIR + "classic/"' in screen and 'GUI_DIR + "neo/"' in screen
assert 'return ResourceLocation.fromNamespaceAndPath("gonzotech", GUI_DIR + file);' in screen
assert 'String file = classic ? classicFile : neoFile;' in screen
assert '"silicon_factory_chip_" + variant + "_gui_bg.png"' in silicon
assert '"silicon_factory_chip_" + variant + "_gui_bg_neo.png"' in silicon
assert "resolvedClassic == classic" in silicon, "cached dynamic GUI sheets must notice config changes"

for locale in ("en_us", "ru_ru"):
    translations = json.loads((ASSETS / f"lang/{locale}.json").read_text(encoding="utf-8"))
    assert "gonzotech.configuration.useClassicMachineGuis" in translations
    assert "gonzotech.configuration.useClassicMachineGuis.tooltip" in translations
assert json.loads((ASSETS / "lang/ru_ru.json").read_text(encoding="utf-8"))[
    "gonzotech.configuration.useClassicMachineGuis"
] == "Использовать классические ГУИ станков"

# JEI deliberately uses classic sheets, independent of the in-game GUI theme;
# animated bar textures remain at textures/gui/.
jei_category = read(JAVA / "integration/jei/MachineJeiRecipeCategory.java")
jei_recipes = read(JAVA / "integration/jei/MachineJeiRecipes.java")
assert '"textures/gui/classic/" + definition.texture() + "_gui_bg.png"' in jei_category
assert '"textures/gui/classic/" + definition.texture() + "_gui.png"' in jei_category
assert '"textures/gui/" + name + ".png"' in jei_recipes
assert "_neo" not in jei_category and "_neo" not in jei_recipes

print(f"machine GUI paths/theme selector + per-machine NEO: PASS ({len(classic_sheets)} classic sheets, {len(actual_neo)} NEO resources)")
