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

Existing 512×512 GUI background PNGs are cropped from the normal 176×166 machine window region; no new image is required for these categories. A dynamic output must be described or represented with all its actual variants, never as a fixed composition or guaranteed byproduct when the machine does not guarantee it.

## Optional dependency and validation

The JEI 20.0.0.4 APIs are compile-only; the NeoForge runtime artifact is included only in the local development runtime. JEI remains an optional client dependency. The machine categories are registered from one annotated `IModPlugin`, with no changes to recipe visibility.

The current implementation was not compiled or launched in Minecraft because this environment has no JDK. Validate the full JEI client path, image crops, layout, and JEI reload behavior when a Java 21 development environment is available.
