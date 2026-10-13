#!/usr/bin/env python3
"""Static contract checks for JEI categories, GUI geometry, recipes, and GOST tooltips."""
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
    "third_distiller", "third_rectifier", "third_dispensing_tap", "crimson_obsidian",
}

categories = (JAVA / "integration/jei/MachineJeiCategories.java").read_text(encoding="utf-8")
ids = set(re.findall(r'define\(\s*"([a-z0-9_]+)"', categories))
ids.update(re.findall(r'defineTextOnly\(\s*"([a-z0-9_]+)"', categories))
assert ids == expected, f"JEI categories differ: missing={expected - ids}, extra={ids - expected}"
textures = re.findall(r'define\(\s*"[a-z0-9_]+"\s*,\s*"([a-z0-9_]+)"', categories)
assert len(textures) == len(expected) - 1 and len(set(textures)) == len(textures)
assert "CATEGORY_HEIGHT = 167" in categories
assert 'return define(id, texture, station, 128, 127, CATEGORY_HEIGHT);' in categories
assert '"alloy_foundry", () -> ModMachines.SECOND_ALLOY_FOUNDRY_ITEM.get(), 128, 128, 166' in categories
assert '"crimson_obsidian", () -> ModItems.CRIMSON_OBSIDIAN_ITEM.get()' in categories

category = (JAVA / "integration/jei/MachineJeiRecipeCategory.java").read_text(encoding="utf-8")
assert 'definition.texture() + "_gui.png"' in category
assert 'definition.texture() + "_gui_bg.png"' in category
assert "definition.textureX(), definition.textureY(), definition.width(), definition.height()" in category
assert "guiHelper.createBlankDrawable(definition.width(), definition.height())" in category
assert "definition.textOnly()" in category
assert "builder.addInvisibleIngredients(OUTPUT).add(new ItemStack(definition.station().get()))" in category
assert "addInvisibleIngredients(slot.role())" in category
assert "invisibleIngredients.add(fluid.fluid(), fluid.amount())" in category
assert "setOutputSlotBackground" not in category
assert "slotBuilder.setStandardSlotBackground();" in category
assert "GAUGE_CYCLE_MILLIS = 5_000L" in category
assert "drawGauge(graphics, gauge, gauge.fillFraction(operationProgress))" in category
assert "gauge.tooltipAreas().isEmpty()" in category
assert "if (!definition.textOnly() || recipe.notes().isEmpty()) return;" in category
assert "public void getTooltip(ITooltipBuilder tooltip" in category
assert "setDisplayedIngredient" not in category

gauge_model = (JAVA / "integration/jei/MachineJeiRecipe.java").read_text(encoding="utf-8")
assert "public record Gauge(" in gauge_model
assert "cycleStart" in gauge_model and "cycleEnd" in gauge_model
assert "List<TooltipArea> tooltipAreas" in gauge_model
assert "tooltipAreas.stream().anyMatch(area -> area.contains(mouseX, mouseY))" in gauge_model
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
    "SiliconFactoryBlockEntity.STEP_TICKS", "operationEnergyTooltip(energyMilli, false)",
):
    assert source in recipes, f"JEI gauge data is not derived from machine logic: {source}"
assert "crusherGauges()" in recipes
assert "activeTickEnergyRangeTooltip(MachineDefs.CRUSHER_GTU_MILLI_PER_TICK_MIN" in recipes
assert "chargeDurationTooltip(100, fastestTicks)" in recipes
assert "chargeDurationTooltip(0, slowestTicks)" in recipes
assert "crusher_energy_range" not in recipes
assert "operationEnergyTooltip(MachineDefs.CRUSHER" not in recipes, "crusher must not claim a fixed total"

# Filler tank rendering and tooltips use the two disjoint screen-authored segments.
for region in (
    "TooltipArea(55, 17, 4, 52)", "TooltipArea(59, 36, 12, 14)",
    "TooltipArea(117, 17, 4, 52)", "TooltipArea(105, 36, 12, 14)",
):
    assert region in recipes, f"missing filler tank sector: {region}"
assert "fillerTankGauge(55," in recipes and "fillerTankGauge(105," in recipes
assert "case 55 ->" in recipes and "case 105 ->" in recipes

filler_regions = [
    (55, 17, 4, 52), (59, 36, 12, 14),
    (117, 17, 4, 52), (105, 36, 12, 14),
]
def overlaps(a, b):
    return a[0] < b[0] + b[2] and a[0] + a[2] > b[0] \
        and a[1] < b[1] + b[3] and a[1] + a[3] > b[1]
for index, region in enumerate(filler_regions):
    assert not any(overlaps(region, other) for other in filler_regions[index + 1:]), \
        f"filler gauge hitboxes overlap: {region}"
    for slot in ((62, 17, 16, 16), (98, 17, 16, 16), (62, 53, 16, 16), (98, 53, 16, 16)):
        assert not overlaps(region, slot), f"filler tank hitbox overlaps item slot: {region} / {slot}"

# Container recipes reflect the real filler operation and AmpouleItem data model.
for fluid_id in (
    "water", "rectificate", "sulfuric_acid", "ethylene", "aminoblazeethanol",
    "formaldehyde", "mash", "wort", "distillate", "hot_water", "poison_potion",
):
    assert f'"{fluid_id}"' in recipes, f"missing filler fluid variant {fluid_id}"
assert "Items.BUCKET), new ItemStack(bucket), fluidId, 1_000" in recipes
assert "AmpouleItem.createFilled(fluidId, AmpouleItem.CAPACITY, false)" in recipes
assert "AmpouleItem.createFilled(fluidId, AmpouleItem.CAPACITY, true)" in recipes
assert "ModItems.EMPTY_AMPOULE.get()" in recipes and "ModItems.DURABLE_AMPOULE.get()" in recipes
assert "registeredFluid(fluidId)" in recipes
assert "fillerTankGauge(55, fluidId, MachineJeiRecipe.GaugeMotion.STATIC" in recipes

# Tap recipes match the block entity's fixed fluid amounts and ordered bottle branches.
for source in (
    '"bottle/vodka", Items.GLASS_BOTTLE, ModItems.VODKA_BOTTLE.get()',
    '"distillate", ModFluids.DISTILLATE.get(), 128, 8',
    '"bottle/beer_mug", Items.GLASS_BOTTLE, ModItems.BEER_MUG.get()',
    '"wort", ModFluids.WORT.get(), 128, 152',
    '"bucket/beer", Items.BUCKET, ModItems.BEER_BUCKET.get()',
    '"wort", ModFluids.WORT.get(), 1_000, 152',
    "MachineJeiRecipe.item(INPUT, 80, 17, input)",
    "MachineJeiRecipe.item(OUTPUT, 80, 53, output)",
):
    assert source in recipes, f"missing dispensing-tap projection: {source}"
tap_logic = (JAVA / "machines/block/entity/DispensingTapBlockEntity.java").read_text(encoding="utf-8")
bottle_branch = tap_logic.index('if (in.is(Items.GLASS_BOTTLE))')
vodka_branch = tap_logic.index('if (distillate.amount() >= 128', bottle_branch)
wort_fallback = tap_logic.index('else if (wort.amount() >= 128', vodka_branch)
assert bottle_branch < vodka_branch < wort_fallback
assert "wort.amount() >= 1000" in tap_logic

# Machine recipes have no bottom-note boxes; crimson obsidian is the sole text exception.
assert set(re.findall(r'jei\.gonzotech\.note\.([a-z0-9_]+)', recipes)) == {"crimson_obsidian"}
assert ".withTooltip(" not in recipes
assert 'recipeId(MachineJeiCategories.CRIMSON_OBSIDIAN, "reaction/lava_redstone_contact"), List.of()' in recipes
assert 'Component.translatable("jei.gonzotech.note.crimson_obsidian")' in recipes
assert "builder.addInvisibleIngredients(OUTPUT)" in category

# Both GUI layers remain complete 512px sheets and each visible category has its own art.
for texture in textures:
    path = RES / f"assets/gonzotech/textures/gui/{texture}_gui.png"
    assert path.is_file(), f"missing machine GUI foreground: {path}"
    with path.open("rb") as image:
        image.seek(16)
        width, height = struct.unpack(">II", image.read(8))
    assert (width, height) == (512, 512), f"unexpected GUI sheet dimensions: {path}"
    background = RES / f"assets/gonzotech/textures/gui/{texture}_gui_bg.png"
    assert background.is_file(), f"missing machine GUI background: {background}"
    with background.open("rb") as image:
        image.seek(16)
        bg_width, bg_height = struct.unpack(">II", image.read(8))
    assert (bg_width, bg_height) == (512, 512), f"unexpected GUI background dimensions: {background}"

fluid_source = (JAVA / "core/fluid/ModFluids.java").read_text(encoding="utf-8")
fluid_ids = set(re.findall(r'FLUID_TYPES\.register\("([a-z0-9_]+)"', fluid_source))
assert len(fluid_ids) == 7, f"unexpected registered FluidType set: {sorted(fluid_ids)}"
gauge_translation_keys = {
    "jei.gonzotech.gauge.total_per_operation", "jei.gonzotech.gauge.active_tick",
    "jei.gonzotech.gauge.up_to", "jei.gonzotech.gauge.ticks", "jei.gonzotech.gauge.start_charge",
    "jei.gonzotech.gauge.per_input_item",
}
processes = (
    "smelting", "washing", "separation", "grinding", "press_recovery", "reaction", "alloying",
    "etching", "photolithography", "vulcanization",
)
for process in processes:
    gauge_translation_keys.add(f"jei.gonzotech.gauge.process.{process}")
for locale in ("en_us", "ru_ru"):
    data = json.loads((RES / f"assets/gonzotech/lang/{locale}.json").read_text(encoding="utf-8"))
    for fluid_id in fluid_ids:
        key = f"fluid_type.gonzotech.{fluid_id}"
        assert key in data and data[key], f"missing {locale} fluid translation: {key}"
    missing = {key for key in gauge_translation_keys if not data.get(key)}
    for category_id in expected:
        key = f"jei.gonzotech.category.{category_id}"
        if not data.get(key):
            missing.add(key)
    assert not missing, f"missing {locale} JEI translations: {sorted(missing)}"
    assert data.get("jei.gonzotech.note.crimson_obsidian"), f"missing {locale} crimson obsidian text"
    note_keys = {key for key in data if key.startswith("jei.gonzotech.note.")}
    assert note_keys == {"jei.gonzotech.note.crimson_obsidian"}, f"unexpected {locale} JEI note strings: {note_keys}"
assert json.loads((RES / "assets/gonzotech/lang/ru_ru.json").read_text(encoding="utf-8"))[
    "jei.gonzotech.note.crimson_obsidian"
] == "При соприкосновении лавы и блока красного камня образуется багровый обсидиан."

plugin = (JAVA / "integration/jei/GonzoTechJeiPlugin.java").read_text(encoding="utf-8")
assert ".includeHidden()" in plugin
assert "Items.LAVA_BUCKET, Items.REDSTONE_BLOCK" in plugin
assert "hideRecipes" not in plugin + recipes
assert "RecipeUnlocks" not in recipes, "machine JEI recipes must not be progression-filtered"
docs = (ROOT / "docs/JEI-MACHINES.md").read_text(encoding="utf-8")
assert "GOST-JEI-01" in docs and "17 categories" in docs
assert "176×167" in docs and "alloy_foundry" in docs
assert "no continuous bar is drawn" in docs
print("JEI display checks passed: 17 categories, crop exceptions, disjoint filler gauges, tap/container recipes, GOST tooltips")
