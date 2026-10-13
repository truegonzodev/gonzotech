#!/usr/bin/env python3
"""Static contract checks for JEI artwork, registrations, and fluid labels."""
import json
from pathlib import Path
import re
import struct

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"

expected = {
    "first_electric_furnace", "second_electric_furnace", "second_crusher",
    "second_centrifuge", "third_pcfsoz", "second_grinder", "second_press",
    "second_alloy_foundry", "third_chemical_plant", "third_filler",
    "third_silicon_factory", "third_fermentation_vat", "third_wort_kettle",
    "third_distiller", "third_rectifier",
}

categories = (JAVA / "integration/jei/MachineJeiCategories.java").read_text(encoding="utf-8")
ids = set(re.findall(r'define\(\s*"([a-z0-9_]+)"', categories))
assert ids == expected, f"JEI machine categories differ: missing={expected - ids}, extra={ids - expected}"
textures = re.findall(r'define\(\s*"[a-z0-9_]+"\s*,\s*"([a-z0-9_]+)"', categories)
assert len(textures) == len(expected) and len(set(textures)) == len(expected)

category = (JAVA / "integration/jei/MachineJeiRecipeCategory.java").read_text(encoding="utf-8")
assert 'definition.texture() + "_gui.png"' in category
assert "_gui_bg.png" not in category
assert "drawableBuilder(texture, 128, 128, definition.width(), definition.height())" in category
for texture in textures:
    path = RES / f"assets/gonzotech/textures/gui/{texture}_gui.png"
    assert path.is_file(), f"missing machine GUI foreground: {path}"
    with path.open("rb") as image:
        image.seek(16)
        width, height = struct.unpack(">II", image.read(8))
    assert (width, height) == (512, 512), f"unexpected GUI sheet dimensions: {path}"

fluid_source = (JAVA / "core/fluid/ModFluids.java").read_text(encoding="utf-8")
fluid_ids = set(re.findall(r'FLUID_TYPES\.register\("([a-z0-9_]+)"', fluid_source))
assert len(fluid_ids) == 7, f"unexpected registered FluidType set: {sorted(fluid_ids)}"
for locale in ("en_us", "ru_ru"):
    data = json.loads((RES / f"assets/gonzotech/lang/{locale}.json").read_text(encoding="utf-8"))
    for fluid_id in fluid_ids:
        key = f"fluid_type.gonzotech.{fluid_id}"
        assert key in data and data[key], f"missing {locale} fluid translation: {key}"

plugin = (JAVA / "integration/jei/GonzoTechJeiPlugin.java").read_text(encoding="utf-8")
recipes = (JAVA / "integration/jei/MachineJeiRecipes.java").read_text(encoding="utf-8")
assert ".includeHidden()" in plugin
assert "hideRecipes" not in plugin + recipes
assert "RecipeUnlocks" not in recipes, "machine JEI recipes must not be progression-filtered"
assert "GOST-JEI-01" in (ROOT / "docs/JEI-MACHINES.md").read_text(encoding="utf-8")
print("JEI display checks passed: 15 categories, foreground GUI sheets, 7 fluid labels, visible recipes")
