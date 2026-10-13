#!/usr/bin/env python3
"""Static contract checks for JEI recipes, gauge animation, tooltips, and translations."""
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
assert 'definition.texture() + "_gui_bg.png"' in category
assert "machineGuiForeground.draw(graphics, 0, 0);" in category
assert "addInvisibleIngredients(slot.role())" in category
assert "invisibleIngredients.add(fluid.fluid(), fluid.amount())" in category
assert "setOutputSlotBackground" not in category
assert "slotBuilder.setStandardSlotBackground();" in category
assert "backgroundTexture, 128, 128, definition.width(), definition.height()" in category
assert "foregroundTexture, 128, 128, definition.width(), definition.height()" in category
assert "GAUGE_CYCLE_MILLIS = 5_000L" in category
assert "drawGauge(graphics, gauge, gauge.fillFraction(operationProgress))" in category
assert "public void getTooltip(ITooltipBuilder tooltip" in category
assert "slotBuilder.setStandardSlotBackground();" in category
assert "setDisplayedIngredient" not in category

gauge_model = (JAVA / "integration/jei/MachineJeiRecipe.java").read_text(encoding="utf-8")
assert "public record Gauge(" in gauge_model
assert "cycleStart" in gauge_model and "cycleEnd" in gauge_model
assert "motion == GaugeMotion.STATIC) return 1.0F" in gauge_model
assert "motion == GaugeMotion.CONSUME ? 1.0F - progress : progress" in gauge_model

recipes = (JAVA / "integration/jei/MachineJeiRecipes.java").read_text(encoding="utf-8")
for source in (
    "MachineDefs.ELECTRIC_GTU_PER_ITEM", "SecondTierDefs.ELECTRIC_GTU_PER_ITEM",
    "SecondTierDefs.GRINDER_GTU_MILLI_PER_TICK", "MachineDefs.CENTRIFUGE_GTU_MILLI_PER_WASH",
    "MachineDefs.PCFSOZ_GTU_MILLI_PER_TICK", "recipe.gtuPerTickMilli()",
    "SecondTierDefs.PRESS_GTU_PER_STAMP", "SecondTierDefs.ALLOY_FOUNDRY_GTU_MILLI_PER_TICK",
    "FillerBlockEntity.YELLOW_CAKE_ACID_TOTAL", "WortKettleBlockEntity.BOIL_GTH_COST",
    "DistillerBlockEntity.PROCESS_GTH_COST", "RectifierBlockEntity.PROCESS_GTU_COST",
    "MachineDefs.CRUSHER_PROGRESS_TOTAL", "MachineDefs.crusherProgressUnitsPerTick(",
    "MachineDefs.CRUSHER_GTU_MILLI_PER_TICK_MIN", "MachineDefs.CRUSHER_GTU_MILLI_PER_TICK_MAX",
    "fluidGauge(55, 35", "fluidGauge(105, 35", "fluidGauge(152, 35, \"bar_mash\"",
    "fluidGauge(80, 35", "fluidGauge(116, 35", "fluidGauge(116, 17", "fluidGauge(44, 17",
    "fluidGauge(152, 17", "fluidGauge(62, 35", "fluidGauge(98, 35",
    "SiliconFactoryBlockEntity.STEP_TICKS",
):
    assert source in recipes, f"JEI gauge data is not derived from machine logic: {source}"

draw_start = category.index("public void draw(")
draw_end = category.index("@Override\n    public void getTooltip", draw_start)
draw_body = category[draw_start:draw_end]
assert "recipe.slots()" not in draw_body and "setDisplayedIngredient" not in draw_body, \
    "JEI animation must affect gauges only; input/output slots stay static"
assert draw_body.index("drawGauge(graphics, gauge") < draw_body.index("machineGuiForeground.draw(graphics, 0, 0)"), \
    "machine GUI foreground must mask the animated bars"
for texture in textures:
    path = RES / f"assets/gonzotech/textures/gui/{texture}_gui.png"
    assert path.is_file(), f"missing machine GUI foreground: {path}"
    with path.open("rb") as image:
        image.seek(16)
        width, height = struct.unpack(">II", image.read(8))
    assert (width, height) == (512, 512), f"unexpected GUI sheet dimensions: {path}"
    background = RES / f"assets/gonzotech/textures/gui/{texture}_gui_bg.png"
    assert background.is_file(), f"missing machine GUI background layer: {background}"
    with background.open("rb") as image:
        image.seek(16)
        bg_width, bg_height = struct.unpack(">II", image.read(8))
    assert (bg_width, bg_height) == (512, 512), f"unexpected GUI background dimensions: {background}"

fluid_source = (JAVA / "core/fluid/ModFluids.java").read_text(encoding="utf-8")
fluid_ids = set(re.findall(r'FLUID_TYPES\.register\("([a-z0-9_]+)"', fluid_source))
assert len(fluid_ids) == 7, f"unexpected registered FluidType set: {sorted(fluid_ids)}"
gauge_translation_keys = {
    "jei.gonzotech.gauge.operation_cost", "jei.gonzotech.gauge.active_tick_cost",
    "jei.gonzotech.gauge.active_tick_cost_limit", "jei.gonzotech.gauge.normalized_note",
    "jei.gonzotech.gauge.operation_fluid",
    "jei.gonzotech.gauge.operation_fluid_output", "jei.gonzotech.gauge.active_tick_fluid_input",
    "jei.gonzotech.gauge.active_tick_fluid_output", "jei.gonzotech.gauge.active_tick_fluid_input_limit",
    "jei.gonzotech.gauge.active_tick_fluid_output_limit", "jei.gonzotech.gauge.duration",
    "jei.gonzotech.gauge.duration_range", "jei.gonzotech.gauge.operation_cost_range",
    "jei.gonzotech.gauge.crusher_duration", "jei.gonzotech.gauge.crusher_energy_range",
    "jei.gonzotech.gauge.silicon_energy_note", "jei.gonzotech.gauge.retained_water",
    "jei.gonzotech.gauge.fluid_per_input_item", "jei.gonzotech.gauge.distiller_poison_output", "jei.gonzotech.gauge.wort_boiling_flow",
    "jei.gonzotech.gauge.wort_boiling_energy", "jei.gonzotech.gauge.wort_evaporation_flow",
    "jei.gonzotech.gauge.distiller_flow",
    "jei.gonzotech.gauge.rectifier_flow", "jei.gonzotech.gauge.filler_calcite_salt",
    "jei.gonzotech.gauge.filler_salt_evaporation",
}
for locale in ("en_us", "ru_ru"):
    data = json.loads((RES / f"assets/gonzotech/lang/{locale}.json").read_text(encoding="utf-8"))
    for fluid_id in fluid_ids:
        key = f"fluid_type.gonzotech.{fluid_id}"
        assert key in data and data[key], f"missing {locale} fluid translation: {key}"
    missing_gauge_keys = {key for key in gauge_translation_keys if not data.get(key)}
    for process in ("smelting", "washing", "separation", "grinding", "press_recovery", "reaction",
                    "alloying", "etching", "photolithography", "vulcanization"):
        key = f"jei.gonzotech.gauge.process.{process}"
        if not data.get(key):
            missing_gauge_keys.add(key)
    assert not missing_gauge_keys, f"missing {locale} gauge translations: {sorted(missing_gauge_keys)}"

plugin = (JAVA / "integration/jei/GonzoTechJeiPlugin.java").read_text(encoding="utf-8")
recipes = (JAVA / "integration/jei/MachineJeiRecipes.java").read_text(encoding="utf-8")
assert ".includeHidden()" in plugin
assert "hideRecipes" not in plugin + recipes
assert "RecipeUnlocks" not in recipes, "machine JEI recipes must not be progression-filtered"
assert "GOST-JEI-01" in (ROOT / "docs/JEI-MACHINES.md").read_text(encoding="utf-8")
print("JEI display checks passed: 15 categories, layered GUI, static item slots, fluid bars, invisible JEI fluid lookup")
