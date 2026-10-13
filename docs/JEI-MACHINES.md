# Machine recipes in JEI

## Scope

The plugin registers 17 categories: 15 machine categories, the dispensing tap, and a text-only crimson-obsidian reaction entry.

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
16. `third_dispensing_tap`
17. `crimson_obsidian` (text-only; no visible ingredient/result slots)

Vanilla smelting recipes are projected into both electric-furnace categories with hidden JEI entries included; their normal vanilla category remains untouched. Crusher, centrifuge and PCFSOZ output chances remain visible. No JEI recipes are hidden, and machine recipe visibility is not progression-gated.

The crimson-obsidian page has one text box and no visible slots. The output block remains an invisible JEI ingredient for search/focus; lava buckets and redstone blocks are registered as category catalysts. The text is: “При соприкосновении лавы и блока красного камня образуется багровый обсидиан.”

## Artwork crops and geometry

GUI background and foreground layers keep the in-game order: `*_gui_bg.png` → gauges → the normal `*_gui.png` foreground. The foreground is not replaced by the `_gui_bg.png` layer. Standard 512×512 sheets use crop `(128,127)` with size `176×167`; this includes the first row of the authored window. `alloy_foundry` is kept at its existing exceptional crop `(128,128)`, `176×166` because its art has different geometry. The JEI canvas is not expanded to 256×256. Existing item-slot and gauge coordinates are unchanged.

The filler tanks mirror `FillerScreen` at `(55,17)` and `(105,17)`, each nominally `16×52`. Each visual gauge and tooltip uses two separate, non-overlapping regions:

- left tank: channel `(55,17,4,52)` and belly `(59,36,12,14)`;
- right tank: channel `(117,17,4,52)` and belly `(105,36,12,14)`.

The fill level is clipped to those authored regions, so no continuous bar is drawn through the cut-out between them. The visual regions are also the tooltip hitboxes; neither intersects the item slots. Invisible fluid ingredients preserve JEI lookup/focus wherever the tank resource is a registered `Fluid`.

## Added display projections

### Crusher

The GTU gauge is animated and the process gauge reports the real duration range at the full/empty start-charge endpoints. The charge at operation start fixes progress speed; energy drawn on each working tick follows the *current* charge. The tooltip therefore shows the source-derived active-tick range and duration, not a fabricated total GTU amount for the whole crushing operation.

### Dispensing tap

The category uses the existing pump GUI artwork and the tap's existing input/output slot positions. It lists:

- glass bottle + 128 mB distillate → vodka bottle;
- glass bottle + 128 mB wort → beer mug;
- empty bucket + 1000 mB wort → beer bucket.

`DispensingTapBlockEntity` checks the vodka branch first for a glass bottle when at least 128 mB distillate is available and the vodka output fits. If that condition fails, the `else if` may use 128 mB wort for a beer mug (including when the vodka output is blocked). The bucket path consumes 1000 mB wort. These are instantaneous container operations, so their liquid bars are static; the required volume is shown in the tooltip. No duration or energy cost is invented.

### Filler containers

The filler now shows bucket filling for its nine existing bucket items: water, rectificate/ethanol, sulfuric acid, ethylene, aminoblazeethanol, formaldehyde, mash, wort and distillate. Bucket recipes use the actual 1000 mB container amount. Empty regular and durable ampoules each have separate 128 mB recipes for all eleven supported ampoule fluids: those nine plus hot water and poison solution. Filled ampoule stacks are created through `AmpouleItem.createFilled`, so the displayed result carries the same fluid id, amount and durable state as the real item.

Fluid ingredients are registered invisibly when a corresponding `Fluid` exists. The filler’s private tank types (for example ethylene, aminoblazeethanol, hot water and poison solution) are not registered `Fluid` objects; their filled bucket/ampoule item results remain searchable by item focus instead.

### Crimson obsidian

The reaction entry has no visible ingredient or result slots, no machine GUI art, and no animation. It consists only of the requested sentence shown above.

## JEI display standard — GonzoTech GOST-JEI-01

This is a project display convention, not an external standards certification.

- **Slots:** input and output item stacks stay static at the existing 16×16 machine positions and all use JEI's standard slot frame. Outputs do not use JEI's enlarged crafting-result frame.
- **Fluid search:** visible fluid slots are replaced by bars, while real registered-fluid ingredients remain hidden-registered for JEI lookup/focus. Machine-private fluids that have no `Fluid` registry entry are represented through their filled container items.
- **Bars:** animate only actual GTU/GTH, process and fluid flows. Use one synchronized five-second visual loop for real operations; instantaneous or retained resources remain static. A bar represents the operation/active-tick flow, not machine-buffer or tank capacity.
- **Tooltips:** keep them to actual amounts and units, in the fluid/GTU/GTH palette: `Суммарно на операцию`, `За активный тик`, and the real duration. Only data affecting duration/speed or actual cost/flow is shown. The silicon-factory GTU tooltip is the total for a full chip operation; it is not split into separate step costs. The fermentation vat has no invented per-item GTH total because feeding and fermentation are continuous.
- **Text boxes:** removed from all machine recipes. The crimson-obsidian text-only page is the sole exception.
- **Display only:** JEI projections and animation do not execute recipes, mutate machine state or apply progression checks.

## Adding another category

1. Add a `MachineJeiCategories.Definition`, including it in `ALL`, with a stable id, GUI texture base name, item supplier and the correct crop geometry. Text-only categories use a blank drawable.
2. Project the authoritative machine behavior into `MachineJeiRecipes`; do not make server processing classes depend on JEI.
3. Keep item positions from the in-game menu and add hidden fluid ingredients only for actual registered `Fluid` instances.
4. Add the category title and any necessary display strings to both `en_us.json` and `ru_ru.json`.

## Validation status

`audit/jei_display_test.py` checks the category set, texture/crop geometry, ingredient lookup, standard item slots, filler tank regions, tap outputs, container amounts and RU/EN tooltip keys. Static Python audits are available in this repository. A full Java build and in-game JEI visual check require a Java 21 development environment; the current sandbox has no JDK.
