# Проверки проекта и история использования

## Команды и доказательства

- `python3 audit/run_all.py` — все `*_test.py` отдельными процессами. **SKIP ≠ PASS**, FAIL не маскируется. Код возврата 0 только без FAIL.
- `python3 audit/validate.py` — ресурсы, JSON, локализации и связность контента; это не MC-компиляция.
- `python3 audit/doc_links.py` — Markdown-ссылки, локальные пути и старые текстовые ссылки с явной картой архива. Замороженные архивные тексты не правятся ради нового layout.
- `python3 audit/docs_structure_test.py` — 75 SHA-256, полный исходный inventory, маршруты переноса/все 4173 source-пункта, whitelist корня, разные task ID и четыре патча удержания, разделение доски/заметок и наличие реестра Sinks/ресурсных BE. Проверяет сохранность текста/структуры, **не математически полную сохранность авторского смысла**.
- `python3 audit/content_inventory.py` — ресурсный census в `audit/reports/content-inventory.csv`, не в docs. `--check` сравнивает с локальным отчётом. Это не survival reachability solver: машинные таблицы/события и намерение автора проверяются отдельно.
- JDK/harness/typecheck и Minecraft build/GameTest — разные уровни. Общие скобки/Tree-sitter не доказывают Mojmap/API compatibility. Недоступный JDK/Pillow отмечается как SKIP.
- JDK 21 + зависимости: `./gradlew build runRadiationTest --offline --no-configuration-cache`. Тестовый мир `run-radiation-merge-tests-311`, namespace gonzotech; 11 GameTests в RadiationGameTests. Не считать наличие тестов выполнением клиентского ЛКМ/ПКМ/Q.

## Реестр Python-инструментов

Описание взято из docstring; конкретный контракт — в самом скрипте. История введения/изменений восстанавливается из Git и версионных записей [HISTORY](HISTORY.md), не по предположению о дате имени файла. Java self-tests и stubs лежат рядом в audit; ряд Python wrappers компилирует/запускает их.

| Скрипт | Назначение/самоописание |
|---|---|
| [_ver.py](../audit/_ver.py) | Единая проверка версии мода для пинов (08.10.2026). |
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
| [radiation_behavior_test.py](../audit/radiation_behavior_test.py) | Static and numerical contracts for 0.3.232 radiation flows and medicines.""" |
| [radiation_contour_test.py](../audit/radiation_contour_test.py) | Compile/run the actual pure-Java radiation-contour core, without Gradle/Minecraft. |
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
