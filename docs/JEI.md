# JEI: категории и display-контракты

Срез кода: **0.3.311**, `c15083f`; документальная база: `42730c3`. Сверка/миграция: 27.10.2026.


Optional plugin; отключение JEI не должно ломать сервер. Рецепты в JEI не скрываются progression-гейтами. Физический крафт/книга/страницы — другие механизмы.

## Текущие категории

В `MachineJeiCategories.ALL` **24 категории**, включая `third_grinder`. Старые документы 0.3.226 показывали 23 и 292/293 фиксированных записей: это исторические значения, не текущие счётчики. Две электропечи проецируют загруженный SMELTING, число зависит от datapacks/mods. Альтернативы ингредиентов внутри одного слота не являются отдельными display-записями.

1. `first_electric_furnace`
2. `second_electric_furnace`
3. `second_crusher`
4. `second_centrifuge`
5. `third_pcfsoz`
6. `second_grinder`
7. `third_grinder`
8. `second_press`
9. `second_alloy_foundry`
10. `third_chemical_plant`
11. `third_filler`
12. `third_silicon_factory`
13. `third_fermentation_vat`
14. `third_wort_kettle`
15. `third_distiller`
16. `third_rectifier`
17. `third_dispensing_tap`
18. `firebox`
19. `second_nuclear_firebox`
20. `boiler`
21. `stirling_generator`
22. `second_steamgen_core`
23. `first_turbine_rotor`
24. `crimson_obsidian`

Число runtime display-записей пока не пересчитано; D-E5 остаётся открытым. Названия получают точные block translation keys; новые динамические представления строятся из реальных машинных таблиц, не меняя find/consume.

## Artwork crops, coordinates, and draw order

GUI background and foreground layers keep the in-game order: `*_gui_bg.png` → animated bars → the normal `*_gui.png` foreground. The foreground is not replaced by the `_gui_bg.png` layer. Standard 512×512 sheets use crop `(128,127)` with size `176×167`; this includes the first row of the authored window. `alloy_foundry` keeps its exceptional crop `(128,128)`, `176×166` because its art has different geometry. The JEI canvas is not expanded to 256×256.

- All machine categories, including the six generator pages, shift every slot, bar, and associated tooltip hitbox **down by 1 px**, except the alloy foundry, which stays at its authored coordinates. GUI background/foreground art is not shifted.
- Text-only crimson obsidian has no visible machine slots or bars.
- Item slots stay static and use JEI's normal **16×16** slot background for both inputs and outputs.
- Machine liquid gauges are `16×52`; bars animate only flow, not item stacks.

The filler tanks mirror `FillerScreen` at `(55,17)` and `(105,17)`, each nominally `16×52`. Each visual gauge and tooltip uses two separate, non-overlapping regions:

- left tank: channel `(55,17,4,52)` and belly `(59,36,12,14)`;
- right tank: channel `(117,17,4,52)` and belly `(105,36,12,14)`.

The fill level is clipped to those authored regions, so no continuous bar is drawn through the cut-out between them. The visual regions are also the tooltip hitboxes; neither intersects the item slots. Registered-fluid ingredients remain invisible to JEI's slot renderer but are still registered for JEI lookup/focus wherever the resource is a real `Fluid`.

## Machine-specific display projections

### Crusher and custom alloys

The crusher's GTU bar reports the true active-tick energy range, while its progress tooltip reports the real duration range at the full/empty start-charge endpoints. Charge at operation start fixes progress speed; energy drawn on each working tick follows the current charge. The tooltip therefore does not invent a fixed total GTU cost.

The alloy foundry keeps all 14 named preset recipes and adds a fifteenth `custom_alloy` display record. Its paired input slots cycle through validated generic-material pairs only: the two sides are different materials and each contributes one ten-unit ingot/dust portion, or ten nuggets. The representative output is two custom-alloy items, matching the minimum two-portion batch; the JEI projection does not mutate the machine or invent a specific composition/tint.

### Fermentation and alcohol processing

The fermentation vat retains the existing six organic-input projections and adds two dynamic examples. The warm trajectory illustrates alcohol rising from 0 to 13% at the machine's actual 0.56%/min rate per 4,000 mB reference amount, when stored heat is at least 90%. The low-heat trajectory follows the source decay constants at 0% heat: alcohol 13→2%, rot 0→about 33%. It does **not** change machine balance or claim the rejected 0→98% rot trajectory. Only the fluid bar/tooltip animates; items remain static.

- Wort-kettle boiling shows the actual 0–13% alcohol / 0–98% rot mash range and 0–13% alcohol, 0% rot wort projection. Evaporation shows its actual 0–30% alcohol ceiling; container filling stays static.
- Distiller input tooltips show mash/wort alcohol and rot ranges, 0.10%/min alcohol decay and 0.40%/min rot growth during storage, plus the actual clean/poison output thresholds.
- Rectifier tooltips show the source alcohol concentrations for distillate (48%) and rectificate (100%).

### Filler salt

The filler salt tooltip is an operation amount, not a concentration percentage based on a made-up tank capacity. Calcite adds 5 mB salt per tick for 30 ticks, so its bar tooltip animates 0→150 mB and explicitly notes that actual addition is capped by the water volume. Evaporation animates the 128 mB salt batch down to zero over 120 ticks for one salt item. The source machine now names these rates as shared constants so the JEI projection and operation logic stay aligned.

### Continuous generators and one-cycle projections

- **Firebox:** one representative vanilla fuel entry and actual 9 GTH/t generation, 80 GTH/t export limit, and 1 GTH/t passive loss.
- **Nuclear firebox:** all item forms accepted by `NuclearFireboxBlockEntity.isNuclearFuel` appear as input alternatives. The tooltip shows actual 116 GTH/t generation, 282 GTH/t export limit, 0.2 GTH/t passive loss, and the 25% maximum burn-time reduction above the configured acceleration threshold.
- **Boiler:** displays all actual water-provider bucket variants plus empty-bucket return, and the continuous 20 mB water + 22 GTH → 20 mB steam active-tick cycle. The separate 1 mB/t steam condensation and 1 GTH/t passive loss are labeled as losses, not as part of the active input/output rate. The recipe also notes that the boiler needs an adjacent firebox.
- **Stirling generator:** displays 40 mB steam/t input, 1.8–2.0 GTU/t output, 6–36 mB/t active return-water range for 0–6 adjacent condensers, and the separate 1 mB/t parasitic condensate. The tooltip distinguishes the active return-water flow from passive condensation.
- **SteamGen:** six control profiles use `SteamGenMath` for one core with 0, 2, 5, 12, 22, or 26 platinum exchangers. Water, GTH, and steam are labeled per cycle. The shown output is a cycle amount, not a guaranteed sustainable stream through the machine's fluid I/O ports.
- **Turbine:** one rotor profile shows 64 mB/t steam consumption and 2 GTU/t generation from `TurbineMath`.

### Dispensing tap and filler containers

The tap uses its existing pump GUI artwork and input/output positions. It lists:

- glass bottle + 128 mB distillate → vodka bottle;
- glass bottle + 128 mB wort → beer mug;
- empty bucket + 1000 mB wort → beer bucket.

`DispensingTapBlockEntity` checks the vodka branch first for a glass bottle when at least 128 mB distillate is available and the vodka output fits. If that condition fails, the `else if` may use 128 mB wort for a beer mug (including when the vodka output is blocked). The bucket path consumes 1000 mB wort. These are instantaneous container operations, so their liquid bars are static; the required volume is shown in the tooltip. No duration or energy cost is invented.

The filler shows bucket filling for its nine existing bucket items: water, rectificate/ethanol, sulfuric acid, ethylene, aminoblazeethanol, formaldehyde, mash, wort and distillate. Bucket recipes use the actual 1000 mB container amount. Empty regular and durable ampoules each have separate 128 mB recipes for all eleven supported ampoule fluids: those nine plus hot water and poison solution. Filled ampoule stacks are created through `AmpouleItem.createFilled`, so the displayed result carries the same fluid id, amount and durable state as the real item.

Fluid ingredients are registered invisibly when a corresponding `Fluid` exists. The filler’s private tank types (for example ethylene, aminoblazeethanol, hot water and poison solution) are not registered `Fluid` objects; their filled bucket/ampoule item results remain searchable by item focus instead. Poison solution is not presented as a fluid ingredient.

### Crimson obsidian

The reaction entry has no visible ingredient or result slots, no machine GUI art, and no animation. It consists only of the requested sentence shown above.

## JEI display standard — GonzoTech GOST-JEI-01

This is a project display convention, not an external standards certification.

- **Slots:** input and output item stacks stay static at the existing 16×16 machine positions and all use JEI's standard slot frame. Outputs do not use JEI's enlarged crafting-result frame.
- **Fluid search:** visible fluid slots are replaced by bars, while real registered-fluid ingredients remain hidden-registered for JEI lookup/focus. Machine-private fluids that have no `Fluid` registry entry are represented through their filled container items.
- **Bars:** animate only actual GTU/GTH, process and fluid flows. Use one synchronized five-second visual loop for real operations; instantaneous or retained resources remain static. A bar represents the operation/active-tick flow, not machine-buffer or tank capacity.
- **Tooltips:** keep them concise and use the fluid/GTU/GTH palette. Tick labels remain white. Show only actual operation amounts, active-tick flows, real durations and relevant process conditions. The silicon-factory GTU tooltip is the total for a full chip operation, not separate step costs.
- **Text boxes:** removed from all machine recipes. The crimson-obsidian text-only page is the sole exception.
- **Display only:** JEI projections and animation do not execute recipes, mutate machine state or apply progression checks.

## Adding another category

1. Add a `MachineJeiCategories.Definition`, include it in `ALL`, and use the exact block translation key for the heading. Supply the stable id, GUI texture base name, item supplier and crop geometry; text-only categories use a blank drawable.
2. Keep the 1 px content offset on all machine/generator pages; alloy foundry is the sole `0` offset exception.
3. Project authoritative machine behavior into `MachineJeiRecipes`; do not make server processing classes depend on JEI.
4. Keep item positions from the in-game menu and add hidden fluid ingredients only for actual registered `Fluid` instances.
5. Add/synchronize category titles and tooltip strings in both `en_us.json` and `ru_ru.json`.

## Validation status

`audit/jei_display_test.py` checks the 23-category set, exact block-title translations, texture/crop geometry, one-pixel alignment exceptions, ingredient lookup, standard item slots, dynamic alloy/fermentation/salt tooltips, generator profiles, filler tank regions, tap outputs, container amounts and RU/EN strings. The static audit can run with Python. A full Java build and in-game JEI visual check still require a Java 21 development environment; the current sandbox has no JDK.

## История

0.3.213: optional plugin; 0.3.219–226: crops, анимации/динамические проекции и прежний census; 0.3.250–253: переработка/ПЦФСОЗ/third_grinder; 0.3.311 docs migration: текущая таблица категорий пересверена — 24. Исторические fixture-проверки старых docs не доказывают нынешний счёт runtime-записей. Полный лог — [HISTORY](HISTORY.md), открытые задачи — D-E1…E5 в [DEFFERED](DEFFERED.md).
