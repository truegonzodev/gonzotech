#!/usr/bin/env python3
"""Static contract checks for JEI categories, GUI geometry, recipes, and GOST tooltips."""
import json
from pathlib import Path
import re
import struct

import _ver

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/gonzotech"
RES = ROOT / "src/main/resources"

expected_categories = {
    "first_electric_furnace", "second_electric_furnace", "second_crusher",
    "second_centrifuge", "third_pcfsoz", "second_grinder", "second_press",
    "second_alloy_foundry", "third_chemical_plant", "third_filler",
    "third_silicon_factory", "third_fermentation_vat", "third_wort_kettle",
    "third_distiller", "third_rectifier", "third_dispensing_tap", "firebox",
    "second_nuclear_firebox", "boiler", "stirling_generator", "second_steamgen_core",
    "first_turbine_rotor", "crimson_obsidian", "reprocessing_grinder",
}

categories = (JAVA / "integration/jei/MachineJeiCategories.java").read_text(encoding="utf-8")
category_fields = re.findall(
    r'public static final Definition (\w+) = define(?:Aligned)?\(\s*"([a-z0-9_]+)"', categories)
text_fields = re.findall(
    r'public static final Definition (\w+) = defineTextOnly\(\s*"([a-z0-9_]+)"', categories)
category_ids = {category_id for _, category_id in category_fields + text_fields}
assert category_ids == expected_categories, (
    f"JEI categories differ: missing={expected_categories - category_ids}, "
    f"extra={category_ids - expected_categories}"
)
assert len(category_fields) == 23 and len(text_fields) == 1

all_block = categories.split("public static final List<Definition> ALL = List.of(", 1)[1].split(");", 1)[0]
listed_fields = set(re.findall(r"\b[A-Z][A-Z0-9_]*\b", all_block))
assert listed_fields == {field for field, _ in category_fields + text_fields}, "ALL omits or duplicates a category"

textures = re.findall(
    r'define(?:Aligned)?\(\s*"[a-z0-9_]+"\s*,\s*"([a-z0-9_]+)"', categories)
assert len(textures) == 23 and len(set(textures)) == len(textures)
assert "CATEGORY_HEIGHT = 167" in categories
assert 'return define(id, texture, station, 128, 127, CATEGORY_HEIGHT, 1);' in categories
assert '"second_alloy_foundry", "alloy_foundry", () -> ModMachines.SECOND_ALLOY_FOUNDRY_ITEM.get(),\n        128, 128, 166, 0' in categories
assert '"crimson_obsidian", () -> ModItems.CRIMSON_OBSIDIAN_ITEM.get()' in categories
generator_ids = {
    "firebox", "second_nuclear_firebox", "boiler", "stirling_generator",
    "second_steamgen_core", "first_turbine_rotor",
}
for generator_id in generator_ids:
    default_offset_definition = (
        r'Definition \w+ = define\(\s*"' + generator_id
        + r'"\s*,\s*"[a-z0-9_]+"\s*,\s*\(\) -> [A-Za-z0-9_.]+\(\)\);'
    )
    assert re.search(default_offset_definition, categories), \
        f"generator category does not use the default +1 px content offset: {generator_id}"
assert "defineAligned" not in categories

category = (JAVA / "integration/jei/MachineJeiRecipeCategory.java").read_text(encoding="utf-8")
assert 'definition.texture() + "_gui.png"' in category
assert 'definition.texture() + "_gui_bg.png"' in category
assert 'Component.translatable("block.gonzotech." + definition.id())' in category
assert "slot.y() + definition.contentOffsetY()" in category
assert "drawGauge(graphics, gauge, gauge.fillFraction(operationProgress), definition.contentOffsetY())" in category
assert "mouseY - definition.contentOffsetY()" in category
assert "definition.textureX(), definition.textureY(), definition.width(), definition.height()" in category
assert "guiHelper.createBlankDrawable(definition.width(), definition.height())" in category
assert "definition.textOnly()" in category
assert "builder.addInvisibleIngredients(OUTPUT).add(new ItemStack(definition.station().get()))" in category
assert "addInvisibleIngredients(slot.role())" in category
assert "invisibleIngredients.add(fluid.fluid(), fluid.amount())" in category
assert "setOutputSlotBackground" not in category
assert "slotBuilder.setStandardSlotBackground();" in category
assert "GAUGE_CYCLE_MILLIS = 5_000L" in category
assert "gauge.tooltipAreas().isEmpty()" in category
assert "if (!definition.textOnly() || recipe.notes().isEmpty()) return;" in category
assert "public void getTooltip(ITooltipBuilder tooltip" in category
assert "setDisplayedIngredient" not in category

recipe_model = (JAVA / "integration/jei/MachineJeiRecipe.java").read_text(encoding="utf-8")
assert "public record Gauge(" in recipe_model
assert "cycleStart" in recipe_model and "cycleEnd" in recipe_model
assert "List<TooltipArea> tooltipAreas" in recipe_model
assert "@Nullable DynamicTooltip dynamicTooltip" in recipe_model
assert "dynamicTooltip.lines(phaseProgress(operationProgress))" in recipe_model
assert "motion == GaugeMotion.STATIC) return 1.0F" in recipe_model
assert "motion == GaugeMotion.CONSUME ? 1.0F - progress : progress" in recipe_model
assert "public boolean contains(double mouseX, double mouseY)" in recipe_model

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
    "addGeneratorRecipes(recipes)", "SteamGenMath.gthPerCycleMilli(1, exchangers)",
    "SteamGenMath.waterPerCycle(1, exchangers)",
    "SteamGenMath.steamPerCycleMilli(1, 150 * exchangers, exchangers)",
    "TurbineMath.maxSteamConsumption(rotors)", "TurbineMath.gtuMilliForSteam(steamMb, 0)",
    "NuclearFireboxBlockEntity.isNuclearFuel(stack)", "waterProviderItems()",
):
    assert source in recipes, f"JEI gauge data is not derived from machine logic: {source}"
assert "crusherGauges()" in recipes
assert "activeTickEnergyRangeTooltip(MachineDefs.CRUSHER_GTU_MILLI_PER_TICK_MIN" in recipes
assert "chargeDurationTooltip(100, fastestTicks)" in recipes
assert "chargeDurationTooltip(0, slowestTicks)" in recipes
assert "crusher_energy_range" not in recipes
assert "operationEnergyTooltip(MachineDefs.CRUSHER" not in recipes, "crusher must not claim a fixed total"
assert "int[] exchangerCounts = {0, 2, 5, 12, 22, 26};" in recipes
assert 'MachineJeiRecipe.items(INPUT, 80, 53, fuels)' in recipes
assert 'MachineJeiRecipe.item(INPUT, 44, 53, Items.COAL)' in recipes
assert 'MachineJeiRecipe.items(INPUT, 8, 17, waterProviderItems())' in recipes
assert 'MachineJeiRecipe.item(OUTPUT, 8, 53, Items.BUCKET)' in recipes
assert 'MachineJeiRecipe.GaugeMotion.PRODUCE, steamTooltip' in recipes
assert 'fluidAmountLine("per_cycle", steamMb, GtUnits.STEAM)' in recipes
firebox_menu = (JAVA / "machines/menu/FireboxMenu.java").read_text(encoding="utf-8")
nuclear_menu = (JAVA / "machines/menu/NuclearFireboxMenu.java").read_text(encoding="utf-8")
boiler_menu = (JAVA / "machines/menu/BoilerMenu.java").read_text(encoding="utf-8")
assert "FireboxBlockEntity.SLOT_FUEL, 44, 53" in firebox_menu
assert "NuclearFireboxBlockEntity.SLOT_FUEL, 80, 53" in nuclear_menu
assert "BoilerBlockEntity.SLOT_WATER_IN, 8, 17" in boiler_menu
assert "BoilerBlockEntity.SLOT_BUCKET_OUT, 8, 53" in boiler_menu
assert 'energyAmountLine("generated_per_tick", gtuMilli, false)' in recipes

# Dynamic custom alloy uses paired, distinct-material, minimum-batch input alternatives.
alloy_recipes = (JAVA / "machines/processing/AlloyFoundryRecipes.java").read_text(encoding="utf-8")
alloy_catalog = (JAVA / "machines/processing/AlloyMaterialCatalog.java").read_text(encoding="utf-8")
assert alloy_recipes.count('preset("') == 14
assert "displays.add(customAlloyDisplay());" in alloy_recipes
assert "AlloyMaterialCatalog.genericInputs().entrySet()" in alloy_recipes
assert "a.getValue().material().id().equals(b.getValue().material().id())" in alloy_recipes
assert "firstSlot.add(new ItemStack(a.getKey(), AlloyMaterialCatalog.UNITS_PER_PORTION / unitsA))" in alloy_recipes
assert "secondSlot.add(new ItemStack(b.getKey(), AlloyMaterialCatalog.UNITS_PER_PORTION / unitsB))" in alloy_recipes
assert "new ItemStack(ModItems.CUSTOM_ALLOY.get(), 2)" in alloy_recipes
assert "if (entry.getValue().genericAllowed())" in alloy_catalog
assert 'recipeId(MachineJeiCategories.SECOND_ALLOY_FOUNDRY, display.id())' in recipes

# Filler tank rendering/tooltips use the two screen-authored, disjoint segments.
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

# Salt animation reports operation mB and the actual water-volume cap, never fake 0–100% salinity.
filler_entity = (JAVA / "machines/block/entity/FillerBlockEntity.java").read_text(encoding="utf-8")
for declaration in (
    "SALT_PER_ITEM_MB = 128", "CALCITE_DISSOLUTION_TICKS = 30",
    "CALCITE_SALT_MB_PER_TICK = 5", "SALT_EVAPORATION_TICKS_PER_ITEM = 120",
    "SALT_EVAPORATION_GTH_PER_TICK = 11",
):
    assert declaration in filler_entity, f"missing shared filler operation constant: {declaration}"
assert "Math.min(leftFluidAmount, leftSaltMb + CALCITE_SALT_MB_PER_TICK)" in filler_entity
assert "leftSaltMb >= SALT_PER_ITEM_MB" in filler_entity
assert "leftSaltMb -= SALT_PER_ITEM_MB" in filler_entity
assert 'saltAmountTooltip("jei.gonzotech.gauge.salt_added"' in recipes
assert "progress * FillerBlockEntity.CALCITE_SALT_MB_PER_TICK" in recipes
assert "FillerBlockEntity.CALCITE_DISSOLUTION_TICKS" in recipes
assert "(1.0 - progress) * FillerBlockEntity.SALT_PER_ITEM_MB" in recipes
assert "saltPercentageTooltip" not in recipes and "* 100.0" not in recipes
assert "salt_dissolution_limit" in recipes

# Fermentation animation is derived from the source rates and keeps the agreed 13→2 / 0→~33 path.
fermentation = (JAVA / "machines/block/entity/FermentationVatBlockEntity.java").read_text(encoding="utf-8")
for declaration in (
    "MAX_ALCOHOL = 13.0", "MAX_ROT = 98.0", "FERMENTATION_HEAT_THRESHOLD_PERCENT = 90.0",
    "ALCOHOL_GAIN_PERCENT_PER_MINUTE = 0.56",
    "ALCOHOL_LOSS_PERCENT_PER_MINUTE_PER_SHORTAGE = 0.01",
    "ROT_GAIN_PERCENT_PER_MINUTE_PER_SHORTAGE = 0.03",
):
    assert declaration in fermentation, f"fermentation source contract changed: {declaration}"
assert "heatPercent >= FERMENTATION_HEAT_THRESHOLD_PERCENT" in fermentation
assert "FERMENTATION_HEAT_THRESHOLD_PERCENT - heatPercent" in fermentation
assert '"ferment/alcohol_growth"' in recipes and '"ferment/low_heat_rot"' in recipes
assert "MachineJeiRecipes::lowHeatFermentationTooltip" in recipes
assert "ALCOHOL_LOSS_PERCENT_PER_MINUTE_PER_SHORTAGE * maximumShortage" in recipes
assert "ROT_GAIN_PERCENT_PER_MINUTE_PER_SHORTAGE * maximumShortage" in recipes
assert "FermentationVatBlockEntity.MAX_ALCOHOL - 2.0" in recipes
assert "33.0 * progress" not in recipes and "98.0 * progress" not in recipes
assert "GtUnits.x1(percent)" in recipes
wort_source = (JAVA / "machines/block/entity/WortKettleBlockEntity.java").read_text(encoding="utf-8")
distiller_source = (JAVA / "machines/block/entity/DistillerBlockEntity.java").read_text(encoding="utf-8")
rectifier_source = (JAVA / "machines/block/entity/RectifierBlockEntity.java").read_text(encoding="utf-8")
assert "MAX_WORT_ALCOHOL = 30.0" in wort_source and "EVAPORATE_RATE = 2" in wort_source
assert 'GtUnits.alcoholLore("0–30")' in recipes and "WortKettleBlockEntity.MAX_WORT_ALCOHOL" in recipes
assert "double rotGain = 0.40 / 1200.0" in distiller_source
assert "double alcLoss = 0.10 / 1200.0" in distiller_source
assert "rawInput.rotPercent() > 8.0 || poison.amount() > 127" in distiller_source
assert "PROCESS_DISTILLATE_COST = 2" in rectifier_source and "PROCESS_RECTIFICATE_YIELD = 1" in rectifier_source
assert 'GtUnits.alcoholLore("48")' in recipes and 'GtUnits.alcoholLore("100")' in recipes
# At 0% heat: shortage=90; 13→2 requires 11/0.9 minutes, adding 0.03×90% rot/min ≈33% rot.
alcohol_loss = 0.01 * 90
rot_gain = 0.03 * 90
elapsed_minutes = (13 - 2) / alcohol_loss
assert abs(rot_gain * elapsed_minutes - 33.0) < 1e-9

# Filler container recipes reflect the real item data model and registered-fluid focus.
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

# Only crimson obsidian carries explanatory note text; process ticks are explicitly white.
assert set(re.findall(r'jei\.gonzotech\.note\.([a-z0-9_]+)', recipes)) == {"crimson_obsidian"}
assert ".withTooltip(" not in recipes
assert 'recipeId(MachineJeiCategories.CRIMSON_OBSIDIAN, "reaction/lava_redstone_contact"), List.of()' in recipes
assert 'Component.translatable("jei.gonzotech.note.crimson_obsidian")' in recipes
assert "withStyle(ChatFormatting.WHITE)" in recipes

# All machine GUI layers remain complete 512px sheets and each visible category has its own art.
for texture in textures:
    path = RES / f"assets/gonzotech/textures/gui/classic/{texture}_gui.png"
    assert path.is_file(), f"missing machine GUI foreground: {path}"
    with path.open("rb") as image:
        image.seek(16)
        width, height = struct.unpack(">II", image.read(8))
    assert (width, height) == (512, 512), f"unexpected GUI sheet dimensions: {path}"
    background = RES / f"assets/gonzotech/textures/gui/classic/{texture}_gui_bg.png"
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
    "jei.gonzotech.gauge.per_input_item", "jei.gonzotech.gauge.generated_per_tick",
    "jei.gonzotech.gauge.firebox_export_limit", "jei.gonzotech.gauge.passive_loss_per_tick",
    "jei.gonzotech.gauge.passive_condensate", "jei.gonzotech.gauge.boiler_steam_condensation",
    "jei.gonzotech.gauge.per_cycle", "jei.gonzotech.gauge.alcohol_ceiling",
    "jei.gonzotech.gauge.alcohol_per_minute", "jei.gonzotech.gauge.at",
    "jei.gonzotech.gauge.boiler_requires_firebox", "jei.gonzotech.gauge.distiller_clean_condition",
    "jei.gonzotech.gauge.distiller_poison_condition", "jei.gonzotech.gauge.distiller_storage_decay",
    "jei.gonzotech.gauge.fermentation_heat_condition", "jei.gonzotech.gauge.fermentation_low_heat_basis",
    "jei.gonzotech.gauge.for_mash_amount", "jei.gonzotech.gauge.nuclear_firebox_acceleration",
    "jei.gonzotech.gauge.salt_added", "jei.gonzotech.gauge.salt_batch_remaining",
    "jei.gonzotech.gauge.salt_dissolution_limit", "jei.gonzotech.gauge.steamgen_profile",
    "jei.gonzotech.gauge.stirling_condensate_efficiency", "jei.gonzotech.gauge.stirling_condensers",
    "jei.gonzotech.gauge.turbine_profile",
}
processes = (
    "smelting", "crushing", "washing", "separation", "grinding", "press_recovery", "reaction", "alloying",
    "etching", "photolithography", "vulcanization",
)
for process in processes:
    gauge_translation_keys.add(f"jei.gonzotech.gauge.process.{process}")

for locale in ("en_us", "ru_ru"):
    data = json.loads((RES / f"assets/gonzotech/lang/{locale}.json").read_text(encoding="utf-8"))
    for fluid_id in fluid_ids:
        key = f"fluid_type.gonzotech.{fluid_id}"
        assert data.get(key), f"missing {locale} fluid translation: {key}"
    missing = {key for key in gauge_translation_keys if not data.get(key)}
    for category_id in expected_categories:
        category_key = f"jei.gonzotech.category.{category_id}"
        block_key = f"block.gonzotech.{category_id}"
        if not data.get(category_key) or not data.get(block_key):
            missing.add(category_key if not data.get(category_key) else block_key)
        elif data[category_key] != data[block_key]:
            raise AssertionError(f"{locale} JEI title does not match {block_key}")
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

_ver.at_least("0.3.241", "JEI machine display audit")
inventory = (ROOT / "docs/JEI-RECIPE-INVENTORY.md").read_text(encoding="utf-8")
docs = (ROOT / "docs/JEI-MACHINES.md").read_text(encoding="utf-8")
assert "23 категории" in inventory and "**292**" in inventory and "**293**" in inventory
assert "GOST-JEI-01" in docs and "23 categories" in docs
assert "176×167" in docs and "alloy_foundry" in docs
assert "no continuous bar is drawn" in docs
assert "down by 1 px" in docs and "alloy foundry" in docs
assert "0→150 mB" in docs and "0→about 33%" in docs
print("JEI display checks passed: 23 categories, exact titles, offsets/crops, dynamic recipes, generator cycles, filler/tap contracts, and RU/EN tooltips")
