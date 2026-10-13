# Machine recipes in JEI

## Scope

GonzoTech contributes one JEI category for each of these registered machines:

1. `first_electric_furnace`
2. `second_electric_furnace`
3. `second_crusher`
4. `second_centrifuge`
5. `third_pcfsoz`
6. `second_grinder`
7. `second_press`
8. `second_alloy_foundry`
9. `third_chemical_plant`
10. `third_filler`
11. `third_silicon_factory`
12. `third_fermentation_vat`
13. `third_wort_kettle`
14. `third_distiller`
15. `third_rectifier`

Vanilla smelting recipes are copied into the two electric-furnace categories through JEI's runtime lookup with hidden entries included. Their normal vanilla JEI category remains untouched. Crusher, centrifuge, and PCFSOZ output chances are displayed separately from guaranteed outputs. Dynamic recipes are described rather than represented by a fabricated fixed result.

No recipe is hidden. Progression gates are not applied to machine recipes. Gated workbench recipes remain visible in the ordinary crafting category and receive only a small marker with a tooltip describing the gate.

## Adding another machine category

The category renderer and recipe model are shared. A new machine normally needs these additions:

1. Add a `MachineJeiCategories.Definition` and include it in `MachineJeiCategories.ALL`. Supply a stable category id, an existing GUI texture base name, and the machine's item supplier.
2. Add a JEI-agnostic display projection beside the authoritative processing table when the table is enumerable. Do not make server processing classes depend on JEI.
3. Add the projection to `MachineJeiRecipes.build()` (or a small dedicated adapter for dynamic operations), using machine-layout coordinates for item/fluid slots and translated notes where necessary.
4. Add the category title and any new notes to both `en_us.json` and `ru_ru.json`.

JEI now uses the normal 512×512 foreground `*_gui.png`, not `*_gui_bg.png`. The crop remains at source-sheet origin `(128, 128)` with size `176×166`; this change does not move any recipe slots. The in-game screen's foreground sheet supplies the machine's slot/window artwork. No new image is required for these categories.

Custom registered fluid names are translated with `fluid_type.gonzotech.<fluid_type_id>` in both locale files. Add the FluidType's display name there; the separate registry id shown with advanced tooltips is expected. A dynamic output must be described or represented with all its actual variants, never as a fixed composition or guaranteed byproduct when the machine does not guarantee it.

## JEI display standard — GonzoTech GOST-JEI-01

This is a project display convention, not an external standards certification. The goal is to make the recipe page read like a short, repeatable machine cycle rather than a paragraph of notes.

- **Machine artwork:** use the normal `*_gui.png` sheet, cropped to the actual machine window. Preserve its authored artwork and do not offset recipe slots to compensate for choosing the wrong layer.
- **Slot frame:** every item icon, including outputs, stays at the normal 16×16 ingredient size and uses JEI's standard slot background. Do not apply its enlarged crafting-output frame to machine recipes.
- **Coordinate space:** recipe-slot positions are local to the cropped `176×166` window. For coordinates measured on the `512×512` sheet in `SUPER_CRUCIAL_FILE.md`, convert with `jeiX = sheetX - 128` and `jeiY = sheetY - 128`. Do not move slots based on a full-screen screenshot without identifying its crop origin.
- **One page represents one operation:** show the real inputs, animate the machine's progress bar from empty to complete, reveal the result at completion, then repeat. Multi-lane machines must not silently multiply a single operation's output or energy.
- **Normalized energy bar:** the displayed gauge represents the energy for one operation, not the block entity's full storage capacity. At the start it shows the exact per-operation cost; as progress advances, it drains in step with the operation. Derive cost and duration from authoritative machine code/tables, including rounding behavior where relevant. Never substitute an illustrative number for missing data.
- **Fluids and outcomes:** use the actual fluid type, localized display name, amount, and accepted variants. Probabilistic byproducts remain visibly probabilistic; the animation must not turn a chance into a guarantee.
- **Minimal notes:** reserve text for exceptions, dynamic state, and probabilities that the animation cannot express. Do not duplicate the main input → process → output explanation in prose.
- **Display only:** JEI animation is a visual simulation. It must not execute recipes, mutate machine state, or apply progression checks.

The category currently has a shared renderer and static display recipes; the animated cycle above is the target convention, not yet implemented. Electric-furnace smelting is the natural first pilot because its actual duration and per-item energy are already defined in machine code. Other machines can adopt the same convention when their display projection has authoritative process data.

## Coordinating visual adjustments

For a future alignment pass, send the machine/category and recipe id, plus (1) a JEI screenshot at a known UI scale and (2) the matching in-game machine GUI. If an element truly needs moving, mark its desired **top-left slot coordinate relative to the 176×166 recipe window**, or annotate the image with that coordinate. A source-sheet coordinate uses the `-128` conversion above. This pass deliberately changes only the artwork layer and fluid names; the existing slot coordinates are unchanged.

## Optional dependency and validation

The JEI 20.0.0.4 APIs are compile-only; the NeoForge runtime artifact uses Gradle's `runtimeOnly` configuration for development launches and is not embedded in the mod JAR. JEI remains an optional client dependency. The machine categories are registered from one annotated `IModPlugin`, with no changes to recipe visibility.

The current implementation was not compiled or launched in Minecraft because this environment has no JDK. Validate the full JEI client path, image crops, layout, and JEI reload behavior when a Java 21 development environment is available.
