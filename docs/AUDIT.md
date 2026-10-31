# Проверки проекта и история использования

**Последний полный локальный прогон, PERF-001/1 / 0.3.327: 88 PASS / 0 SKIP / 0 FAIL.** Parallel4321assertions/9mutants; double7498/7mutants; loaded399/23mutants. Это headless, не nativeMinecraft.1 открыт: cold-source отказ не воспроизведён;5 закрыт326/удалить330.

## Команды и доказательства

- `python3 audit/run_all.py` — все `*_test.py` отдельными процессами. **SKIP ≠ PASS**, FAIL не маскируется. Код возврата 0 только без FAIL.
- `python3 audit/validate.py` — ресурсы, JSON, локализации и связность контента; это не MC-компиляция.
- `python3 audit/doc_links.py` — Markdown-ссылки, локальные пути и старые текстовые ссылки с явной картой архива. Замороженные архивные тексты не правятся ради нового layout.
- `python3 audit/docs_structure_test.py` — 75 SHA-256, полный исходный inventory, маршруты переноса/все 4173 source-пункта, whitelist корня, разные task ID и четыре патча удержания, разделение доски/заметок и наличие реестра Sinks/ресурсных BE. Проверяет сохранность текста/структуры, **не математически полную сохранность авторского смысла**.
- `python3 audit/content_inventory.py` — ресурсный census в `audit/reports/content-inventory.csv`, не в docs. `--check` сравнивает с локальным отчётом. Это не survival reachability solver: машинные таблицы/события и намерение автора проверяются отдельно.
- JDK/harness/typecheck и Minecraft build/GameTest — разные уровни. Общие скобки/Tree-sitter не доказывают Mojmap/API compatibility. Недоступный JDK/Pillow отмечается как SKIP.
- JDK 21 + зависимости: `./gradlew build runRadiationTest --offline --no-configuration-cache`. Тестовый мир `run-radiation-merge-tests-311`, namespace gonzotech; 23 GameTests в RadiationGameTests (0.3.314). Не считать наличие тестов выполнением клиентского ЛКМ/ПКМ/Q.

## Реестр Python-инструментов

Описание взято из docstring; конкретный контракт — в самом скрипте. История введения/изменений восстанавливается из Git и версионных записей [HISTORY](HISTORY.md), не по предположению о дате имени файла. Java self-tests и stubs лежат рядом в audit; ряд Python wrappers компилирует/запускает их.

| Скрипт | Назначение/самоописание |
|---|---|
| [_ver.py](../audit/_ver.py) | Единая проверка версии мода для пинов (08.10.2026). |
| [virtual_replacement_test.py](../audit/virtual_replacement_test.py) | PERF-001/11: actualsetVirtual/get/remove/callers,9363assertions/8mutants incl exact323; MC/storage/manualMixinboundaries, не nativeBE. |
| [slot_capacity_test.py](../audit/slot_capacity_test.py) | PERF-001/2: production slot/admission и upstream wrappers,354assertions/10mutants; MC doubles/manual mixin wiring, не native bootstrap. |
| [routing_cache_test.py](../audit/routing_cache_test.py) | PERF-001/4: production cache methods, пустой/положительный кэш, version/TTL, lookup boundaries;126assertions и5mutants, не native cart/transfer test. |
| [legacy_accumulator_test.py](../audit/legacy_accumulator_test.py) | PERF-001/7–8: настоящие receive/push методы I/II + GtBuffer/Defs/TickIntakeBudget, aggregate intake/equilibrium; --mutation-check8assertion kills, не fullMC compile. |
| [model_texture_paths_test.py](../audit/model_texture_paths_test.py) | TEX-001: реальные item/blockstate/model цепочки и существование текстур после авторского переноса в third/; --mutation-check проверяет9подмен, не GPU render. |
| [achievement_tree_test.py](../audit/achievement_tree_test.py) | Static regression contract for the Energy advancement tree and event triggers.""" |
| [air_filter_test.py](../audit/air_filter_test.py) | Actual filter core + GUI/resource wiring. Not an in-game NeoForge test. |
| [alcohol_test.py](../audit/alcohol_test.py) | Compile/run the actual pure-Java alcohol-dose core, without Gradle/Minecraft. |
| [audit_followup_test.py](../audit/audit_followup_test.py) | 27 Sep follow-up: execute production method bodies with API stubs (NOT Minecraft). |
| [autominer_markers_test.py](../audit/autominer_markers_test.py) | Pins 0.3.256-0.3.263 — метки автошахтера, Плотник, сеть, сейсмика и буры. |
| [blast_furnace_test.py](../audit/blast_furnace_test.py) | Blast furnace (0.3.65): pure 3x3x3 layout self-test via ECJ + wiring pins. |
| [cleanroom_test.py](../audit/cleanroom_test.py) | Compile/run the actual pure-Java clean-room core, without Gradle/Minecraft. |
| [composite_bundle_test.py](../audit/composite_bundle_test.py) | Вспомогательный скрипт / source check |
| [content_inventory.py](../audit/content_inventory.py) | Reproducible resource inventory, NOT a survival reachability solver. |
| [custom_alloy_name_test.py](../audit/custom_alloy_name_test.py) | Пин name-hook'а custom_alloy (инструмент агента, 08.10.2026). |
| [desolation_biome_injection_test.py](../audit/desolation_biome_injection_test.py) | Пин: desolation в оверворлде — мутация пресета ПОСЛЕ сборки реестров. |
| [dispensing_tap_color_test.py](../audit/dispensing_tap_color_test.py) | Ensure the dispensing tap uses the same liquid text colors as the source machines.""" |
| [distiller_raw_fluid_test.py](../audit/distiller_raw_fluid_test.py) | Static regression pins for exclusive mash/wort buffering in the distiller.""" |
| [double_chest_routing_test.py](../audit/double_chest_routing_test.py) | Whole production routing with fresh54 compoundviews, current physical owners, replacement/selfroute/shield; exact325negative/9assertionmutants. Not nativeMC. |
| [item_loaded_chunks_test.py](../audit/item_loaded_chunks_test.py) | Whole production ItemRouting + ItemFilterRouting/SlotCapacity; observed loaded boundaries; exact324routers/comparator negatives and23assertion mutations. Not nativeMC. |
| [docs_structure_test.py](../audit/docs_structure_test.py) | Архив, whitelist, маршруты, task ID/retention и проекции текущего кода. |
| [doc_links.py](../audit/doc_links.py) | Проверка ссылок на документы Gonzo Tech. |
| [door_leak_test.py](../audit/door_leak_test.py) | Open hermetic-door physics (0.3.61): outside drain -8%/s, cross-room |
| [explosives_content_test.py](../audit/explosives_content_test.py) | Regression pins for the gunpowder block and industrial TNT content.""" |
| [faint_ui_test.py](../audit/faint_ui_test.py) | Execute extracted production hook bodies against tiny API stubs, plus static wiring. |
| [filler_scale_test.py](../audit/filler_scale_test.py) | Pin third_filler's exact milli sync, tenth-unit labels, and user-selected capacity.""" |
| [foam_test.py](../audit/foam_test.py) | Compile/run the actual pure-Java fluid-foam core, without Gradle/Minecraft. |
| [gate_tooltip_test.py](../audit/gate_tooltip_test.py) | Headless production-core checks + static policy/resource wiring checks. |
| [graphite_tvel_test.py](../audit/graphite_tvel_test.py) | Regression pins for graphite synthesis and the first reactor-fuel content slice.""" |
| [greatsword_content_test.py](../audit/greatsword_content_test.py) | Static content/regression checks for the monolith and greatsword feature.""" |
| [gui_alignment_test.py](../audit/gui_alignment_test.py) | Static production wiring + actual PNG geometry, not an in-game renderer test. |
| [inside_exposure_test.py](../audit/inside_exposure_test.py) | Actual insideDose/physical collector/owner-slot bodies + real contour/cluster core; world boundary doubles; mutation mode. |
| [instrument_hud_test.py](../audit/instrument_hud_test.py) | Actual InstrumentHud registration/callback + source contracts; not GPU/chat rendering. |
| [java_bracket_balance_test.py](../audit/java_bracket_balance_test.py) | 0.3.277-f2: баланс {} () [] во всех java-файлах src/main/java. |
| [jei_display_test.py](../audit/jei_display_test.py) | Static contract checks for JEI categories, GUI geometry, recipes, and GOST tooltips.""" |
| [lead_chest_test.py](../audit/lead_chest_test.py) | Пины раунда 11 (0.3.111): свинцовый ящик + 14-связный контур. |
| [liquid_fire_radiation_test.py](../audit/liquid_fire_radiation_test.py) | Static regression contract for 0.3.159 liquid-fire effects and corium radiation.""" |
| [litho_room_link_test.py](../audit/litho_room_link_test.py) | Litho machine <-> room ledger link: tooltip, ledger and reject roll agree. |
| [machine_gui_darkening_test.py](../audit/machine_gui_darkening_test.py) | Use vanilla world darkening and draw only the machine GUI sandwich above it.""" |
| [machine_gui_theme_test.py](../audit/machine_gui_theme_test.py) | Static contracts for machine GUI paths, theme selection, and per-machine NEO art.""" |
| [mash_timer_test.py](../audit/mash_timer_test.py) | Static contract: fermented alcoholic drinks reset the Telifon no-mash timer.""" |
| [micro_patch_148_test.py](../audit/micro_patch_148_test.py) | Regression checks for the 0.3.148 filler, capacity, tooltip and litho fixes.""" |
| [multiblock_node_rules_test.py](../audit/multiblock_node_rules_test.py) | Static contracts: правила узлов мультиблоков (0.3.255, автор). |
| [music_debug_command_test.py](../audit/music_debug_command_test.py) | Static contracts for the client-authoritative music debug roll.""" |
| [naming_scan.py](../audit/naming_scan.py) | Сверка правил именования Gonzo Tech (автор, 21.09.2026). |
| [noble_moss_test.py](../audit/noble_moss_test.py) | Static regression checks for the 0.3.209 noble-moss block family.""" |
| [node_cap_test.py](../audit/node_cap_test.py) | 0.3.71: узлы дороже не делать обшивкой — лимиты узлов на структуру (авторские числа). |
| [node_clump_sim.py](../audit/node_clump_sim.py) | Текущая модель: НЕ зависит от поршней (кэша нет, каждый тик полный BFS).""" |
| [node_clump_test.py](../audit/node_clump_test.py) | Вспомогательный скрипт / source check |
| [node_perf_rad_test.py](../audit/node_perf_rad_test.py) | Вспомогательный скрипт / source check |
| [notes_favorites_test.py](../audit/notes_favorites_test.py) | 0.3.308 source/resource contracts, NOT a Java or in-game execution claim. |
| [notes_model_test.py](../audit/notes_model_test.py) | Notes model compile-gate (0.3.78): the whole chalkboard/notes package is |
| [nuclear_firebox_fuel_test.py](../audit/nuclear_firebox_fuel_test.py) | Nuclear firebox fuel-duration and per-item GTH-yield contract (0.3.143).""" |
| [onboarding_content_test.py](../audit/onboarding_content_test.py) | Пин онбординга первого запуска (инструмент агента, 11.10.2026). |
| [pcfsoz_test.py](../audit/pcfsoz_test.py) | PCFSOZ centrifuge (0.3.89, author 04.10.2026): clone of Tsf1UR with the |
| [pet_bowl_healing_test.py](../audit/pet_bowl_healing_test.py) | A successful pet-bowl bite heals cats and wolves by four health points.""" |
| [pipe_loss_test.py](../audit/pipe_loss_test.py) | Per-block GTU/GTH losses (0.3.58, author's numbers): pure math + wiring pins. |
| [pipe_routing_diagnostics_test.py](../audit/pipe_routing_diagnostics_test.py) | Opt-in, behavior-neutral diagnostics for pipe routing and node-clump rebuilds.""" |
| [pipe_routing_perf_test.py](../audit/pipe_routing_perf_test.py) | PipeRouting perf fix (0.3.90, author report: absorber field killed TPS, |
| [pipe_routing_tail_test.py](../audit/pipe_routing_tail_test.py) | Regression contract for the capacity-induced zero-step pipe-routing tail. |
| [qol_batch_test.py](../audit/qol_batch_test.py) | Static regression checks for the QOL batch and the 0.3.212 dead-slime fog rework.""" |
| [rad_materials_audit.py](../audit/rad_materials_audit.py) | Аудит экранирования (раунд 14, автор 03.10.2026). |
| [radiation_behavior_test.py](../audit/radiation_behavior_test.py) | Текущая radiation wiring/storage/flows и неизменённые лекарственные контракты; без held-state. |
| [radiation_contour_test.py](../audit/radiation_contour_test.py) | Compile/run the actual pure-Java radiation-contour core, without Gradle/Minecraft. |
| [radiation_engine_test.py](../audit/radiation_engine_test.py) | Реальное Java-ядро, извлечённые flow/claim/children; 11 групп + `--mutation-check`, не Minecraft. |
| [raw_ore_blocks_test.py](../audit/raw_ore_blocks_test.py) | Regression checks for all 26 compressed raw-ore blocks and processing recipes.""" |
| [reactor_fuel_storage_test.py](../audit/reactor_fuel_storage_test.py) | Regression contracts for per-sheath fuel ownership and lossless break rounding.""" |
| [reactor_gui_test.py](../audit/reactor_gui_test.py) | Static regression checks for the reactor panel GUI follow-up.""" |
| [reactor_matrix_integration_test.py](../audit/reactor_matrix_integration_test.py) | Contracts for cached ReaCalc 1.2 operating metrics and per-sheath TVEL ownership.""" |
| [reactor_matrix_test.py](../audit/reactor_matrix_test.py) | Compile the matrix, loaded metrics, ReaCalc 1.7 dynamics, and Java oracles.""" |
| [reactor_radiation_trace_test.py](../audit/reactor_radiation_trace_test.py) | Static contracts for the first reactor components and the five-second dose trace.""" |
| [reactor_runtime_test.py](../audit/reactor_runtime_test.py) | Static contracts for the ReaCalc 1.7 runtime, pipe I/O, and live reactor GUI.""" |
| [reactor_structure_assets_test.py](../audit/reactor_structure_assets_test.py) | Static contracts for reactor forming, shielded port rules, and owned textures.""" |
| [reactor_structure_lifecycle_test.py](../audit/reactor_structure_lifecycle_test.py) | Regression contracts for the reactor's saved-bounds/event lifecycle.""" |
| [resource_texture_coverage_test.py](../audit/resource_texture_coverage_test.py) | Resolve every mod JSON model path and verify its inherited texture references.""" |
| [run_all.py](../audit/run_all.py) | Единый гейт аудит-сьютов (инструмент агента, 08.10.2026). |
| [scale_format_test.py](../audit/scale_format_test.py) | Форматы тултипов шкал (раунд 14, автор 03.10.2026). |
| [second_lumber_test.py](../audit/second_lumber_test.py) | Static regression checks for the tier-two wool/grinder and lumber mill feature.""" |
| [second_pass_test.py](../audit/second_pass_test.py) | Execute production bodies with small API stubs; NOT Minecraft or a full mod build. |
| [sedative_worldgen_test.py](../audit/sedative_worldgen_test.py) | Static regression checks for worldgen and sedative behavior.""" |
| [sketch_page_video_test.py](../audit/sketch_page_video_test.py) | Static contract for the Scholar Notes video/audio playback and overlay ordering.""" |
| [space_sky_rotation_test.py](../audit/space_sky_rotation_test.py) | Static regression contract for sky-star rotation and the Overworld path.""" |
| [spent_reprocessing_test.py](../audit/spent_reprocessing_test.py) | Static contracts: отработка ТВЭЛов и Измельчитель II (0.3.253).""" |
| [steam_power_tooltips_test.py](../audit/steam_power_tooltips_test.py) | SteamGen balance curve, exchanger efficiency, and Steam/GTH tooltip contract. |
| [steamgen_endpoint_test.py](../audit/steamgen_endpoint_test.py) | Execute the actual 0.3.307 virtual-endpoint methods against tiny world stubs. |
| [sun_event_snow_test.py](../audit/sun_event_snow_test.py) | Regression pins for vanilla and custom snowfall support checks.""" |
| [surface_worldgen_test.py](../audit/surface_worldgen_test.py) | Static contract checks for the all-biome surface ruin and waste generators.""" |
| [third_epoch_test.py](../audit/third_epoch_test.py) | Пины раунда 10 (0.3.110): экранированная семья эпохи 3 + свинцовые поршни. |
| [tongs_ladle_test.py](../audit/tongs_ladle_test.py) | Tongs & ladle (0.3.79, EPOCH3-BASE 2.8): pure storage logic compiled and |
| [third_storage_test.py](../audit/third_storage_test.py) | ENG-001: real Java budget/loss/sync core, mutation mode, static loot/GUI/JEI wiring; not MC build. |
| [typecheck_test.py](../audit/typecheck_test.py) | Вспомогательный скрипт / source check |
| [validate.py](../audit/validate.py) | Статическая валидация ресурсов Gonzo Tech (без Java/Minecraft). |
| [vanilla_polish_test.py](../audit/vanilla_polish_test.py) | Static contracts: «Улучшение ванили» и компоненты 0.3.254 (автор).""" |
| [docs_structure_test.py](../audit/docs_structure_test.py) | Введён при docs migration 0.3.311; integrity, task lifecycle, source/section inventory, профильные таблицы |

## История результатов

| Срез | Результат | Уровень/ограничения |
|---|---|---|
| 0.3.307 | 54 PASS / 19 SKIP / 0 FAIL из 73 | Исторический source/harness-прогон, не blanket-вердикт по 42 пунктам |
| 0.3.308–310 | 55 PASS / 19 SKIP / 0 FAIL из 74 | Заметки/creative/шаблоны; сборка/игра отдельно |
| c15083f / 42730c3, 0.3.311 | 53 PASS / 19 SKIP / 2 FAIL из 74 | autominer_markers_test:31 ожидает 310; radiation_behavior_test:47 — старый held-state; ниже остаются RadDoseIndex-пины |
| Авторская 0.3.311 | build + 11 GameTests успешны по отчёту автора | Здесь без JDK независимо не повторены; ручной клиентский тест не заменяют |
| DOC-001, 0.3.311, 27.10.2026 | **54 PASS / 19 SKIP / 2 FAIL из 75** | Новый docs_structure_test PASS; те же два исходных FAIL, runtime не изменён |
| DOC-001: сохранность/навигация | 75/75 SHA-256 и Git 42730c3; 4173 пункта; 69 исходов; 0 битых ссылок | Структурные проверки не доказывают автоматически всю семантику |
| DOC-001: ресурсы/census | validate: 0 ошибок / 0 предупреждений; content_inventory + --check PASS | 606 item definitions, 792 recipe files; CSV в audit/reports, не в docs |

### DOC-002: проверка расширенной структуры (27.10.2026, 0.3.311)

- docs_structure_test: PASS — **29** живых документов, инвентарь 51 страницы в SCHOLAR_NOTES, технические владельцы CHALKBOARD/MULTIBLOCK, все Sinks и BE с буферами/receive* отражены в MACHINES; прежние 75 оригиналов и 4173 source-пункта сохранены. Наличие названия метода в документе не доказывает полной семантической корректности.
- doc_links: **0** битых ссылок; validate: **0 ошибок / 0 предупреждений**; git diff --check: чисто.
- run_all: **54 PASS / 19 SKIP / 2 FAIL из 75**. Те же autominer_markers_test и radiation_behavior_test, assertions не менялись. Новых code/asset/dependency изменений нет; игровой/Gradle-прогон не выполнялся.
- Пределы радиационных тестов и D-AUD1 остаются; этот документальный проход их не «озеленяет».

## Исторические fixtures и текущие контракты

Существующие source suites читают тексты прежних docs; пути явно перенесены в `docs/archive/legacy-0.3.311/`, утверждения не удалены. Это сохраняет прежнюю проверку исходного свидетельства, но не выдаёт архив за текущую спецификацию. Новая структура проверяется отдельным docs_structure_test. Для новых регресс-пинов использовать актуальный тематический файл и код, не архивный ответ.

Известные 2 FAIL сохраняются как FAIL, задача D-AUD1 в [DEFFERED](DEFFERED.md). Нельзя менять sentinel или удалять старые assertions в рамках документального переноса только ради зелёного отчёта. Остальные baseline-результаты историчны и не гарантируют нынешний игровой исход.

## DOC-003: 0.3.312, 27.10.2026

Новые инструменты: [psyche_contracts_test.py](../audit/psyche_contracts_test.py) — P04: source-пин 0,2%/с и извлечённое арифметическое выражение с дробным остатком/низкими значениями; P14: source-пин правильного отсчёта и все 256 последовательностей восьми интервалов 1/2 тика. Дополнительно шкальные границы/вероятности, P13=220, материалы/реестр debug проверены против PSYCHE. **Это Python/source-модель, не запуск Java/NeoForge.** [docs_snapshots.py](../audit/docs_snapshots.py) — hash/bytes/source_commit зарегистрированных OUTDATED, интегрирован в docs_structure и doc_links; README/metadata архива не исключены из ссылочных проверок. Авторский файл-указатель разрешён явно, не удалён.

- `python3 audit/run_all.py`: **55 PASS / 19 SKIP / 2 FAIL из 76**. Новая psyche-регрессия PASS. Прежние FAIL: autominer_markers_test.py:31 жёстко ждёт 0.3.310; radiation_behavior_test.py:47 ждёт старую запись `ItemRadioactivity.setInduced(stack, state.value);`. Assertions не ослаблялись; D-AUD1 остаётся открытым.
- `python3 audit/docs_structure_test.py`: PASS, 29 живых профилей + авторский указатель; **75** legacy-оригиналов побайтно; **4173** исходных пункта / **69** решений; дополнительно **3** полные редакции перед DOC-003, SHA-256/размер/сравнение с Git 6cf8daa.
- `python3 audit/doc_links.py`: 0 битых ссылок; зарегистрированные исторические тела не переписываются, новые навигационные ссылки проверяются.
- `python3 audit/validate.py`: **3275 JSON**, 606 item definitions, en=ru=1279; **0 ошибок / 0 предупреждений**. `git diff --check`: чисто.
- Нет java/javac; Gradle/11 Minecraft GameTests и авторский игровой тест chemical/tremor **не выполнялись**. 19 SKIP из-за JDK/Pillow — не успешные прогоны. Исправление интервала клиентского тремора не означает проверку FPS/ощущения в реальном клиенте.
- Изменения Java ровно два: PsycheChemical (согласованный спад) и PsycheTremorClient (согласованный счётчик); radiation/, PlotnickBlock, ресурсы/ассеты/build-настройки не изменены. Версия в gradle.properties 312. Полная хроника/ответы P01–P14 — HISTORY; прежние редакции — [архив](archive/editions/0.3.311/README.md).

## RAD-001: 0.3.313, 27.10.2026

База 863ef748, только радиационная заявка. Проверки идут на установленном JDK 21.
Новые GameTests: perItemSourceSelection (одиночная стопка; 1/16/64 источников; одинаковые уран/плутоний/радий с разделениями 1/2/31/32/33/63 по 100 шагов), sourceSnapshotTiesAndOrder, nestedSourceStrength, radiationPanelPixelLayout. Первый прогон 14/15: фикстура порядка слотов использовала уже обработанный t=100; следующий расчёт законно пропускался. Фикстура исправлена на t=120, runtime обхода пауз не добавлялся.

Пиксельная сверка: все светлые/тёмные пиксели кромки и четыре срезанных угла совпали с авторским 1×-образцом 160×19. Проверялся рендер прямоугольников из RadiationPanel, без клиентского игрового прогона; предпросмотр содержит авторские глифы только для проверки отступов. Логи и снимок базы: `C:/Users/User/Documents/Codex/2026-10-20/new-chat/work/rad-fix-313`.

Итог 0.3.313:

- `gradle build runRadiationTest --offline --no-configuration-cache` — **BUILD SUCCESSFUL, 15/15 обязательных GameTests**, 27.10.2026. `gradle build --offline` — **BUILD SUCCESSFUL**, configuration cache сохранён. JAR: `build/libs/gonzotech-0.3.313.jar`.
- `git diff --check` — PASS. SHA-256: **5 326** ранее отслеживаемых файлов вне задачи неизменны; изменения только в перечисленных радиационных классах, версии и документах. Все исходные ресурсы и остальные Java-классы (включая новые PsycheChemical/PsycheTremorClient 0.3.312) сохранены.
- Неизменённые `docs_structure_test.py` / `doc_links.py` в Windows падают на старом `OUTDATED_DOC-003_PSYCHE.md`: checkout CRLF не совпадает с сохранёнными LF-хешами. Неизменённый `validate.py` выдаёт 202 ложных отсутствия вложенных моделей из-за сравнения `os.path.relpath` с `\` и ResourceLocation с `/`.
- Отдельный проверочный адаптер **в рабочей папке Codex, не в проекте** нормализует CRLF только если полученный SHA-256 точно совпадает с прежним manifest; для ресурсного скрипта приводит только разделитель относительного пути к `/`. С этим явно обозначенным условием: docs_structure PASS (7 редакций, 75 legacy-оригиналов, 4173 пункта), doc_links — **1183 ссылки, 0 битых**; validate — **3275 JSON, 0 ошибок / 0 предупреждений**. Исходные FAIL-логи и PASS-логи адаптера сохранены отдельно; старые assertions, архивы и ресурсы не исправлялись ради зелёного результата.
- Существующая ошибка загрузки рецепта third_grinder (неиспользованный ключ G) в Minecraft-логе осталась; этот ресурс не входит в радиационный патч. Статический validate не проверяет весь семантический контракт ванильного рецепта.
- Реальный клиент/ЛКМ/ПКМ/Q, `/tick rate 200` и авторская приёмка панели не выполнялись. Серверные тесты и пиксельная сверка — успешны, игровой баг до авторской проверки не объявлен закрытым.
 Исторические source-пины старого NBT/held-state не ослаблялись; D-AUD1 остаётся отдельной задачей.


## D-RAD2: 0.3.314, 27.10.2026

- `gradle build runRadiationTest --offline --no-configuration-cache`: **BUILD SUCCESSFUL**. Первый прогон 21/21, после проверки двойного сундука и уточнения его критериев соединения финальный **22/22 обязательных GameTests** (3,943 с исполнения тестов). Не считать временем обработки обычного игрового тика или полноценным серверным benchmark.
- Новые проверки: одинаковые 1/30 источников, count 1/64, порядок слотов, равные природные пресеты без наведёнки; активные шaлкер/bundle без записи item data; изоляция владельцев; свинцовая передача ×0,2 и независимый вход чанка; 5000 шагов замкнутой обратной связи при 1/41 слотах ×1/64 предметах; неизвестная панель скрыта; закрытый сундук/armor stand реально обрабатываются расписанием; двойной сундук с наведением между половинами и сохранением внешней дозы. Прежние 15 регрессий также прошли.
- Повторный пользовательский `gradle build --offline`: **BUILD SUCCESSFUL**, configuration cache сохранён. JAR `build/libs/gonzotech-0.3.314.jar`, version в META-INF проверена, новый RadiationCluster.class присутствует. SHA-256: `343cb3b3e419dd6649fb488dca0f35d7a5c93bb561cd5f156f921ba3b59dc083`.
- `git diff --check`: PASS. По SHA-256 снимка незакоммиченной 0.3.313 сохранены **5 324** исходных файлов вне изменяемого списка; нет удалённых или неожиданных изменённых файлов. В том числе сохранены авторские ресурсы, Плотник, прочие Java-классы и весь замороженный архив. Резервный ZIP и логи: `C:/Users/User/Documents/Codex/2026-10-20/new-chat/work/rad-clusters-314`.
- Прежний отдельный Windows-адаптер из rad-fix-313: docs_structure PASS (7 сохранённых редакций, 75 исходных файлов, 4173 пункта/69 решений); doc_links **1182 ссылки, 0 битых**; validate **3275 JSON, 0 ошибок / 0 предупреждений**. Условие адаптера прежнее: CRLF нормализуется только до уже записанного manifest SHA, относительный путь модели — до `/`. Это не утверждение, что исходные Windows-скрипты без адаптера исправлены. Сами audit-скрипты/архивы не менялись; общий исторический source-аудит не перезапускался и не объявлен целиком зелёным.
- В Minecraft-логе остаётся прежний ERROR рецепта `third_grinder` — неиспользованный ключ `G`; радиационные тесты проходят, рецепт не входит в данный патч.
- Интерактивный клиент, ощущения панели/ЛКМ/ПКМ/Q и приёмка автора не выполнялись. D-RAD2/RAD-001 остаются «реализовано, ждёт проверки», игровые баги автоматически закрытыми не объявляются. Для стороннего IItemHandler-only хранилища нужен адаптер владельца; общая математика этого не заменяет.


### Окончательная поставка 0.3.314 после исправления копий

Повторный build + **23/23 обязательных GameTests PASS**, 27.10.2026; independentNestedCopies подтверждает раздельные вложенные экземпляры/дозы при одинаковых компонентах и сериализации, точные индексы пустых слотов, выбранный элемент bundle и полную сумму эмиссии. Ранее перечисленные 21/22-тестовые прогоны — промежуточные этапы этой версии. Обычный `gradle build --offline` также успешен.

Финальный JAR: `build/libs/gonzotech-0.3.314.jar`; SHA-256 `3700ec71e232e49a5e35f810836b715b1e952259c1a67ade2674ab61f86deacb`. Сохранены **5 322** файла исходного снимка вне задачи; дополнительно к радиационным классам изменены hook ItemStack.copy и регистрация конструктора immutable-контейнера в mixins.json. Неожиданных изменений/удалений нет. Формат динамической дозы остаётся внешним, исходные PNG/OGG/модели и прочий авторский Java-код сохранены. Глубокое отделение выполняется только при копировании контейнерного предмета до глубины 4, с затратой O(S) по вложениям; нагрузочная приёмка реального сервера отдельно от регрессий.

Лог окончательного прогона: `C:/Users/User/Documents/Codex/2026-10-20/new-chat/work/rad-clusters-314/test-copy-isolation.log`. Все ранее обозначенные границы (ручной клиентский тест, адаптеры сторонних хранилищ, старый ERROR third_grinder и Windows-условия валидаторов) сохраняются.

## Ознакомление с авторской 0.3.315 и CRASH-001 (27.10.2026)

База `5ce51b8`, сравнение с доставленным `863ef74`: 36 файлов, 1875 добавлений/435 удалений. Изучены docs и текущие RadiationCluster/RadiationCopies/ItemRadioactivity/расписание владельцев/панель/приоритет труб, crash-миксин и источники LEDGER. Это обзор/разбор присланных фрагментов, **не игровой воспроизводящий запуск** и не новый балансный патч. Отчёт автора о 23/23 GameTests 0.3.314 сохранён выше; здесь независимо не повторялся.

- Локально `python3 audit/run_all.py`: **52 PASS / 19 SKIP / 5 FAIL из 76**, exit 1. Два прежних: autominer_markers_test.py:31 (exact version 310), radiation_behavior_test.py:47 (старый held-state write). Ещё три текущих source-FAIL после переписывания RadiationSystem: lead_chest_test.py:87 ищет `effectiveEmission, CHEST_TO_CHUNK_RATE`; node_perf_rad_test.py:48 — старый call `scanContainersInto(level, chunkKey, data, nowTick)`; reactor_radiation_trace_test.py:133 — старые текстовые ключи `inventoryContribution=`/`strongestOtherSource=`. Текущий код имеет cluster-target transfer, world-tick scheduler и trace `strongestOther/item=`; assertions не ослаблялись. Эти FAIL не являются доказательством причины закрытия клиента и не объявлены исправленными. Полная актуализация source-контрактов остаётся отдельным D-AUD1, не активированным данным обзором.
- `python3 audit/docs_structure_test.py`: PASS — 29 профилей + указатель, 75 неизменных legacy, 4173 source-пункта/69 решений, **7** зарегистрированных предыдущих редакций, SHA/байты/Git. В этом Linux checkout адаптер EOL/путей не нужен.
- `python3 audit/doc_links.py`: 0 битых ссылок. `python3 audit/validate.py`: 3275 JSON, 606 items, en=ru1279, **0 ошибок/0 предупреждений**. `git diff --check` — PASS для текущих изменений.
- java/javac отсутствуют; Minecraft client/Gradle/23 радиационных и 2 новых pipe GameTests здесь не запускались. Подтверждение причины завершения 27.10 заблокировано отсутствием полного лога/exit code/актуального crash-report или hs_err. Новых runtime/source-test правок не делалось.

## D-AUD1 — code-first ремонт пинов и полный Java-прогон 0.3.315

27.10.2026, база `6497c9a` + авторская 0.3.315. Задача активирована автором после предыдущего обзора и закрыта в пределах audit-контрактов. **Runtime/src/build/игровые зависимости/ассеты не менялись**, повышения версии нет. Ни crash, ни игровой баг этим не объявлены исправленными.

### Что именно доказано

- Новый radiation_engine_test: **11 поведенческих групп / 450560 assertions PASS**, реальная Java-реализация 8 классов, дословные children/transfer/claim и world-transfer statements. Проверяется утверждённая 314/315 формула с 0.0013/mZt, не Python-зеркало старой 0.00097; count/order/self/subtree, lazy catchup, один active tick во вложении, x0.75, оба свинцовых направления и независимость входящего чанка, count-preserving copy/merge, изоляция деревьев, feedback 5000 шагов (1/41 × 1/64).
- `python3 audit/radiation_engine_test.py --mutation-check`: **11/11 KILLED**, в временных копиях без правок src. Это проверки чувствительности, не «11 найденных багов»: старый uptake, потеря MILLI, отсутствие вычитания intrinsic/x0.75, deep+single tick, свинец=1, включение чанка при =30 mZt, self-subtree в источниках, сумма induced вместо max, общий nested stack копий, сумма per-item rates вместо взвешенного merge.
- `_radiation_contracts` — только source-wiring: физические inventory slots, world-tick phases/getChunkNow/unload, double-chest canonical owner, update BE при изменении, cap суммарного переноса, новая trace-схема, отсутствие возвращённого RadDoseIndex/re-equip/clamp. Click/creative/thread-guard/LEDGER, рецепты/ассеты и нереляционные autominer/network требования сохранены.
- Typecheck: **52 реальных файла + 131 compile-only стаб, 0 ошибок**. Не исключён ни один прежний real-file; добавлен реальный ProcessingRecipeDisplay. Восстановлены поверхности новых mod-owned зависимостей, inherited menu и используемых MC типов. Стабы — проверка внутренней согласованности, не независимое доказательство Mojmap/API.
- Reactor matrix/operating/thermal oracle: **7358 assertions PASS**. Исправлен setup reload-проверки: production восстанавливает outputCurve из live matrix, а transient slopes линейной fixture не входят в Saved. Сохранены требования горячего охлаждения/положительного остаточного выхода; добавлен повторный restore. Runtime и режим formed-port не менялись.
- GUI: **914 static + actual PNG checks PASS**. Кризисный blit уже с 285 использует явный source-size overload/vertex alpha; пин исправлен на фактические 13 аргументов с прежними 23 кадрами 128×72, не на изменение PNG. Остальные ранее скрытые wiring/dependency FAIL разобраны в HISTORY.

### Промежуточные результаты не скрыты

Baseline без toolchain: **52 PASS / 19 SKIP / 5 FAIL из 76**. После пяти ремонтов + нового harness с Java: **66 PASS / 5 SKIP / 6 FAIL из 77** (audit_followup, blast_furnace, pipe_loss, radiation_contour, tongs_ladle, typecheck). После этих ремонтов: **72 PASS / 5 SKIP / 0 FAIL**. После Pillow и ECJ-поддержки двух оставшихся wrappers: **75 PASS / 0 SKIP / 2 FAIL** (gui_alignment old blit pin, reactor_matrix linear reload fixture). После их разбора: **77 PASS / 0 SKIP / 0 FAIL**, exit 0. Список tests не фильтровался, assertions не отключались.

### Среда/повторение

Обычная среда: JDK 21 (java/javac через JAVA_HOME или PATH), Python + Pillow; `python3 audit/run_all.py`. Либо Java 21 runtime + ECJ: wrappers принимают `ECJ_JAR` (в том числе теперь reactor_matrix и steamgen_endpoint). Только tools в .cache; они не входят в Git и могут отсутствовать после восстановления workspace.

Фактически здесь: jdk4py **21.0.8.2**, Temurin **21.0.8+9-LTS**; ECJ **3.46.100**, извлечён из npm `exoproc-jjit@0.6.5` (SHA-256 jar `74c93172475fee0cd4e6eefb44233516dd40525642da35d009146d97ea598229`); Pillow **12.0.0**. Java runtime поставлен PyPI в `.cache/java-toolchain`, Pillow — `.cache/python-toolchain`, compiler — `.cache/package/deps`. Ни бинарники инструментов, ни сгенерированные классы не коммитятся.

```sh
export JAVA_HOME="$PWD/.cache/java-toolchain/jdk4py/java-runtime"
export ECJ_JAR="$PWD/.cache/package/deps/ecj-3.46.100.jar"
export PYTHONPATH="$PWD/.cache/python-toolchain"
python3 audit/run_all.py
python3 audit/radiation_engine_test.py --mutation-check
python3 audit/docs_structure_test.py
python3 audit/doc_links.py
python3 audit/validate.py
git diff --check
```

Minecraft/Gradle/23 авторских GameTests и реальный клиент здесь **не запускались**. Boundary doubles для ItemStack/preset/material/components/transactions не гарантируют реальное сохранение owner sidecar, packets, codecs, MC event/Mixin injection или сторонние IItemHandler. RAD-001/D-RAD2/UX-001/D-VAL1 остаются отдельной игровой приёмкой. Невоспроизводимое закрытие 27.10 по-прежнему неизвестной причины, отчёт 25.10 исключён автором.

Контроль документации этого шага: structure **9** previous editions/29 live/75 byte-identical legacy/4173 source items/69 dispositions; doc_links **1192**, 0 broken; validate **3275 JSON/606 items/en=ru1279, 0 errors/0 warnings**; diff-check чисто.

## ENG-001 / 0.3.316 — новые III-хранилища и JEI-preview

Среда та же, что D-AUD1: Java21 (.cache/java-toolchain/jdk4py/java-runtime), ECJ3.46.100 (.cache/package/deps), Pillow12 (.cache/python-toolchain); бинарники не входят в Git. Команда:

```bash
JAVA_HOME="$PWD/.cache/java-toolchain/jdk4py/java-runtime" \
ECJ_JAR="$PWD/.cache/package/deps/ecj-3.46.100.jar" \
PYTHONPATH=.cache/python-toolchain python3 audit/third_storage_test.py --mutation-check
# тот же env: python3 audit/run_all.py
```

- `ThirdStorageSelfTest` компилирует **настоящие ThirdStorageDefs + TickEnergyStorage**, без MC doubles. **1535705 assertions:** точные8констант, combined intake/output в одном tick, множество источников/приёмников, simulate и probe другого tick без сброса clock, capacity/empty/отрицательные/Long.MAX_VALUE, loss32раз/тик/без долга, battery stable10000ticks, energy conservation, 100000 случайных sync roundtrip + carry/full/signed-short boundaries, 2×5000×30 модельных операций.
- 68 static checks: registrations/gate/creative/no invented recipe, ordinary CarrierItem storage, actual path-loss debit, read-only simulate wiring, long NBT/CUSTOM_DATA, loot-context single drop/no manual drop path, placement, 512 PNG/literal slot interiors/gauge opening+backing, independent client sync limbs, six live Kind forms + cell/greatsword getters, JEI-only composition-free display.
- `--mutation-check`: **10/10** temporary code regressions rejected by AssertionError; compile failure is **not** counted as kill. Per-call input/output, mutation during either simulate, rounded charge, missing/double loss, wrong intake budget, discarded fraction and short overflow. Actual src is untouched by mutations.
- Aggregate first pass after implementation: **78 PASS / 0 SKIP / 0 FAIL**, `/tmp/gonzo-eng316-initial.log`; old77tests retained + third_storage_test. GUI theme expectation is now72 Classic512 sheets (new pair), not70; per-screen distinct NEO checks remain. Added both screens to III X.Y format suite.
- Upstream API research: JEI tagv20.0.0 has minecraftVersion1.21.4, display-aware vanilla factory/builder verified; real repository PetBowlBlock establishes MC getDrops/LootParams signature; NeoForge1.21.4 DeferredRegister confirms custom item properties overload/useBlockDescriptionPrefix. Cached sources are not dependencies or committed artifacts.

**Limits:** no full MC/NeoForge/Gradle build, client rendering, actual loot engine/codec/network integration, optional-JEI runtime or GameTests executed here. Existing typecheck covers its own selected sources/stubs, **not all new MC-facing classes**; this run is not claimed as their compilation. Real-core long restore is not proof that MC loot lifecycle supplies BE in every modded destruction path. New devices, actual survival break/place/quit/rejoin, creative loot behavior, slots/shift, mixed-tier/parallel network and all8 JEI pages + real same/mixed alloy crafting need author playtest. Placeholder visual quality is intentionally not final. No radiation/Plotnick runtime changed.

Финальный повтор после синхронизации всех профилей и cleanup: run_all **78 PASS / 0 SKIP / 0 FAIL** (`/tmp/gonzo-eng316-final.log`); docs_structure — 9 previous editions / 29 live / 75 immutable originals / 4173 source items / 69 dispositions; doc_links — **1199 / 0 broken**; validate — **3285 JSON / 608 items / en=ru1281 / 0 errors / 0 warnings**; git diff --check — чисто. Проверено отсутствие изменений radiation runtime, PlotnickBlock и прежних tracked PNG. Runtime0.3.316; build/dependencies без изменений.

## HUD-RAD-001 / 0.3.317 — авторский игровой репорт, новый direct dose contract

После вопроса о конфликте current+legacy40%-block-only автор явно выбрал5% полной физической эмиссии окружения/с до личной защиты, все источники; свой inventory unchanged, выход в чанк режется F. Это авторская норма, не «подгонка» прежних проверок. Новый world path не доказывает, в каком историческом commit возникло отличие от ожидания автора.

- Новый `inside_exposure_test.py`: **57 assertions**, actual Containment.insideDose/contentsEmission/containsSource + RadiationOwners.slots/children bodies и настоящие RadiationContour/RadiationCluster/ItemRadioactivity/ExternalRadiation/deep-copy/math классы. Границы мира/сущностей/lookup — doubles, не MC API proof. Full counts/induced vs strongest target, сундук, вложения, floor/frame, другие игроки, mob equipment, container entity+hand dedup, миска/две physical halves, boundary/frame vs beyond-wall, observer exclusion, NO ender chest, live mutations/no source aging/count change, F=0/F=.8, actual closed two-layer geometry/open corridor/unloaded observer.
- `--mutation-check`: **7/7 assertion kills**, compile failures не считаются kill: старый40% вес, chunkTarget вместо total, observer included, умножение на outgoing F, игнорирование room membership, потерянный chest path и remote ender inventory. Все мутанты только во временной директории.
- Новый `instrument_hud_test.py`: настоящий InstrumentHud callback проверяет одну registerAbove CHAT регистрацию/ID и вызов двух renderer без повторов; source guards подтверждают прежние positions/colors/F1/hands/speed и отсутствие Post/depth hacks. Это не GPU depth-buffer test. Полные SolarWatchHud/SpeedometerHud/InstrumentHud добавлены в существующий typecheck.
- `radiation_contour_test.py`: прежние160 geometry checks сохранены, только численные dose expectations пересчитаны с40% на утверждённые5%; отдельно9cache/lifecycle/order checks. geometry/cache/layer material policy не менялись.
- `typecheck_test.py`: **55 real / 148 boundary stubs / 0 errors**. Новые surfaces соответствуют используемым repository MC1.21.4 APIs/verified NeoForge source. RadiationSystem/DebugCommand call-site edits не выдаются за полную их MC-компиляцию; actual-body harness/typecheck и авторский build — разные доказательства.
- Первый aggregate **80 PASS / 0 SKIP / 0 FAIL** (`/tmp/gonzo317-first.log`), прежние78 сохранены +2новых. Java21/ECJ3.46.100/Pillow12 восстановлены в исключённой .cache (не Git). В этом sandbox JAVA_HOME теперь `.cache/python-toolchain/jdk4py/java-runtime`, ECJ_JAR `.cache/package/deps/ecj-3.46.100.jar`, PYTHONPATH `.cache/python-toolchain`.

```bash
JAVA_HOME="$PWD/.cache/python-toolchain/jdk4py/java-runtime" \
ECJ_JAR="$PWD/.cache/package/deps/ecj-3.46.100.jar" \
PYTHONPATH=.cache/python-toolchain python3 audit/inside_exposure_test.py --mutation-check
# тот же env: python3 audit/instrument_hud_test.py; python3 audit/run_all.py
```

Полный Minecraft/NeoForge Gradle build, GameTests, GPU/chat fade/open-chat и повтор автора **не выполнялись**. Нужны survival/direct-dose проверки всех owner categories внутри/снаружи/с открытой дверью, без двойного inventory, с личной защитой и сохранением внешнего F. Патч реализован, не объявлен игровым закрытием. Нет изменения source aging/transfer formulas/PNG/PlotnickBlock/migration. GUI source upstream: NeoForge1.21.4 `GuiLayerManager.java`, `RegisterGuiLayersEvent.java`, `VanillaGuiLayers.java` (GitHub).

Финальная сверка диагностического UI317: прежние RU/EN labels «Доза от блоков внутри» / «Internal block dose» заменены на общий direct-dose inside-enclosure текст: значение включает и физическое содержимое, не только блоки. Добавлены source checks одинакового observer-excluding call в tick/getdose и актуальности обеих локализаций. Новых ключей/единиц/параметров не добавлено.

Финальный повтор после всех docs и RU/EN diagnostic labels: **80 PASS / 0 SKIP / 0 FAIL** (`/tmp/gonzo317-final.log`), 55real/148boundary typecheck PASS; 9 previous-edition hashes /29live /75immutable /4173source-items /69dispositions PASS; doc_links **1200/0broken**; validate **3285JSON/608items/en=ru1281/0errors/0warnings**; diff-check clean. Предыдущие radiation_engine/inside_exposure suites не исключены. README/PIPE/PNG/PlotnickBlock, engine/ItemRadioactivity и build dependencies неизменны; изменения radiation ограничены новым room exposure/diagnostic caller и разрешённым весом5%.

## Уточнение HUD-RAD-001 / 0.3.318 — входящий фон свинцового ящика

- База e420651 / 0.3.317. Прежние kernel, native GameTest и документы явно требовали неослабленного свинцом входа. Автор заменил это правило: ×0,2 применяется и к входу; на отдельный вопрос выбрал порог **после** умножения. Исторические утверждения выше относятся к прежним версиям, не к текущему контракту.
- Production-изменение только в RadiationCluster.step: incomingChunk=chunkEmission×policy.internalTransfer, затем строгий >CHUNK_THRESHOLD. NORMAL=1/LEAD=0,2 сохранены; нет F, второго множителя вложения или изменения исходящих путей/ItemRadioactivity/прямых5%.
- radiation_engine_test.py --mutation-check: **11 поведенческих групп / 450609 assertions PASS; 12/12 mutants assertion-killed**. Проверяются обычный/свинцовый вход на 30, 30+epsilon, 100, 149, 150, 150+epsilon, 151, 500 mZt/s; trace, неизменность count, вложенное однократное ослабление, вычитание intrinsic после экрана. Добавлен мутант отсутствующего входящего свинцового коэффициента; строгий порог по-прежнему mutation-проверяется.
- Первый полный aggregate: **80 PASS / 0 SKIP / 0 FAIL**, `/tmp/gonzo318-first.log`; прежние exposure/HUD/contour/outgoing/storage checks сохранены. Среда Java21/ECJ/Pillow та же, что для317, boundary typecheck55real/148stubs.
- Native clusterLeadAndIncomingChunk обновлён для 40/100/150/150+epsilon/500 mZt/s; **Minecraft/Gradle/GameTests/игра не запускались**. Headless harness не объявляется native integration proof. Авторская игровая приёмка остаётся открытой.

Финальный повтор318 после синхронизации профилей: **80 PASS / 0 SKIP / 0 FAIL**, `/tmp/gonzo318-final.log`; повтор mutation-check12/12 assertion-killed, `/tmp/gonzo318-mutations.log`. Docs:9previous-edition hashes/29live/75immutable/4173source-items/69dispositions; links1200/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; diff-check clean. Изменены только11файлов согласованного исправления/тестов/версии/документов. Доставка commit/push в фиксированную рабочую ветку разрешена автором; HUD-RAD-001 остаётся ждать игры.

## BUILD-001 / 0.3.319 — фактический compileJava FAIL автора

Автор прислал лог сборки318: два `package ModMachines does not exist` в TierThreeCrafting.isGatedOutput (строка184, оба новых III item). Причина — обращение к короткому имени без импорта; сам реестр существует в machines.registry, поля зарегистрированы. Это пропуск agent-патча316, не радиационная ошибка318. Прежний third_storage source-пин подтверждал наличие условий, а typecheck не включал TierThreeCrafting: 80PASS не означали успешную Minecraft-компиляцию.

Исправление production — одна строка импорта; версия319. typecheck_test теперь компилирует **весь TierThreeCrafting**, вместе с прежними55классами: **56real/154boundary stubs/0errors**. Дополнены только используемые registry/progress/event/recipe/player/menu surfaces по вызовам, существовавшим в репозитории и принятой компилятором части авторской сборки. Registry refs здесь item-границы, не реальная регистрация; Phase3Events/MC/NeoForge остаются doubles. Гейт прогрессии, callback-поведение и recipe-book в игре этой компиляцией не проверены.

При каждом typecheck временная копия TierThreeCrafting **без импорта ModMachines** компилируется с тем же набором исходников, без baseline классов в classpath. Компилятор обязан отказать с упоминанием ModMachines; иначе тест падает. Проверено **1/1 rejection**. Рабочее дерево не мутируется; временные классы/копии удаляются. Первый полный aggregate: **80 PASS / 0 SKIP / 0 FAIL**, `/tmp/gonzo319-first.log`.

Полный Gradle/NeoForge/Minecraft build здесь **не выполнялся**. Закрытие BUILD-001 ждёт повторной авторской сборки; предупреждения deprecated/unchecked из лога не объявляются ошибками и попутно не правятся. Runtime радиации/энергии/рецепты/PNG/Plotnick не менялись.

Финальный повтор319 после обновления docs: **80 PASS / 0 SKIP / 0 FAIL**, `/tmp/gonzo319-final.log`. Typecheck56real/154stubs, негативная компиляция без импорта1/1 rejected; docs9editions/29live/75immutable/4173source-items/69dispositions, links1200/0broken, validate3285JSON/608items/en=ru1281/0errors/0warnings, diff-check clean. Native Gradle build не выполнялся; готово к commit/push по разрешению автора.

## TEX-001 / 0.3.320 — модели после авторского731ff19

База remote731ff19 / runtime319. Из-за восстановления sandbox локальный HEAD оказался bf5e72e, но полное дерево рабочего каталога, собранное отдельным временным индексом, **побайтно совпало** с доставленным61c9324 (tree cfeab77aac32bcbf6cb14d9569d329e2afc861cc). Только после доказанного равенства восстановлены branch/index на61c9324 и выполнен fast-forward к авторскому731ff19; чужие изменения не присваивались/не стирались. Авторский коммит:13перемещённыхPNG и6обновлённыхPNG, без изменения models.

До исправления audit/validate.py: **18errors/0warnings**, все — старые second/texture slots в7JSON. Исправлены только ссылки second→third; аккумулятор/батарейка уже корректны. После исправления: **3285JSON/608items/en=ru1281/0errors/0warnings**. PNG/Java/blockstates/items/родители/UV/геометрия не менялись; версия320.

Новый model_texture_paths_test.py входит в run_all:9 реальных item/blockstate/model цепочек с точными parents/face slots/facing rotations,1098локальных texture slots по всем моделям; проверяются существование PNG и lower-case paths, без хэш-пинов против будущей авторской перекраски. --mutation-check:7возвратов старого пути (по модели), валидный но неверный battery north-face PNG и сломанная inventory-ссылка — **9/9 assertion-rejected**, данные меняются только во временной in-memory копии.

Первые два aggregate: **80 PASS / 0 SKIP / 1 FAIL из81** (`/tmp/gonzo320-first.log`, `/tmp/gonzo320-second.log`), оба autominer_markers_test: старый жёсткий textures/block/second/. Три таких чтения в тесте перенесены в third/ по авторским реальным файлам; проверки16×16/GUI/логики и весь остальной набор не удалялись. Отдельный autominer_markers_test после исправления PASS. Java21/Pillow12/ECJ3.46.100 восстановлены в ignored.cache; native Gradle/Minecraft/client/GameTests/GPU/resource reload не выполнялись. Исторические audit FAIL сохранены, не объявлены PASS.

Финальный прогон после всех ресурсных исправлений и профильных документов: **81 PASS / 0 SKIP / 0 FAIL из81**, `/tmp/gonzo320-final.log`;9/9ресурсных мутаций отвергнуты. Docs9editions/29live/75immutable/4173source-items/69dispositions PASS; links1201/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; git diff --check чистый. Сравнение с авторским731ff19: PNG, Java, blockstates/items неизменны. Коммит/пуш в фиксированную ветку разрешены; клиент/Gradle/визуальная приёмка не выполнялись.

## PERF-001 — постановка и начальная валидация, 28.10.2026

Автор подтвердил восемь реализованных задач и отдельно установил внешнюю причину CRASH-001: фоновый процесс драйвера по системному логу. Все9записей закрыты/немедленно архивированы по прямому указанию автора, без нового агентского native build/playtest. Архив CURRENT изa4aae45 зарегистрирован SHA-256/bytes/Git source; предыдущие81PASS остаются историческим прогоном320, не тестом новых14исправлений.

Все14пунктов зарегистрированы в PERF-001. Source подтверждает1(wrapper==),2(menu1 vs container max),3(sentinel2 overwritten),4(OR TTL),6(no payload rate gate/repeated Fence begin),7/8(I→II gap/per-call intake),10(remove(0)),11(insert/dump in setter),12(chunk cache remove/global + stack copies),13(repeated full path),14(strong values/no local unload cleanup). Для5 обнаружены getBlockState/Hopper lookup без local loaded guard: объём загрузок требует отдельной инструментированной пробы, он не измерен. Авторские server-пробы1/2/3 не повторялись здесь; пользовательское воспроизведение дюпа11 отсутствует. TPS/heap не измерены.

Официальный source/API research9 через gh api: EngineHub/WorldGuard version/7.0.x RegionContainer.java115 get(World), RegionManager.java303 getApplicableRegions(BlockVector3); EngineHub/WorldEdit master BukkitAdapter.java124 adapt(org.bukkit.World). Полученные исходники в ignored.cache/research, не в Git. Конкретный WG bridge/сервер не запускался. Неизвестны endpoint-load tickets, bridge mapping/failure policy и budgets кнопок; вопросы вынесены до runtime. Ни игровой код, ни версия/баланс/ресурсы/пины старых тестов этим шагом не менялись.

Документальная проверка шага записывается после исполнения. Полные cycle2/3 regression/mutation/native проверки новых исправлений ещё не выполнены.

Проверки документального шага исполнены: docs_structure_test PASS (10previous editions с Git/hash/bytes,29live,75immutable,4173source-items,69dispositions); doc_links1202/0broken; git diff --check clean. Промежуточная проверка до завершения HISTORY/переноса указателя D-RAD2 дала3broken anchors, после завершения0; это не runtime FAIL. Код/ресурсы/gradle.properties не изменены, runtime320. Нового aggregate/native/GameTest/TPS/heap прогона не было;81PASS относится к предыдущему завершённому runtime-шагу. Следующий шаг — ответы на blockers5/6/9 и воспроизводимые root-регрессии, не обещание готовых14фиксов.

## PERF-001, цикл7/8 — 0.3.321

После постановки038fc35 автор ответил на4вопроса:5только adjacent endpoint через chunk boundary;9bridge выбирают создатели серверов, targetWG7.0.13/WE7.4.x, выбран optional_strict_regions;6coalesce/reject/Stop priority +cache+тихое10секКД после completion отдельно по операции. Ответы перенесены в CURRENT/связанные профили; не объявлены реализацией5/6/9.

Production: TickIntakeBudget + receiveGtu обоих старыхBE, branchII в I.pushGtu; версия321. Числа вMachineDefs/SecondTierDefs и GtBuffer не менялись; NBT/save/load/loss/output/III/радиация/PNG/Plotnick неизменны. budget owner-local, transient, clock=level.gameTime (до присвоенияlevel0), probe не пишет даже future-tick clock, accepted-only commit.

Новый legacy_accumulator_test.py компилирует **реальные неизменённые GtBuffer/MachineDefs/SecondTierDefs/TickIntakeBudget и точные fields/imports/receiveGtu/pushGtu обоихBE**. Узкие doublesLevel/BlockPos/NBT/route dispatcher/III boundary не nativeAPI/lifecycle proof. Real GtBuffer decimal string save/load прогоняется противNBTboundary, не настоящего world save. Route loss17 — заданный dispatcher результат для проверки source debit, не реальный routing algorithm.

Результат **80605assertions PASS**, randomized200ticks×100calls×2stores; simulation/capacity/tick reset/accepted-only/independent owners and callbacks, future probe readonly, outflow не пополняет intake. ВсеI/II equal/lower/higher направления;IIIветка сохраняется;5источников с реальнымIoutput заполняют общийIIintake. Начальная fixture ошибочно ожидала насытить144000intakeII двумя источникамиI с32000output каждый; assertion честно упал64000, fixture исправлена на5источников, баланс/проверяемый код не подгонялись.

--mutation-check **8/8 assertion-killed**, compiler failures не считаются: missingIIbranch/full-diff, no-used budget, mutating simulation, requested-vs-accepted debit, never reset tick, oldper-callI, oldper-callII. Генератор harness берёт именно нужные productionimports, не скрывает забытый импорт wildcard. Первый aggregate **82PASS/0SKIP/0FAIL**, `/tmp/gonzo321-first.log` (81старый+новый). Java21/ECJ/Pillow восстановлены в ignored.cache; fullMC Gradle/build/GameTests/direct+wire/reload/playtest не запускались. Оставшиеся12пунктов PERF-001 не исправлены, вся задача не закрыта.

Финальный повтор321 после полного обновления профилей: **82 PASS / 0 SKIP / 0 FAIL**, /tmp/gonzo321-delivery.log; legacy80605assertions/8of8assertion-killed. Docs11editions сGit/hash/bytes,29live/75immutable/4173source-items/69dispositions PASS; links1209/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; diff-check clean. Новые изменения ограничены7/8, helper/test/version и документами/архивом. FullMC/native/gameplay не запускались, остальные12пунктов не исправлены. Подготовлено к commit/push по стоящему разрешению автора.

## PERF-001/4 — небольшой цикл0.3.322

Автор попросил лёгкие независимые фиксы за один заход, тяжёлое/спорное оставить на потом в текущей задаче. Формат handoff: номера реализации, отдельный игровой чек-лист/ожидаемое поведение; архив только после явного «добро». В этом цикле выбран только4;7/8из321 включены в очередь приёмки, их код не менялся.

После snapshot-восстановления HEAD/indexbf5e72e: временный индекс доказал полное равенство worktree с origin707bc30 (tree3af78b34234f60e3129b79295f5c051cf1a9da99). Только затем mixed-reset на origin, без перезаписи WIP. Java21/ECJ/Pillow восстановлены в ignored.cache. Production: ItemRouting.sinksCached OR→AND, комментарии о TTL/live-проверке и version322; TTL40/budget2048/throughput/физические lookup/liveSinks/sinkLive/радиация/accumulators/PNG/Plotnick не менялись.

routing_cache_test.py компилирует дословные Sink/CachedSinks records/cache fields/constants и методы onWorldEdit/topoVersion/sinksCached/liveSinks. BFS discovery/контейнерный lookup/часы — узкие doubles, не настоящий мир. **126assertions PASS:** пустой поиск→появился endpoint без block edit; age1..40reuse/age41refresh; положительный кэш тоже истекает, hits не сдвигают builtTick; version change внутриTTL; live rejection→удаление cache entry; разные миры/старты; прежний client branch; exhausted-budget fallback и retry при следующем доступном tick. Source sinkLive/path и transfer не переписывались; весь ItemRouting компилируется прежним typecheck с boundary stubs.

--mutation-check: **5/5 assertion-killed**, включая точный старыйOR, игнорированиеTTL/версии, вечный пересчёт и сдвиг границы40. Ошибки компиляции не считаются kill, рабочий файл не мутируется. Первый aggregate **83PASS/0SKIP/0FAIL** (/tmp/gonzo322-first.log):82старых+новый. Native Gradle/Minecraft/GameTests/вагонетка/перенос/TPS не запускались. При исчерпанном BFS-бюджете fallback оставлен — пункт3ещё не закрыт; обещания жёсткого времени discovery на перегруженной сети нет. Архивировать4/7/8без нового ответа автора запрещено.

Финальная доставка322: повтор полного suite **83PASS/0SKIP/0FAIL**, /tmp/gonzo322-final.log; повтор cache126assertions/5of5assertion-killed, /tmp/gonzo322-mutations.log; whole ItemRouting в typecheck56real/154stubs/0errors. Docs11editions/29live/75immutable/4173source-items/69dispositions; links1210/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; diff-check clean. Ни одного изменения archive/DEFFERED;4/7/8остаются вCURRENT до авторского ответа. Native Minecraft/build/GameTests/игровой перенос не запускались. Подготовлено к commit/push в фиксированную ветку по разрешению автора.

## 0.3.322 — игровая приёмка4/7/8 автором, docs-only

28.10.2026 автор подтвердил в игре вагонетки/аккумуляторы/правильный суммарный BE max input/t и прямо разрешил немедленный архив этих записей. Это новый уровень авторского игрового свидетельства, не результат прежних headless harness и не native запуск агентом. ПримерIII:38+38=76GTU/t допустим при достаточном собственном лимитеBE/ёмкости; wire limits и aggregateBE input/t различаются. Исправный runtime/баланс не менялись.

CURRENT сохраняет11открытых пунктов со старыми номерами;4/7/8 вынесены в archive/editions/0.3.322 с точным исходным CURRENT460edfb и manifest. MACHINES/CAPABILITIES/HISTORY/archive index синхронизированы. Прежний полный suite83PASS и mutation результаты — исторические, не новый прогон этого docs-only шага. Проверки документации записываются ниже после исполнения.

Проверки docs-only закрытия: docs_structure PASS —12предыдущих редакций сGit/hash/bytes,29live/75immutable/4173source-items/69dispositions; doc_links1222/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; diff-check PASS после удаления концевого пробела в цитате ответа (слова автора не менялись). Полный code-suite/nativeMinecraft не перезапускались: код/тесты/ресурсы не менялись. Рабочая ветка arena/48845eec-gonzotech, runtime0.3.322.

## PERF-001/2 — одноштучные слоты на подаче,0.3.323

Автор разрешил фикс2и расширил формулировку на единичные слоты вроде карт/TNT «во всех возможных стезях». После вопроса о raw setItem прямо отверг «записать64и потом выбрасывать»: из64на входе берётся1,63остаются у источника. Production SlotCapacity, A/B/TNT capacities, admission gates вItemRouting/InvWrapper/SidedInvWrapper/Hopper и проверки raw replacement реализованы; только новые invalid replacements fail-fast, не drop/trim. Legacy in-place/старый TNTloadSurplus сохранены, обычный склад/GTU/баланс/радиационный закон/PlotnickBlock/PNG не менялись.

slot_capacity_test354assertions: обе карты/TNT,1/2/16/64offers через реальные upstream InvWrapper/SidedInvWrapper algorithms, simulate/commit/repeat/occupied, остаток/сумма/компоненты/caller mutation/dirty, обычный склад и depot64, sided index≠physical index иinvalid, реальные ItemRouting.insertOne/tryPlace, bulk/reject/occupied Hopper, глобальный container max1, raw invalid replacement без изменения/drop, legacy безtrim/in-place update. Тела BE/труб извлечены из production; storage/dimensions/registry/тип мира — doubles (виртуал сокращён для пробы, не новая смета). Hopper original из явно неофициального decomp mirror. Все три полные mixin source тоже компилируются против annotation/API doubles; ручной вызов hook bodies не выдаётся за реальную Mixin weaving/bootstrap. Existing ExternalRadiation.merge в этой count-пробе — boundary double; действующие radiation tests идут общим suite.

--mutation-check **10/10 assertion-killed**: отключитьslot cap; отключитьraw replacement guard; игнорироватьglobalmax1; вернутьA/B64; отключитьInv/Sided hook; перепутатьwrapper/physical index; передатьHopperполнуюстопку вместоoffer; вpipeиспользоватьslot0вместонужного; разрешитьoccupiedA/B. Ошибки компиляции не считаютсяkill, рабочие файлы не мутируются. Неожиданный runtime invalid write в пробе превращается в явную AssertionError нарушения admission, не маскируется как проход.

Upstream fixtures LGPL2.1/origin/hash сохранены offline; byte equality сNeoForge f4b4... и Hopper mirror6e967... проверена gh contents по pinned commits. MixinExtrasWrapMethod проверен на0.4.1; API точного установленного NeoForge21.4.157/nativeMC не компилировался. Первый aggregate **84PASS/0SKIP/0FAIL**, /tmp/gonzo323-first.log; whole SlotCapacity/ItemRouting typecheck57real/154stubs/0errors (+missing-import negative1). Java21/ECJ/Pillow восстановлены вignored.cache.

Пункт2ждёт игры/запуска автора, не архивируется. Выявленные head-card permission vs comment и старые AUTOMINER512/54 vs current runtime64/216 отдельно показаны автору; ответа пока нет, эти политики/баланс не выбирались. Other10пунктов не применены, принятые4/7/8не открыты заново.

Финальный повтор323: **84PASS/0SKIP/0FAIL**, /tmp/gonzo323-final.log; новый admission-тест **354assertions/10of10assertion-killed**, /tmp/gonzo323-mutations.log (добавлены wrong-type offers, raw-guard/globalmax mutants и проверки injector declarations/GUI1). Typecheck57real/154stubs/0errors и missing-import negative1. Docs12snapshots сGit/hash/bytes/29live/75immutable/4173source-items/69dispositions; links1223/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; diff-check PASS. Архив/DEFFERED/PlotnickBlock/радиационныйruntime/энергобаланс/PNG не менялись. Пункт2готов к авторской игре, не закрыт и не архивирован. NativeMinecraft/Gradle/Mixinbootstrap не запускались; два смежных вопроса заданы, ответа пока нет. Подготовлено к commit/push в фиксированную ветку по разрешению автора.

## PERF-001/2 принята; следующий независимый11 —0.3.324,30.10.2026

Автор: «Проверено всё, можно закрывать этот пункт подтвержено проверкой в игре»;2принят и сразу архивирован по standing workflow. Уточнение: не ограничивать карты внеA/B, функционал только вA/B, комментарий не источник механики. Native запуск агентом не приписывается этому ответу. Точные старые CURRENT@e4da112 сохранены вarchive0.3.323сmanifest, закрыт только2. Вопрос старой512фразы не трактован как ответ на баланс.

Выбран11как небольшой независимый следующийфикс. ProductionsetVirtual больше не split/insert/dump/возвращаетcur, только requireReplacement/замена/dirty/stats; empty→empty/invalidindex no-op. Caller audit: один directprivatecaller setItemvirtualbranch; GUI/shift virtual не показывают, real ItemRouting/InvWrapper/SidedInvWrapper делаютпредварительныйadmission/merge, removeItem/removeItemNoUpdate дают явный ownership, hopperfailedtransfer восстанавливаетsnapshot. Старыеstates/legacyNBTне мигрировались; исходные load/save/removeне менялись. Устаревшиеcard/sided source comments уточнены без измененияполитики. Version324. Other9openнеприменены; SlotCapacity/mixins/energy/radiation/PlotnickBlock/ресурсы untouched.

virtual_replacement_test:9363assertions на productionsetter/get/remove/capacity/actualsetItem и upstreamwrapperalgorithms; MCstorage/registries/components/virtualdimensions/ContainerHelper — явные doubles, nativeMixinbootstrap вручнуюwired каквпредыдущемтесте. Сокращённыйvirtual24вместо216дляfixture —не новыйruntimeбаланс. Разные item/components/0/1/3/7/63/64counts, same/different/emptyreplacement, callerownedold/incomingunchanged, storage+explicitownercounts, dirty/stats, invalidindices, nonstackable/oversizedatomicrejection, legacyinplaceno-trim, removeNoUpdate, ordinary/sidedhandler simulate/merge/extract, pipes,100failedhopperrollbackиsuccess. Нет автоматическихinsert/dumpcall, лишнегоclone/grow/split. Existingradtransfer вcount-probe boundary, общийradiationsuite сохраняется.

--mutation-check8/8assertion-killed: exactold0.3.323setterизe4da112(negativefixtureсGit/hash/bytes), auto-insertold, auto-dropold, merge, skipclear, missinglimit, missingstats, missingdirty. Compilerfailure killнеявляется, unexpectedinvalidreplacement вprobe — явнаяAssertionError. Старый2400charwindow sourcepin заменён точнымmethodparser+запретgrow/split/insert/dump, не ослаблен. Wholetypecheck остаётся57real/154stubs/0errors; fullnativeBEcompile/GameTest/Minecraft/TPS не запускались.

Первыйaggregate85PASS/0SKIP/0FAIL, /tmp/gonzo324-first.log; Java21/ECJ/Pillow вignored.cache.11готовк авторской игре, не закрыт/не архивирован;2/4/7/8не открытызаново. Docs/current/profile/frozenprevious/HISTORY обновлены тем же шагом; новые тонкие вопросы не выбраны молча.

Финальный повтор324: **85PASS/0SKIP/0FAIL**, /tmp/gonzo324-final.log; virtualreplacement **9363assertions/8of8assertion-killed**, /tmp/gonzo324-mutations.log, в томчисле exact323fixture сGit/hash/bytes. Документы:13зарегистрированныхprevioussnapshots сGit/hash/bytes,29live/75immutable/4173sourceitems/69dispositions; links1236/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; diff-check PASS. Typecheck57real/154stubs/0errors +negativeimport1. Archive323 —только принятое2;11не закрыт/не архивирован, остальные9неприменены. НативныйMinecraft/полныйGradle/BE/GameTests/TPS агентом не запускались; авторская игра подтвердила только2. Баланс/радиация/SlotCapacity/mixins/PNG/PlotnickBlock/DEFFERED не изменены. Подготовлено к commit/push в фиксированную ветку по разрешению автора.

## 0.3.324 —11принят автором31.10.2026, docs-only

Автор на игровой handoff11ответил «Да, отлично, тоже закрываем это в историю» и попросил уточнить следующие крупные фиксы.11закрыт/сразу архивирован по standingworkflow, CURRENT432b848побайтно сохранён вarchive/editions/0.3.324/manifest; предыдущиеиздания неизменны. Открыты9пунктов1/3/5/6/9/10/12/13/14. Runtime/код/тесты/баланс/радиация/ресурсы не менялись. Прежний полный85PASS и9363/8mutants — результат доставки432b848, не новый code-suite этого шага; native запуск агентом не заявляется. Следующее предложено1, далее проектирование3/13/14; incomplete-deliverypolicy3и старый документальный54/512вопрос ждут ответа. Проверки документации фиксируются после исполнения.

Дополнительное необходимое уточнение для5: разрешена ли единственная boundedпроба непосредственного неизвестного соседнего чанка, если она может показать неendpoint, либо использовать только ранее подтверждённыеадреса и ждать внешней загрузки новых? Безчтения/метаданных неизвестныйтип неопределим; existing adjacent-only/no-cascade policy невыдаётся за ответ наcold-discovery. Этот выбор пока не сделан; новыйruntimeкод5неприменён.

Проверки docs-only приёмки11: docs_structure PASS —14previoussnapshots сGit/hash/bytes,29live/75immutable/4173sourceitems/69dispositions; doc_links1248/0broken; validate3285JSON/608items/en=ru1281/0errors/0warnings; diff-check PASS. Code-suite/Gradle/nativeMinecraft не перезапускались, runtime0.3.324/код/тесты/ресурсы не менялись.11архивирован,9остальных открыты; никакая предложенная политика BFS/unknownchunk/doc512 не применена без ответа.

## 0.3.324 — уточнение1автором31.10.2026, docs-only

Автор сообщает работающий перенос/разный первыйGUIслот1/28, ставит под вопрос нужностьфикса1. Это не подтверждениеwrappertransfer0. ItemRouting.containerAtнет27limit, он делегируетванильномуHopperlookup; sinkLive==самостоятельныйsource-risk. Внешнийdecompmirror/NeoForgebranchpatchнеравныпроверкеточногоruntime. Рекомендованы fullhalf→emptyother insertion иemptyhalf→stockother extraction; поодномупервомуслотунельзяразличитьпорядок54иrealhalf-only27. Код1не менялся, обязательныйфикс приостановлен, не закрыт/не архивирован. Native/Minecraft/code-suite не запускались, runtime0.3.324. Документальные проверки ниже после запуска.

Проверки docs-only уточнения1: docs_structure PASS(14previoussnapshots/Git/hash/bytes,29live/75immutable/4173sourceitems/69dispositions), doc_links1248/0broken, validate3285JSON/608items/en=ru1281/0errors/0warnings, diff-check PASS. Новых native/code-suite запусков нет, код/ресурсы/runtime0.3.324/архив неизменны;1открыт как исследование и не изменён по гипотезе.

## 0.3.325 —PERF-001/5,loaded-only;1без изменения порядка

База3224f15,31.10.2026. Автор уточнил54-доступ28–54→1–27и разрешил начать5. В отдельном выборе отверг иcold-probes, иknown-endpoint loaders: вообще никакой загрузки чанков трубами, обычныйserverlifecycle. Предыдущие adjacent-loading предложения отменены, не остаются текущимвариантом.1не закрыт/не реализован каккосметика; практические conservation/self-route/topology пробы записаныCURRENT.

Production: ItemRouting loadedroot/source/BFSnode+frontier/cachedpath+lastpipe/containerlookup. Chestcompanionguard до vanillaHoppercombiner, иначе последний может читать unloadedhalf. ItemFilterRouting имел собственные directHopperlookup/BFS; они тоже переведены на sharedguard, добавлены loadedcarrierroot/frontier/queuednode/commitpath/endpoint и powereddeletion boundary. Sinkдобавилroot/containerPosтолько для loadedпроверки, не изменялowneridentity/ordering. Каналы/бюджеты/TTL/радиация/BEreplacement/PlotnickBlock/баланс/ресурсы не менялись. Нетtickets/probes/новогокэша илизагрузчика.

Новый item_loaded_chunks_test компилирует **оба целыхproductionrouter, scavengerI/II, UniversalNodeComparator, LoadedItemChunks + реальныйSlotCapacity**, не переписанные алгоритмы. Level/Container/MCblock+chestcombiner/itemcomponents/BE/flow/radiation boundary — явныеdoubles. Мир считаетstate/containerlookup и падает AssertionErrorприunloadedworld/inventoryread. Проверкиloadedобычного переноса/сумм, coldsource/target/root, warmedendpoint, capturedsourceвключаяchestcompanion, middlepathcut/singlelastpipe/queuedunload, LEFT/RIGHTcompanion/SINGLEboundary, longline10000и100повторов безрасширенияloadedset, внешняязагрузка+обычныйTTLexpiryбезworldedit, actualfilterpass/reject/IIdeletion/mid-discoveryunload/commitcut/longline. Не заявлятьреальныеserverchunk lifecycle/TPS/модовуюchestidentity/sidecarphysicsпоэтоймодели.

Exactoriginal324ItemRouting+ItemFilterRouting сохранены сGitcommit/SHA256/bytes; тест компилируетобоихстарых и требуетAssertionError, отдельно старыйfilterсновымItemRoutingтоже долженпровалиться. --mutation-check:23/23assertion-killed (8ItemRouting +13filter +2indirectredstone): entry/source/node/frontier/cachepath/lastpipe/endpoint/chestguard; filterentry/discovery/frontiers/queued/commit/root/segments/deletion/directvanillalookup. Compilerfailure не считаетсяkill. Старыйwaste/rendersecond_pass обновлён наактуальнуюsinkrecordформу иисполняетновыйsinkLoaded сявнойall-loadedграницей, его прежниепроверки неослаблены; новая loadedрегрессия не имитируетfilterплейсхолдером.

Финальныйtypecheck включает ItemFilterRouting/scavengerI/II/LoadedItemChunks/UniversalNodeComparator:61реальныхфайлов/162compileboundarystubs/0ошибок +negativeimport1/1. Перваяпопытка новогоCheststub ошибочноиспользовалаProperties.of, отсутствующий вexistingstub; исправлен толькоstubconstructor, productionAPIне подгонялся. API TYPE/ChestType/getConnectedDirection сверены сMCP1.21.4mirror, vanillaDoubleBlockCombinerчитаетneighborstate безloadedcheck; источникнеавторитетен, это не полныйMojmap/nativebuildproof. JDK21.0.8/ECJ3.46.100/Pillow12вignored.cache. Первыйfullsuite86PASS/0SKIP/0FAIL (/tmp/gonzo325-first.log); финальныеповторы ниже. NativeGradle/Minecraft/GameTests/реальныеchunkunload/TPS не запускались. Игроваяприёмка5обязательна отдельно.

### Дополнительная root-сверка325: косвенные redstone-reads, не ложное завершение5

Первый86PASS/18mutants относился к простому boundarydoubleisPowered, не к нативномуredstone. Финальнаясверка SecondItemScavengerBlock→Level.hasNeighborSignal→SignalGetter/getSignal/getDirectSignalTo +RedStoneWireBlockgetConnectionState выявила косвенныеgetBlockStateбезloadedguard. Автору прямо сообщено, что предыдущийharnessнепокрывалэтотпуть, и5неполностьюготов. Вask_userвыбран **endpoint_unavailable**: unknownIIpowerисключаетendpointполностью, дажеобычнуюrejectветку. Не считать неизвестноеOFF.

ProductionдобавленcanCheckPower наscavengerI/II, sharedLoadedItemChunks сfourcornerhasChunkAtдляконсервативнойvanillareadокрестности±3X/Z; это не загрузка/кастомныйрадиусигрока. ActualIisPoweredнетredstoneчтений; IIвходитвванильныйhasNeighborSignalтолькопослегейта. В vanillaLevel.updateNeighbourForOutputSignal выявленещёвторойgetBlockStateзаconductorбезпроверки; UniversalNodeComparator теперь откладываетнебезопасноеуведомлениепреждезаписиLAST_SIGNALS, повторяетпослевнешнейзагрузки. Самsignal/потокнеизменны.

Harnessрасширенactualscavengers+actualcomparator+actualreadenvelope, nolongerfakeisPowered. NativeLevel.signal/conductor/wire-read/notificationcallbacks по-прежнемумодельные! Guards проверяютсянаknownthird-hopwirefootprint, недоступнойIIreject-веткеприloadedлокальномreceiver, passiveI, loadedunpoweredIIcap2/loadedpoweredIIcap2, negativeXZ/углах/границах, delayedanalogpublication/retry/rising/falling. Естьточныйnegativeoriginal324comparatorсGit/hash/bytes, дополнительноизолированныйoldfilter+fixedpipe истарыеобаrouter. **393assertions**, **21/21assertionmutants**; compilefailuresнеkill. Redstonemirrorисточники: KernelDotDLL/mcp-1.21.4SignalGetter.java, RedStoneWireBlock.java, Level.java; sourceпроверка неавторитетногоdecompmirrorнеравнаnativeMCдоказательству илиподдержкепроизвольныхmoddedcallbacks. Новыйfullsuiteпослеactualredstoneправок86PASS/0SKIP/0FAIL (/tmp/gonzo325-redstone.log). Окончательныйповторпоследокументовниже.

Финальная доставка325: добавленповторныйloadedpower-envelopecheckнепосредственнопередIIdeletionиreject-insertion, чтобыснимокON/OFFнепереживалвыгрузкуокрестностиприпоиске; источник/endpoint/receiverприэтоммогутоставатьсяloaded. Actualproductionregression **399assertions/23of23assertionmutants**, включаяэтидвановыхnegativeсценария; exact324routers/isolatedoldfilter/oldcomparatorвсекомпилируютсяипадаютAssertionError. Полныйпоследнийsuite **86PASS/0SKIP/0FAIL** (/tmp/gonzo325-final.log), typecheck61real/162stubs/0errors+negativeimport1. Docs16previoussnapshots/Git/hash/bytes/29live/75immutable/4173sourceitems/69dispositions;1259links/0broken;validate3285JSON/608items/en=ru1281/0errors/0warnings;diff-checkPASS. Одиночныйsecond_passвызовбезPYTHONPATHполучилmissingPIL; повторполногоsuiteсPillowвPYTHONPATHпроходитбезSKIP, ошибкаокруженияневыдаётсязарегрессиюкода.5реализован, ждётигры;1непеределан/не закрыт;остальныеоткрытыепунктынеобъявленывыполненными. NativeMinecraft/полныйGradle/GameTests/TPSне запускались. Подготовлено кcommit/push вфиксированнуюветкупоразрешениюавтора.

## 0.3.326 — PERF-001/1 блокирующий doublechest;5принят

31.10.2026, база1370c30. Автор: после«фикса»двойнойсундукневидимкакsourceиreceiverпривсехподключениях; отдельно«Выгрузка чанков и стоп/возобновление передачи работает правильно. Это можно закрывать».5закрыт в326поавторскойприёмке325, не приписывать nativeпрогон всех7вариантовпрежнегочек-листа. Короткаязакрытаязаписьостаётсядо330пообычным4патчам, немедленноеудаление незапрошено.1активныйblockingfix, не optionalcosmetic. СтарыеCURRENT/CAPABILITIES/MACHINES1370сохраненыполностью/Git/hash/bytes; immutableпредыдущиеизданиянетронуты.

Установленactualsource/harnessблокер: freshCompoundContainer→sinkLive`==`→rejecttarget. Сравнениебыло идо325; этоне доказываеттемпоральнуюпричинностьпоследнегоизмененияиливсеsourceсимптомы автора. Предыдущийloadedtest моделировалchestкакстабильныйone-slotBox, не собиралfresh54wrapper;399PASSнеявлялисьchestproof. Новыйsuiteвключаетfreshvieweverylookupи27+27реальныхдляfixtureфизическихслотов, но MCworld/BE/combiner/components/flow/radiationboundariesпо-прежнемумодельные. Exact325ItemRouting+ItemFilterRoutingпобайтносохраненыиGit/hashverified; оба компилируются, затемпадаютdestinationAssertionError. Compilerfailureнеkill. MojmapCompoundContainer.contains(Container)сверенпоpinnedMCPmirror6e96771cc9652af23d86d558e30f9f39a7378225: обаchildпоreferenceidentity; это неавторитетныйmirror, неnativeбинарныйbuildproof.

Production326: sameContainerAtfreshlookup+ordinaryexactidentity/compoundbothcurrentBEinbothviews, loadedguardsпередstate/BEread. Нельзя принять replacementпооднимкоординатам/54size. Sourceпослеdiscoveryповторнопроверяется; sharedsameInventoryисключаетaliasвpipesиfilter; получательCompoundstampитcompanion, sourceповторнопроверяетsame-tickstampпередpush. Нетновогокэша/forcedloads/ordering/rebalance/NBT/radiationchanges.3/13/14непеределаны, acceptedTTL4/слоты2/replacement11/PlotnickBlock/прогрев/PNGunchanged.

Новыйdouble_chest_routing_test: **7498assertions**. 4горизонтальныхpairing/2TYPE/каждыйиз54слотов:ordinary→double/double→ordinary/double→double;63+2→64+1/fullbackpressureнаPULL/PUSH;64warmcacheticks/freshwrappers;currentnear/far/bothreplacementtargetиheldsource, split/re-pair, genuinecompoundunload/reloadвобоихнаправлениях, sameinventory2подключения/no-fictitious-flow,companionshield, AUTOsourcefacescapturedbeforeinsert,filteralias. **9/9assertionmutants:**wrapper==/coordinates-only/only-near/only-far/source-owner/selfroute/companionstamp/capturedsourcestamp/filterselfroute. Loadedtestrunnerрасширенoptionalextra/mainиCompound/BEboundaryAPI, его старые399assertions/23mutantsпродолжаютпроходить; новаяреальнаядляfixtureassemblyвключаетсяявно, не подменяет старыеуказанныеполитики.

Первыйaggregate86PASS/1FAIL: autominer_markersдержалсамдефект`containerAt(...)==sink.container`какобязательныйпин. Онзаменён точнымmethodbodyпиномsameContainerAt: sinkLiveвызов, ordinaryidentity, compoundtypes, currentBEreadsиmembershipобеихполовинвобоихviews. Охранаreplacementнеослаблена;9мутацийпроверяютэтоexecution. Повторaggregate **87PASS/0SKIP/0FAIL** (/tmp/gonzo326-first.log). WholeItemRouting+ItemFilterRoutingвtypecheck61real/163stubs/0errors+negativeimport1. FullGradle/Minecraft/GameTests/TPSне запускались. Прямообъявленаобязательнаяноваяигроваяприёмка1,5неоткрытзаново. Финальныеповторыниже.

Финальная проверка326: **87 PASS / 0 SKIP / 0 FAIL**, /tmp/gonzo326-final.log. Doublechest **7498 assertions / 9of9 assertion-killed**, exact325routerpairnegative; loaded **399 assertions / 23of23 assertion-killed**. Typecheck61real/163stubs/0errors+negativeimport1. Docs19previoussnapshotsсGit/hash/bytes,29live/75immutable/4173sourceitems/69dispositions;1272links/0broken;validate3285JSON/608items/en=ru1281/0errors/0warnings;diff-checkPASS. Промежуточныйdocs_structureFAIL был вызван отсутствием пробелов вформатныхполях«Закрыто в 0.3.326 / Удалить при 0.3.330»; исправлентекст, проверкаретенции не ослаблялась.5закрыт поавторскойприёмке325,1исправлениждётавторскойигры326. НативныеMinecraft/Gradle/GameTests/TPSне запускались. Подготовлено кcommit/pushвфиксированнуюветкупоразрешениюавтора.

## 0.3.327 — PERF-001/1: независимые входы и same-tick forward; cold-source диагноз открыт

31.10.2026, базаccdfa6e. Авторская игра326: ordinary→double теперь работает, cold double→ordinary отказывает, warm single→double продолжает, double→double непоследователен. Уточнено: cold отказ изолированно и при подаче, режимы направленные правильные, не AUTO. Автор выбрал allow_forward: несколько входов/транзит в том же тике, заменить blanket source freeze защитой истории обратных/циклических переносов.1 остаётся открытым;5 не переоткрывается (закрыт326/удалить330),2/4/7/8/11 уже приняты,3/6/9/10/12/13/14 не реализуются в этом шаге.

Production: ItemTransferGuard — чистый per-tick DAG owner keys/denial memo/repeated edge fast path; WeakHashMap поLevel, value безLevel/Container/ItemStack. Обычный block address/entity UUID bits, compound оба текущих физическихBE после независимой membership проверки. Overlap/reverse reachability запрещают self/cycle; независимые inputs/forward разрешены. Только successful insert/source debit записывает edge; следующий gameTime очищает историю. ItemRouting удаляет LAST_INSERTED и receiver mutex; ItemFilterRouting делит TransferAttempt/историю с pipes. Physical identity326/source revalidation/alias/loaded/path checks/TTL/BFS/limits/flow/sidecars сохранены. Нет radiation/balance/PlotnickBlock/PNG/NBT изменений. Оптимизации3/13/14 не присваиваются этому графу.

PipeRoutingDiagnostics: существующий opt-in defaultfalse pipeRoutingDiagnostics, item-сэмплирование раз в20тиков. Root/source state, реальные классы/оба владельца, cache age/version/budget/BFS defer/result, live targets, cycle denial, moved result. Off-path без дополнительных diagnostic world reads. Реальная utility typechecked; router execution tests используют diagnostic boundary stub, её logger нативно не запускался. Config зарегистрирован в GonzoTechMod без custom filename; каталог конкретного развёртывания не установлен, не выдумывается. Временно включить существующий флаг, перезапустить и собрать [GONZOTECH ITEM-DIAG] с failing coordinates, затем выключить (общая pipe диагностика высокообъёмная).

Нативный cold отказ не воспроизведён: cold/warm headless controls проходят и не доказывают устранение. Снятый source freeze объясняет starvation после входящего потока/receiver mutex, **не доказывает отдельную изолированную cold причину**. Ни native Minecraft/полного Gradle/GameTests/TPS, ни новой авторской приёмки327 нет.

Проверки: новый item_parallel_inputs_test4321assertions, три входа/6порядков/single-double/caps1-2/агрегат5/почти full/forward через другую половину/реальный reversed public router/filter shared history/world-tick isolation/3000 transitive-closure oracle trials. Exact326 router pair Git/hash/bytes pinned, успешно компилируется и assertion-fails тот же hub test.9of9 assertion-killed mutants; первоначальный no-cycle mutant выжил на API-only проверке, добавлен настоящий reverse route, теперь убит assertion. Doublechest7498assertions/7of7mutants/exact325negative: obsolete blanket-shield мутанты заменены, физические owner мутации отключают обе дублирующие membership границы; это не прежние9 мутаций326. Loaded399assertions/23of23mutants, exact previous negatives сохранены. Typecheck62real/163stubs/0errors+missing-import negative1. Первый aggregate86PASS/2FAIL был устаревшими static pins о freeze/однострочном guard/формулировке комментария; проекции обновлены к согласованному контракту, повтор88PASS/0SKIP/0FAIL.

CURRENT/CAPABILITIES/MACHINES/AUDIT/HISTORY синхронизированы; полные предыдущие CURRENT/CAPABILITIES/MACHINES ccdfa6e сохранены побайтно/Git/hash/bytes в [OUTDATED](archive/editions/0.3.326/README.md#before-transfer-dag). Старые311–326 snapshots не переписаны; archive README/manifest дополнены. DEFFERED/README не активируют чужие задачи. Архивные старый shield/«1ждёт игры326» читаются как прежний контракт, не действующая политика. Старые вопросы AUTOMINER512 и incompleteBFS3 остаются вне этого шага, решения не выдуманы.

**Финальная проверка327:** полныйsuite **88PASS/0SKIP/0FAIL**, /tmp/gonzo327-final.log. Повторены mutation suites: parallel4321/9of9, double7498/7of7, loaded399/23of23; все kills — assertion, exact предыдущие router fixtures компилируются и отбрасываются проверками. Typecheck62real/163stubs/0errors/negativeimport1. Docs22previoussnapshots поGit/hash/bytes,29live/75immutable/4173sourceitems/69dispositions;1283links/0broken. Validate3285JSON/608items/1281en=ru/0errors/0warnings; git diff --check PASS. Protected runtime/resources неизменны. В финальном doc review обновлены также нижние ссылки CURRENT с перепрогона326 на327; прежние байты уже сохранены в OUTDATED.1 открыт, cold-source не диагностирован,5 закрыт326/удалить330. Native Minecraft/полный Gradle/GameTests/TPS не запускались. Доставка в фиксированную ветку по разрешению автора.
