# Проверки проекта и история использования

**Актуальный статус05.11.2026, runtime0.3.337:** D-PORTMODE1 и HUD-FLOW2 **ЗАКРЫТЫ** по игровой приёмке автора («Режимы работы вшитых портов приняты, всё работает»; «С кайфом по проверке в игре всё норм»), удержание обоих до 0.3.341; активных незакрытых задач в CURRENT нет. Принят весь стек 0.3.337: grace без вспышки `сшито <?>`, цифры раз в 1–2 тика, серверный срез `getHud` (окно 2 тика), только текущие потоки >0 ед./тик, три строки подсказки. Игровая приёмка — факт автора, не GameTest и не нативная сборка. [Закрытие 0.3.337](#hud337-closed), [приёмочная правка](#hud337-accept), [первая редакция](#hud337), [приёмка 335](#embedded-port335-accept), [режимы 336](#portmode336). Ниже хронологические срезы; прежние ожидания приёмки не являются действующей очередью.

## Команды и доказательства

- `python3 audit/run_all.py` — все `*_test.py` отдельными процессами. **SKIP ≠ PASS**, FAIL не маскируется. Код возврата 0 только без FAIL.
- `python3 audit/validate.py` — ресурсы, JSON, локализации и связность контента; это не MC-компиляция.
- `python3 audit/doc_links.py` — Markdown-ссылки, локальные пути и старые текстовые ссылки с явной картой архива. Замороженные архивные тексты не правятся ради нового layout.
- `python3 audit/docs_structure_test.py` — 75 SHA-256, полный исходный inventory, маршруты переноса/все 4173 source-пункта, whitelist корня, разные task ID и четыре патча удержания, разделение доски/заметок и наличие реестра Sinks/ресурсных BE. Проверяет сохранность текста/структуры, **не математически полную сохранность авторского смысла**.
- `python3 audit/content_inventory.py` — ресурсный census в `audit/reports/content-inventory.csv`, не в docs. `--check` сравнивает с локальным отчётом. Это не survival reachability solver: машинные таблицы/события и намерение автора проверяются отдельно.
- JDK/harness/typecheck и Minecraft build/GameTest — разные уровни. Общие скобки/Tree-sitter не доказывают Mojmap/API compatibility. Недоступный JDK/Pillow отмечается как SKIP.
- JDK 21 + зависимости: `./gradlew build runRadiationTest --offline --no-configuration-cache`. Тестовый мир `run-radiation-merge-tests-311`, namespace gonzotech; по исходникам 333:23 GameTest в RadiationGameTests +2 в PipeInteractionGameTests +4 в CriticalStateGameTests =29 всего (source-count, не новый запуск). Не считать наличие тестов выполнением клиентского ЛКМ/ПКМ/Q.

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
| [plotnick_budget_test.py](../audit/plotnick_budget_test.py) | Actual server dispatcher/FIFO + selected BE methods;2107assertions/9mutants/exact329negative,2048BE/two-world fairness and bounded preflight. MC/events/logger doubles, not native/TPS. |
| [config_tooltips_test.py](../audit/config_tooltips_test.py) | RU/EN labels and tooltip keys for all explicit server/client config translations; missing-key mutants and exact330 resource negatives. Not rendered GUI. |
| [payload_ingress_test.py](../audit/payload_ingress_test.py) | Actual guard/window/config/Plotnickhandler/Fenceregistration + pinned upstream registrar/wrapper;4416assertions/7mutants/3exact329negative. Executor/connection/menu/codec doubles, not Netty/native. |
| [worldguard_bridge_test.py](../audit/worldguard_bridge_test.py) | Actual optional adapter/policy/session + BE gates/mutations +13pinned WG7.0.13 engine sources in plugin-only loader;1326assertions/9mutants/exact331negative,100000absentAPIcalls. Host/geometry/Guava doubles, not native hybrid. |
| [plotnick_work_test.py](../audit/plotnick_work_test.py) | Production selected BE methods + whole gate/revision;388assertions/12mutants/exact328negative. MC/Forge boundaries, not full BE/native/TPS. |
| [radiation_fence_test.py](../audit/radiation_fence_test.py) | Actual handler/creative bridge/core;73assertions/7mutants/exact328handler negative. Packet/menu/copy-hook doubles, not native bootstrap. |
| [blast_furnace_test.py](../audit/blast_furnace_test.py) | Blast furnace (0.3.65): pure 3x3x3 layout self-test via ECJ + wiring pins. |
| [cleanroom_test.py](../audit/cleanroom_test.py) | Compile/run the actual pure-Java clean-room core, without Gradle/Minecraft. |
| [composite_bundle_test.py](../audit/composite_bundle_test.py) | Вспомогательный скрипт / source check |
| [content_inventory.py](../audit/content_inventory.py) | Reproducible resource inventory, NOT a survival reachability solver. |
| [custom_alloy_name_test.py](../audit/custom_alloy_name_test.py) | Пин name-hook'а custom_alloy (инструмент агента, 08.10.2026). |
| [desolation_biome_injection_test.py](../audit/desolation_biome_injection_test.py) | Пин: desolation в оверворлде — мутация пресета ПОСЛЕ сборки реестров. |
| [dispensing_tap_color_test.py](../audit/dispensing_tap_color_test.py) | Ensure the dispensing tap uses the same liquid text colors as the source machines.""" |
| [distiller_raw_fluid_test.py](../audit/distiller_raw_fluid_test.py) | Static regression pins for exclusive mash/wort buffering in the distiller.""" |
| [double_chest_routing_test.py](../audit/double_chest_routing_test.py) | Whole production routing with fresh54 compoundviews, current physical owners, replacement/selfroute/shield; exact325negative/7assertionmutants. Not nativeMC. |
| [item_loaded_chunks_test.py](../audit/item_loaded_chunks_test.py) | Whole production ItemRouting + ItemFilterRouting/SlotCapacity; observed loaded boundaries; exact324routers/comparator negatives and23assertion mutations. Not nativeMC. |
| [docs_structure_test.py](../audit/docs_structure_test.py) | Архив, whitelist, маршруты, task ID/retention и проекции текущего кода. |
| [embedded_port_mode_336_test.py](../audit/embedded_port_mode_336_test.py) | 0.3.336 D-PORTMODE1: четыре авторских решения (общий бюджет/направление/один режим на узел/сохранение при распаде), отрицательные пины снятой заглушки и перебалансировки, lang-паритет, защита отсутствия записи MODE при распаде. Не нативная сборка/GameTest. |
| [hud_337_test.py](../audit/hud_337_test.py) | 0.3.337 HUD-FLOW2 (уточнено по приёмке): только потоки >0, серверное окно `HUD_WINDOW_TICKS=2`/`getHud` для `FlowTracker`/`ItemFlowTracker`, отрицательные пины на маскировку нуля и «строку априори», grace без вспышки `<?>`, `FLOW_REQUEST_INTERVAL=2`, лимит 24/20, удаление четвёртой строки AUTO, закрытие D-PORTMODE1. Исторические пины 0.3.104/тик-барьера не ослаблялись. Не нативная сборка/GameTest. |
| [embedded_port_335_test.py](../audit/embedded_port_335_test.py) | 0.3.335: source-контракты HUD-NODE1/REACT-VAL1/REACT-VAL2 + приёмка автора (ГОСТ-многострочие, отсутствие классической строки до подтверждения, кэш по `BlockPos` без TTL, пачка владельцев по области); отрицательные пины старого поведения, lang-паритет 1297/1297 и защита от реализации D-PORTMODE1. Не нативная сборка/GameTest. |
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

## 0.3.328 — два узла не означают исчерпание бюджета

Новый `item_bfs_budget_test.py` исполняет wholeproduction ItemRouting/FilterRouting/Guard на observed MC boundaries: **340assertions**. Точно2узла/линия;2sources→1receiver, A→B→C и2раздельные осевые линии между одними doubleсундуками (не fork), caps1/2/оба порядка/cold/cache/edit/conservation; остаток2048→2046→2044. Limits0/1/2/3, complete2 против partial2сqueuedтретьим, запретpartialcache и бесплатныйcachehit. Exact327 frozen/Git/hash/bytes **компилируется и assertion-fails** ту же проверку.5of5assertion-mutants: sentinel2/unbounded/falsecompletion/nevercache/freecost.

Обновлён typed result в прежнем cacheharness:126assertions/5of5TTLmutants. Старые sourcepins autominer/node_perf теперь требуют bounded-loop/complete-cache вместо ошибочного sentinel, не подавлены. Заново: loaded399/23of23, parallel4321/9of9, double7498/7of7, exactold negatives. Полный финальныйsuite: **89PASS/0SKIP/0FAIL**, включаяECJtypecheck. Docs:25snapshot hashes/bytes/Git,75legacy originals,1297links/0broken; diffcheck чист. [Предыдущие редакции327](archive/editions/0.3.327/README.md) побайтные.

Author327native отрицательный; ITEM-DIAG показывает бюджетное голодание готовогоsource после полного2-node поиска. Headless не означает native MC/Gradle/GameTests/TPS или игровую приёмку328;1 открыт,3 count/completion исправлен частично, frontier/resumable/fairness остаются. Инвентарные фазы/перенос не переписаны. [Полная хроника](HISTORY.md#perf-001-two-node-bfs).

Команды328 (JAVA_HOME=локальнаяJava21, ECJ_JAR=ecj3.46.100, PYTHONPATH=toolchain Pillow):
```sh
python3 audit/item_bfs_budget_test.py --mutation-check
python3 audit/routing_cache_test.py --mutation-check
python3 audit/item_loaded_chunks_test.py --mutation-check
python3 audit/item_parallel_inputs_test.py --mutation-check
python3 audit/double_chest_routing_test.py --mutation-check
python3 audit/run_all.py
python3 audit/docs_structure_test.py
python3 audit/doc_links.py
git diff --check
```

### Документальный шаг328 — приёмка1 и переносостатка3

Автор подтвердил штатную работу328 и явно разрешил закрытие1; это игровая приёмка автора, не новыйnativeпрогон агента. Остаток3перенесён вD-PIPE2на позже,13/14покаактивны. Следующий10a только предложен/обследованread-only, код не менялся. Прежний89PASS остаётся последнимполнымcodeпрогоном, заново не выполнялся радиdocs-only. Проверкиэтогошага: docs_structure_test (30snapshot Git/hash/bytes,75legacy), doc_links (0broken), gitdiffcheck. Полная хроника/решения — [HISTORY](HISTORY.md#perf-001-item-accepted).

## 0.3.329 — первый ограниченный патч6/10

`plotnick_work_test.py`:388assertions,12assertionmutants; selectedproductionBE methods (admission/dispatch/cancel/surface/row/3queues/NBTpositionhelpers), wholePlotnickTaskGate/WorkRevision. MC/Forge/worldlookup/inventory/protection/effects/старыеoperationcallbacks—границы; не fullBE/nativeintegration.1000дубликатовдоBEtick→0вызововнаpacketпути→1расчёт, cooldown200/exactboundary/failure/persistence/cancel, FIFO1/4/32/256/retryheadtail/worldfail/reservereturn/NBT, row26→1blockreadsнастабильныйпустойслой, chunkboundary/worldedit/full/protection/tags/newY, worldisolation/lifecycle/interesteviction. Это счётчтений, **не MSPT/TPS**.

`radiation_fence_test.py`:73assertions/7mutants; actualhandler+wholeunchangedcreativebridge+actualcore, menu/player/codec/copyinjectionдубли. Нетповторногоsnapshotдля1/2/100/1000beginдоACK,stalebegin/end,close/replacement(noninventorytoo),ACK,server-knownprovenance/count,2players. Тристарыхконтроля328(handleButton/collectOreRow/Fencehandler) Git/hash/bytes; компиляцияуспешна, assertionfailureобязателен. Compilerfailureне считаетсяkill. Новыеfixturesowncode6ddd502, прежниезамороженныеfixturesнеправились. AutominerstaticпиныadaptedкDeque/pending-safechecks, не отключены.

Полныйsuite91PASS/0SKIP/0FAIL включаяtypecheck; новыеизменённыеметодыBE иFenceисполняютсявextractionharness, анеполнойnativeсборке. НовыеGate/Revisionиexistingcoreкомпилируютсяцеликомвharness.6ещёбезsliceworkbudget/общегоpacketcap,10ждётигры; нехваткареальногобюджетаоткрыта,не скрытаподadmission. БезnativeGradle/GameTests/клиентскогопрогона/TPSизмерений. Документацияпроверяетсяdocs_structure/doc_links/diffcheckотдельно.

```sh
python3 audit/plotnick_work_test.py --mutation-check
python3 audit/radiation_fence_test.py --mutation-check
python3 audit/run_all.py
python3 audit/docs_structure_test.py
python3 audit/doc_links.py
git diff --check
```


<a id="perf-budget330"></a>
## PERF-001/6 — цельный бюджет и ingress330, 01.11.2026

`plotnick_budget_test.py`: **2107 assertions / 9 assertion-mutants**, whole actual PlotnickServerBudget/PlotnickFairQueue + selected BE admission/dispatch/lifecycle methods. Проверены2startsнаMinecraftServerтик через2мира,2048ожидающихBE→1024тикапоследовательногообслуживания,64invalidpreflights/тик, reentryeventтого жетика,identityFIFO/firstactor,preflight/Stop/packUp (включаяотменуticketприактивномpackCD),livecardновыйmodeCD,disconnect/worldchange/removedidentity,GUIbudget,exceptionCD,levelunloadдругоймир/stopновыйserver. MC/logger/eventcallbacks/операция-эффект —дубли; setRemoved/onChunkUnloadedwiringпин, не native unload.

`payload_ingress_test.py`: **4416 assertions / 7 assertion-mutants**, whole production PayloadFloodWindow/Guard/Config/Plotnickpayload, actualFence registration; whole unchanged pinnedNeoForgePayloadRegistrar/HandlerThread/MainThreadPayloadHandler иFMLModConfigEvent. Проверены128default/диапазон1–4096/exactboundary/trueslidingwindow/outstandingпосле10secidle/concurrent16producers,100000uniqueinvalidrequestsпослеdisconnectбезenqueue/киклавины,shared2channels/perconnectionisolation,нетmenu/playerчтенийNetty,принятыйbegin→модельнаяvanillamutation→endFIFO,configloading/reloading/unloadingvolatilepublication/unrelatedspec,executor/handlerfailurecreditrelease,logouttombstone/stop/newconnection. Connection/executor/menu/codecs/events/NetworkRegistry—дубли, не реальныйNettyилиvanillaпакетныйbootstrap. Fencecore73assertionsпроверяетсяотдельно, не выдаётсязасовместныйнативныйтест.

Exact329negativecontrolsиз9c2d9b3: небюджетированныйBEprocessCalculation, весьстарыйPlotnickpayload, oldFence register, oldMAINPlotnickregister. Git/hash/bytes проверены, каждый компилируется и обязан assertion-fail, compilerfailureнеkill. UpstreamfixturesAPIs/source/лицензия — [payload_ingress](../audit/fixtures/payload_ingress/README.md), собственныестарыефайлы —[work_329](../audit/fixtures/work_329/README.md). Остальныеupstreamфайлывmanifest —sourceAPIпины, не исполняемыйnativeAPIproof; branchcommits не объявленыexact21.4.157jar.

Воспроизведение (JDK21 + ECJ; Pillow для полного набора):
```bash
python3 audit/plotnick_budget_test.py --mutation-check
python3 audit/payload_ingress_test.py --mutation-check
python3 audit/plotnick_work_test.py --mutation-check
python3 audit/radiation_fence_test.py --mutation-check
python3 audit/run_all.py
python3 audit/docs_structure_test.py
python3 audit/doc_links.py
git diff --check
```

Первыйfull330:92PASS/1FAIL — старый GUIsourcepin ожидал прямойBEtickrefresh; заменён на event/viewer/dirty **иbudget.start** вactualscheduledcallback +BEregistrationpin, не ослаблен. НовыеexecutableGUIbudgetпробы подтверждаютограничение. Следующийfull:93PASS/0SKIP/0FAIL; послефинальногопроходаповтор. СохраненыстарыеPlotnick388/12 иFence73/7mutants/exact328. NativeGradle/NeoForge/реальныйNetty/MixinGameTests/измеренныйMSPT/TPSнепроводились; авторская«Всёштатно»относитсяк329 и не закрывает6/10. Budget2wholejobs—толькокнопки/GUI, неавтоматическийциклбуров и не hard-msгарантия. Сеть—толькодваC2S, неуниверсальныйDDoS/vanilla/othermodsщит. [Решения/хроника](HISTORY.md#perf-001-budget330).

Финальный330повторпослекода/документации: **93PASS/0SKIP/0FAIL**. Docs:41previouseditions/29live/75immutable/4173sourceitems/69dispositions,1356links/0broken;11историческихтекстовыхцитатсявнымархивныммаршрутом. Scopedfixture.gitattributesсохраняетточныеupstreamбайты/пробелы, не ослабляетproduction/test/docswhitespacecheck.


## PERF-001/6/10 — приёмка и tooltip-localization331

Автор подтвердил: «Окей да всё работает Я потестил», разрешил закрыть6/10. Это игровая приёмка механики330, без перечня отдельных нагрузочных/перезапускных сценариев. Java/протокол/квоты/цикл буров не меняются. В331 только RU/EN `floodRequestThreshold.tooltip`; причина проверена по NeoForge ConfigurationScreen на commitf4b4a9655362b103b95769258ed6586597d8b7f4: getTranslationKey(key)+".tooltip", иначе commentfallback. Новый `config_tooltips_test.py` проверяет все явные translationключи server/client: label+tooltip RU/EN и кириллицу RU, два missingtooltipmutants и два exact330negative ресурса. Это resource regression, не открытый GUI.

Команды: `python3 audit/config_tooltips_test.py`, `python3 audit/run_all.py`, `python3 audit/docs_structure_test.py`, `python3 audit/doc_links.py`, `git diff --check`. Полный331прогон: **94 PASS / 0 SKIP / 0 FAIL**. Новая проверка:7явныхconfigключей ×RU/EN label+tooltip,2missingkeymutants и2exact330negative. Docs:48previouseditions/29live/75immutable/4173sourceitems/69dispositions;1380links/0broken. [Приёмка/история](HISTORY.md#perf-001-accepted331).

<a id="perf-001-wg332"></a>
## PERF-001/9 — production optional bridge332

`python3 audit/worldguard_bridge_test.py`:1326assertions/9assertionmutants/exact331compilednegative;100000вызовов безBukkitAPI,0block/chunkreads. Actual adapter/policy/session + неизменённые extractedBE mayTouchPlace/Break/touchCached/digStep/строительство/oreRow/dismantle/TNT;13exact WG7.0.13 classes (FlagValueCalculator/RegionResultSet/NormativeOrders/Flag/StateFlag/BuildFlag/association/groups/interfaces) исполняются из plugin-only classloader. MC/Bukkit/bootstrap/spatial-region lookup/Guava/EnumFlag parse — doubles. Не wholeBE/MC/Gradle/GameTests/actualhybrid/pluginjar acceptance/TPS. Старый306presence-pin invented aliases/getFlagState/permanentfailopen заменён production wiring assertions + поведенческой регрессией, не просто удалён. Exact331source сверяется с Git/hash/bytes и компилируется до assertion failure.

Матрица81×2gate; build veto/both permissions/unset fallback/explicit piston; nativepriority/ties/parent/group; globalauthoroverride и72ordinary-region nativeoracle cases; installedbroken classloader/disabledWG/WE/missingmappedworld/virtualregiondata с bounded diagnostics и recovery; regionmanagerreplace/same-tickflags/unload/start/stop; отсутствиеWG сохраняет eventcache/другиеNeoForgeотказы. Все6mutationpaths реально исполняются с WGdeny, no mutation/debit/award/queue loss и со штатным повтором после allow (build retry harness явно будит прежнюю stalled queue, не меняет runtime wake-up).

Исходники/APIпины: [manifest](../audit/fixtures/worldguard_7/manifest.json), [границы](../audit/fixtures/worldguard_7/README.md). WG7.0.13 82fdc65d0eec1a55b3346824e4e586983e85d62c; WE7.4.2 ff8ccac9d4934b036a18640f54388ef8b543a075; Paper1.21.4 73116ac9dd1d55c59db411a55de7e91e025a0c80. ИсполняемыеWGфайлы сохранены побайтно; неисполняемыеWGmanager/flags/container, WEadapter иCraftWorld — только source pins, не бинарная проверка релизныхjar. Source license/header сохранены, локальныйgitattributes ограничен frozen.txt.

Автор выбрал9 и уточнил target-only/все правки включая ore suction/оба разрешения/buildfallback/explicitpistons; отдельно выбрал globalbuildALLOWoverride после сообщения о nativeWGlegacyисключении. Реального hybridhost пока нет;9 остаётся **открытым для native-приёмки**,12/13/14 не изменялись. Новые признаки прохождения nativeигры не сочиняются. Полные7предыдущихliveредакций exact13be401 в [архиве331](archive/editions/0.3.331/README.md); принятый1 удалён по сроку332,6/10 удерживаютсядо335.

Полный332прогон: **95 PASS / 0 SKIP / 0 FAIL** (`JAVA_HOME` JDK21, ECJ, Pillow; `python3 audit/run_all.py`). Документы:55exactpreviouseditions/29live/75immutable/4173sourceitems/69dispositions;1402ссылки/0broken,11историческихцитатсархивныммаршрутом. `docs_structure_test.py`, `doc_links.py` и `git diff --check` — отдельные проверки; native/Gradle не запускались.

<a id="status-reset332"></a>
## Повторный аудит332: внешние свидетельства и новая регистрация CRIT

**Это сведения переданного пользователем отчёта, не результаты запуска в этой среде.** Проверенная в отчёте базаbc1da5bc / Minecraft1.21.4 / NeoForge21.4.157 / Java21.

| Уровень | Что сообщено | Что это не доказывает |
|---|---|---|
| JAR/штатный GameTest | Сборка и25штатных тестов прошли | Не исправление A1/A2 и не ручная игра |
| Изолированные дополнительные пробы | 25+6=31/31; A1/A2/A3 observation-tests зелёные при дефекте | Не инвариантные регрессии исправленного поведения |
| Python audit | 95/95 после Windows/JDK-адаптации запуска без смены проверяемого runtime/assertions | Не portable-запуск исходных скриптов без адаптации |
| Общая Gradle-команда | build runRadiationTest selfTest завершилась ошибкой:3selfTest failures | Нельзя объявить весь прогон успешным |
| Рецепт | Minecraft отвергает third_grinder при валидномJSON | JSON-синтаксис не доказывает загрузку рецепта |
| WG/нагрузка | Hybrid WG, heap-profile и полный TPS-benchmark не проведены | Закрытие№9 решением автора не является native-свидетельством |

Артефакты указаны на внешней Windows-машине: `C:\Users\User\Documents\Codex\2026-10-20\new-chat\work\audit-332` (`probes-reactor.log`, `build-tests.log`, `results.json`, `retry-results.json`, `portable_run.py`, `probe-project`). Здесь не предоставлены и не воспроизведены; не зависимости сборки. Карта содержит14исходных вопросов/6пакетов, а не14новых активных задач. Подробности A3–A9 не представлены, они не активированы.

Текущий шаг: пользователь подтвердил CRIT-1/CRIT-2 **только сверка/план**, №9 закрыть без native-claim,12 отложить отдельно,13/14 вD-PIPE2. Source-сверка обнаружила захваченный final sheath-list и formed/count-only guards ReactorFuelInventory/ReactorMenu (fresh cache validation в ReactorFuelStorage не защищает уже захваченный GUI-list), отдельный SidedInvWrapper.insertItem и отсутствие radiation sidecar-hook в его SlotCapacity-mixin. Это не новый native-test и не исправление. Полный сценарий/приёмка — CURRENT; нужны отрицательные инвариантные регрессии и отдельная будущая игровая приёмка. Радиационные единицы — mZt/s, не /t; count-dupe/новый краш не установлены.

Старые95PASS/1326WGchecks и исторические результаты выше не перезаписаны новыми значениями. Runtime остаётся0.3.332; новые GameTest/Gradle/полный Python-suite в этом документальном шаге не запускались. Предыдущие11liveредакций exactbc1da5bc сохранены полностью; этот fetch не менял local HEAD/index/чужой WIP. [Архив](archive/editions/0.3.332/README.md), [решения](HISTORY.md#status-reset332).

Проверки документального шага: `python3 audit/docs_structure_test.py` — PASS (66previouseditions с hash/bytes/Git,29live/75immutable/4173sourceitems/69dispositions); `python3 audit/doc_links.py` —1454links/0broken,11историческихцитат с явным архивным маршрутом. Проверка whitespace выполнена через `git diff --no-index --check` по14реально изменённым файлам относительно exactbc1da5bc (часть docs локально Git-untracked, обычный diff их не покрывает); отрицательный контроль с trailing space обнаружен. Пять исходников CRIT (ReactorFuelInventory/ReactorMenu/ReactorFuelStorage/InvWrapperRadiationMixin/SidedInvWrapperSlotCapacityMixin) побайтно совпали с базойbc1da5bc. Hash-сверка со снимком начала шага подтверждает отсутствие изменений Java/resources/.gitignore/README/постороннего WIP и отсутствие удалений. Полный95suite/Gradle/GameTests/selfTest не перезапускались; runtime0.3.332. Коммит/пуш этого документального шага не выполнялись: локальная ветка наbf5e72e, remote наbc1da5bc, рабочее дерево содержит ранее существовавший большой дифф. Историю/индекс не перестраивали и чужие изменения не коммитили.


> Историческая запись авторской упаковки до переноса в audit/author test. Приведённые в ней успешные link-check относятся к прежнему расположению, не к push6ea3f43; новые результаты и решения ниже имеют приоритет. Полный оригинал 6ea3f43 сохранён в OUTDATED_AUTHOR_PUSH332.


<a id="audit-packages332"></a>
## 0.3.332 — упаковка задач повторного аудита

01.11.2026: по запросу автора создана [карта шести пакетов](archive/editions/0.3.332/OUTDATED_AUTHOR332_00.md) в info. Это docs-only: код и ресурсы не менялись, runtime-тесты на этом шаге не перезапускаются. Результаты предыдущего аудита: build JAR PASS; штатные GameTest25/25; изолированные 25+6=31/31 (часть доказывает дефекты); Python95/95 с адаптацией Windows/JDK; selfTest3 FAIL; реальный загрузчик отвергает рецепт third_grinder. Эти границы сохранены в пакетах. Текущие проверки упаковки и результаты добавляются после их выполнения.

Результат проверки упаковки: docs_structure_test PASS (29 live, 75 immutable originals, 55 previous editions); doc_links PASS (1416 ссылок, 0 битых); дополнительная проверка всех ссылок новых пакетов и SHA-256 доставки 7 файлов PASS; прежние байты трёх дополненных документов сохранены; git diff --check PASS. Первый docs_structure прогон выявил недостающий маркер статуса новой записи CURRENT, он исправлен, повторный прогон PASS. Runtime-проверки повторно не запускались.

<a id="author-sync332"></a>
## Авторский push6ea3f43: проверка доставки и единый срез

**Доставка.** Remote ветки `arena/48845eec-gonzotech` подтвердил commit `6ea3f4330136353ca845c0cbc5cb3e7b142895c3` (`test`), parent `bc1da5bc`. Семь новых Markdown-пакетов и три дополненных документа, ноль runtime/build/version-изменений. Это задания/сводки, не исполняемые тесты и не логи. GitHub check-runs:0; новый CI/native-прогон не подтверждён. Проверка 720 ссылок по 10 изменённым документам pushed tree выявила 18 неверных путей: ../docs и ../src из audit/author test разрешались внутри audit, а docs ссылались на отсутствующую info. Push успешен; документальная навигация была сломана.

**Безопасная Git-база.** Старый HEADbf5e72e — предок remote; весь runtime уже совпадал. Различались 14 локальных документальных правок, а 7 пакетов отсутствовали. С разрешения автора при пустом индексе выполнен mixed fast-forward HEAD/index на 6ea3f43, без перезаписи всех 5637 существовавших рабочих файлов (побайтно проверено). Пакеты прочитаны из Git. Предыдущая работа сохранена checkpoint-коммитом a6be9ac; три remote-редакции и 11 снимков status-reset уже в архиве. Дополнительно сохранены 16 следующих профилей и все 7 исходных пакетов. Старые авторские записи AUDIT/HISTORY объединены; их info-link направлен на архив, полный original6ea3f43 сохранён. Hard reset/force-push/другая ветка не использовались.

**Действующая очередь.** CURRENT — пакет 02/CRIT-1/CRIT-2, только сверка и план. DEFFERED — D-PIPE2(01,A3/A5/13/14/остаток 3), D-SEC1(03,A6/A7), D-PERF12(04,12), D-CONTENT1(05,A4), D-QA1(06,A8/A9). SEC-3 не открыт: автор повторно оставил№9 закрытым без native-claim. Все 88 старых некатегоризированных task bodies/ID сохранены дословно вне пакетов. D-ENG1 лишь перемещён из позиции после «Не являются открытыми задачами» к открытым uncategorized; D-PLOT1 остаётся ближайшим deferred. Существующие рецептурные/JEI/прочие задачи к пакетам не присоединены.

**Source-сверка и границы.** Подтверждены per-face perItemMap в pushItems, поиск sinks фильтра до no-work return, unusedG в third_grinder и crisis_click→enqueueWork→onItemUsed. Эти вопросы не исправлялись. Пакеты — задания/сообщённые результаты, не logs/probes; Windows-артефакты и 6 дополнительных test bodies не загружены. JAR/GameTest/selfTest/полный Python runtime-набор здесь не перезапускались. Source-count:23 в RadiationGameTests +2 в PipeInteractionGameTests =25 всего, не 25 в одном классе и не новый запуск; само старое 23 класса корректно. AUTOMINER описывает существующие 216 мест/54 группы по 4 стопки/MAX_STACK64 с обычными лимитами типа; прежние 54/512 в архиве. Новый баланс/миграция не выбирались, QA/DOC-пакет не закрыт.

**Архивный контроль.** docs_snapshots расширен только на непосредственные Markdown-источники audit/author test, не произвольный audit. Hash/size/exact-Git/проверка незарегистрированных OUTDATED сохранены. Новый docs_snapshots_test проверяет разрешённые docs/author sources и отклонение чужого audit, вложенных путей, абсолютных путей, traversal, symlink-выходов, повреждений размера/hash, дубликатов, отсутствующих/незарегистрированных снимков, чужой папки manifest и несовпадения с Git. Ранний doc_links выявил два ложных textual path в отрицательных test fixtures; несуществующие образцы теперь собраны из частей, правила checker не ослаблялись. Автоматическая расстановка пробелов затронула перемещённый D-ENG1: guard поймал отличие, исходный body восстановлен; все 88 проверены повторно. Это документальные корректировки, не runtime-дефекты.

Runtime0.3.332. [Исходники и редакции](archive/editions/0.3.332/README.md#author-sync332), [текущая работа](CURRENT-STATE.md), [отложенная очередь](DEFFERED.md). Итоговые исполненные проверки — ниже.



**Исполненные проверки синхронизации:**
- `python3 audit/docs_snapshots_test.py` — PASS,13 тестов с положительными и отрицательными случаями; проверяются также exact-Git и запрет незарегистрированных снимков.
- `python3 audit/docs_structure_test.py` — PASS:92previous editions с hash/bytes/Git,29live docs,75immutable originals,4173source items,69historical roadmap dispositions; lifecycle/ownership/code projections сохранены.
- `python3 audit/doc_links.py` —1559 проверенных ссылок,0 битых;11 исторических цитат имеют явный архивный маршрут. Независимый расширенный разбор Markdown, включая `<пути с пробелами>`, проверил1556 навигационных ссылок без ошибок; frozen исключены только по регистрации.
- Семь исходников автора совпали побайтно с6ea3f43; прежние архивные payloads неизменны. Все88 старых uncategorized task bodies/ID/membership сохранены дословно; первая секция DEFFERED содержит ровно пять пакетов, CURRENT — CRIT-1/CRIT-2 без runtime-разрешения.
- Все5428 файлов pushed tree вне документального контура/его validator совпали с6ea3f43; pre-existing non-doc work сохранён.23+2GameTest definitions и константы216/54/64 сверены по исходникам, не исполнением игры. `git diff --check` — PASS.

Доставка выполняется обычными commit/push в `arena/48845eec-gonzotech`, без force и без смены ветки; итоговый SHA сверяется с remote и сообщается автору. Runtime0.3.332; full runtime-suite/Gradle/GameTest/selfTest/native WG/heap/TPS в этом документальном проходе не запускались.

<a id="crit333"></a>
## 0.3.333 — CRIT-1/CRIT-2: root fixes и отрицательные регрессии

Автор разрешил перейти от планирования к двум фиксам и подтвердил закрытие старого GUI при внешнем расформировании/изменении реактора. Version333; оба пункта не закрываются без native/авторской приёмки. Полные 10 предыдущих редакций 332 сохранены с exact462183e/hash/bytes/Git в архиве; предыдущие immutable тексты не менялись.

CRIT-1: runtime-only fuelInventoryRevision на configure/clear/load/remove; immutable captured owners, panel/sheath physical identity, level/loaded/bounds/count checks. getItem проверяет конкретного владельца, не сканирует весь объём на слот. clicked и прямой quickMoveStack проверяют актуальность до vanilla-debit; closeContainer только для своего текущего GUI. Cursor возвращается стандартно, включая drop при заполненном инвентаре. Устаревший вызов не закрывает другое меню. Реакторный storage/redistribution/термодинамика не переписывались.

CRIT-2: SidedInvWrapperRadiationMixin зарегистрирован отдельно, require=2 для full/partial commit grow. До grow accepted-copy объединяется со старым слотом через текущий ExternalRadiation.merge; обе дозы взвешены по фактическим count. simulate/reject/остаток/source/SlotCapacity/обычный InvWrapper не менялись. Внешняя MenuDoseTransaction проверена без двойного merge.

`audit/critical_state_test.py --mutation-check` компилирует целые production ReactorFuelInventory/ReactorMenu/BaseMachineMenu и ExternalRadiation/DoseMath/MenuDoseTransaction, извлекает configure/clear panel bodies, использует hash-pinned NeoForge wrappers с дословными production hook bodies.1692 reactor +2566 radiation assertions PASS. Оба exact462183e baseline assertion-killed;7/7 target mutants assertion-killed, compile failure не считается kill. Первоначальный shift-mutant выжил: тест вызывал shift только после закрытия; добавлены независимые entry-path проверки direct shift/click, повторный прогон ловит оба. Проверены owner replacement даже без removed flag, reconfigure при том же count, foreign level/bounds, unloaded panel/sheath/bounds, два viewers, explicit reopen, direct stale extraction/clear, другой активный GUI, distance, weighted merge в обе стороны, empty/partial/full/face remap/reject, simulate, original offer, incompatible type и outer transaction.

**Граница:** world/slot/click/close/storage/copy hooks и ручная вставка hook bodies — doubles, не настоящий Mixin application/MC/Mojmap/codec/save-load. Panel load/remove increment проверен source-wiring, не runtime NBT. Добавлены 4CriticalStateGameTests с настоящими menu.clicked/SidedInvWrapper/ExternalRadiation/MenuDoseTransaction: replacement-before-debit/GUI-close, clear+reconfigure, insert matrix, outer transaction. Reactor fixture связывает реальные BE напрямую, не доказывает полноценную shell validation/thermal physics. В 333 теперь 29 штатных определений (23+2+4); прежние внешние 25/31 остаются результатами базы 332, не этими новыми тестами. Native GameTest/Gradle/client/save-load здесь не выполнены; это не 29PASS.

Инструменты: Java21.0.8 runtime из jdk4py21.0.8.2 (PyPI) и ECJ3.39.0 с -21, не полный JDK/javac. ECJ взят из GitHub Maven-cache blob0cfc706f6795974c0c9756f600233d567f8fee1a, checksum проверен; SHA-25601f5a92ac19bb2b3bf85e295a68f2c73c264369109158b566ce9b490af982948. Бинарники/Pillow13 и отчёты только в ignored.cache. Прямая загрузка Temurin JDK остановилась на недоступном release-assets host; Gradle distribution/Minecraft/Maven dependencies и полноценного JDK здесь нет. Не выдавать ECJ с MC doubles за сборку мода. Полный runtime/resource/source Python-набор и docs-проверки фиксируются ниже по результатам.

Для native-проверки автора: `./gradlew build runRadiationTest --no-configuration-cache` с JDK21 и зависимостями. Известные selfTest3FAIL/third_grinder unusedG остаются D-QA1/D-CONTENT1 и не лечатся этим патчем; selfTest не добавляется в эту команду ради обхода известного результата, а сохраняется отдельным незелёным долгом. Ни WG9, ни deferred, ни баланс/legacy/шалкеры/PlotnickBlock.java не активированы. [CURRENT](CURRENT-STATE.md), [история/решение](HISTORY.md#crit333), [точные предыдущие редакции](archive/editions/0.3.332/README.md#crit333).

Предыдущая редакция: [333 перед исправлением сборки](archive/editions/0.3.333/OUTDATED_BEFORE_API334_AUDIT.md); [разбор334](HISTORY.md#crit334).


**Итоговые исполненные проверки333:**
- Полный набор97Python test-скриптов —97/97PASS:95runtime/resource/source-скриптов (включая новый critical_state_test) и2 документальных. Во внутренних логах нет SKIP; документы/ссылки проверены повторно после синхронизации.
- CRIT-regression:1692reactor +2566radiation assertions PASS; exact462183e baseline2/2 и7/7target mutants assertion-killed. Compile failure не считается kill.
- Соседние усиленные прогоны: slot_capacity_test --mutation-check —354assertions,10/10mutants; radiation_engine_test --mutation-check —450609assertions/11groups,12/12mutants. Это production-core/extracted-flow с указанными doubles, не native Minecraft.
- Targeted typecheck:62 реальных исходника +163stubs,0 ошибок; missing-ModMachines-import negative1/1. Это не полный Mojmap classpath. Native CriticalStateGameTests не объявляются собранными/выполненными: четыре новых определения используют настоящие menu/wrapper API; радиационные величины native fixture масштабированы через RadUnits.MILLI.
- docs_snapshots_test:13 тестов PASS; docs_structure_test:102 предыдущие редакции exact hash/bytes/Git,29live,75immutable originals,4173source items,69roadmap dispositions PASS. doc_links:1587 проверенных ссылок,0 битых,11 исторических цитат имеют явный архивный маршрут.
- Scope guard: ровно32 целевых пути,5645 прежних файлов вне них побайтно неизменны. Все88uncategorized task bodies/ID/membership сохранены, пять deferred-пакетов не активированы; HISTORY сохранила полный прежний prefix, старые архивные payloads не менялись. PlotnickBlock.java/PlotnickBlockEntity, рецепты/текстуры/закон радиации/README/.gitignore не менялись. Version333 и min333 regression pin согласованы;29GameTest source definitions — не29PASS. git diff --check PASS; после selective staging проверяется также cached diff.

Оба CRIT остаются **реализованы, не закрыты; ждут native-прогона и игровой приёмки автора**. Сборка Gradle/Minecraft/native Mixins/client/save-load/selfTest здесь не заявлена. Известные3FAIL selfTest и third_grinder остаются в своих deferred. Доставка — обычный commit/push в фиксированную arena/48845eec-gonzotech без force; итоговый SHA сверяется с remote в конце шага.


<a id="crit334"></a>
## 0.3.334 — исправление native GameTest caller после compileJava FAILED у автора

Автор передал настоящий лог сборки333 на Windows: compileJava FAILED, одна ошибка и два предупреждения, BUILD FAILED за55s. Ошибка в CriticalStateGameTests.sidedInsertInsideMenuTransaction: `ChestMenu.oneRow(40,player.getInventory(),box)` не существует; доступна фабрика только `(int,Inventory)`. Предупреждения `[removal] makeMockServerPlayerInLevel` не являются причиной этой остановки. JAR/native GameTest/игровая проверка этим запуском не подтверждены. Прежние97PASS относились к Python/harness, не к компиляции этого native caller — это оказался реальный пробел покрытия агента.

Исправление334: публичный пятиаргументный конструктор `new ChestMenu(MenuType.GENERIC_9x1,40,player.getInventory(),box,1)`. Это привязывает реальный девятислотовый box к меню/внешней MenuDoseTransaction. Добавлены assert identity menu.getContainer()==box и row count1: пустая фабричная копия больше не может незаметно подменить тестируемого владельца. Две deprecated server-player фабрики оставлены: тестам нужен настоящий серверный closeContainer; ни замены на Player-заглушку, ни SuppressWarnings ради зелёного вывода нет. Production runtime CRIT-1/CRIT-2 из333, радиационный закон/баланс/слоты и deferred не менялись; поднята версия334 и min334 pin.

API сверено с MCP1.21.4 mirror `KernelDotDLL/mcp-1.21.4`, commit6e96771cc9652af23d86d558e30f9f39a7378225, ChestMenu.java; сохранены лишь две публичные декларации в audit/fixtures/chest_menu_api с source path/commit/full-source hash/excerpt hash. Это API-signature fixture, не реализация Minecraft и не полный NeoForge/Mojmap classpath. Авторский compiler diagnostic независимо подтверждает двухаргументную фабрику.

Расширен существующий critical_state_test: теперь дословно извлекается и компилируется именно production native-метод sidedInsertInsideMenuTransaction, затем исполняется с API/MC doubles и настоящим radiation core/transaction/wrapper hook bodies. Положительный проход —10assertions (identity/layout и оба направления merge/outer reconciliation). Точный старый caller333 из31fc7ed компилируется отдельно и обязан получить ошибку oneRow arguments (1/1). Это специально ожидаемый **compile-negative**, не behavioral mutant kill; в прежних2baseline/7behavioral mutants compiler failure по-прежнему не засчитывается. Повторный behavioral прогон:1692reactor +2566radiation PASS, baseline2/2 иmutants7/7 assertion-killed. Первый запуск нового шаблона выявил пересечение placeholder CTOR/FACTORY; исправлен порядок подстановок, положительный compiler pass и отрицательный caller перепроверены.

Полная native сборка334/реальное применение Mixins/GameTest/клиент всё ещё требуют повторного запуска автора с JDK21. Локальная проверка использует Java21 runtime+ECJ и явные doubles, не выдаётся за Minecraft. Все4GameTest сохранены (29source-определений всего), утверждения не удалялись ради сборки. Два полных документа333 до смены статуса сохранены exact31fc7ed в архиве с manifest/Git/hash/bytes; HISTORY остаётся append-only. CRIT-1/CRIT-2 остаются реализованными и не закрытыми, теперь ожидают успешного build/native334 и авторской приёмки. [CURRENT](CURRENT-STATE.md), [предыдущий статус](archive/editions/0.3.333/README.md).


Проверки334 выполнены: native-caller API fixture —10assertions PASS; точный старый333 caller получил ожидаемую ошибку компиляции oneRow (1/1 compile-negative). CRIT behavioral1692+2566assertions PASS, baseline2/2 и7/7mutants assertion-killed. Полный набор97Python test-скриптов —97/97PASS (95runtime/resource/source +2docs), внутренних SKIP нет. docs_snapshots_test13PASS; docs_structure104exact snapshots/29live/75immutable/4173items/69dispositions PASS; doc_links1598/0broken,11исторических цитат с явным архивным маршрутом. Whitespace и selective scope проверяются перед commit. Native Gradle/JAR/GameTest334 здесь не выполнялись, авторская приёмка не заявлена.


<a id="reactor-review334"></a>
## Приёмка334 и новые HUD/реакторные задачи — документальный шаг

**Источник:** сообщение автора «Оба теста прошли штатно, фиксы применены, уносим в закрытое» и три новых наблюдения; уточнение через вопрос: «Любая сшитая группа». Этого достаточно для закрытия CRIT-1/CRIT-2 по его решению. Новых логов/счёта всего native suite нет: не заявлять29/29, полный save/load прогон или новый агентский native run. Исторический333 compileJava FAILED не скрыт; исправленный caller334 остаётся регрессионно защищённым.

Новые задачи не исправлялись: HUD-NODE1 — мигает строка группы; REACT-VAL1 — смена режима встроенного узла даёт неформируемое состояние; REACT-VAL2 — после возврата обшивки поршнем formed-текстура расходится с GUI/валидностью. Автор сообщает рабочий поршневой контроль на парогене/турбине; агент его не запускал. Source-сверка: WrenchHud REQUEST_INTERVAL5/FLOW_STALE_TICKS15 и shared FlowEntry.clumpSize; ReactorStructure world-edit invalidation/placement-only initialize; gthPortLimit и MachineDefs=16 364GTH/т на вывод. Это направления расследования, не доказанные root causes. Частота полного пересчёта не измерялась.

План доказательства будущих исправлений: стабильный/сменившийся HUD target с задержками; смена режимов всех допустимых реакторных портов без распада/ресурсного reset и ordinary-node контроль; завершённое piston out/in с проверкой panel/bounds/index/endpoints/GUI, сохранением ресурсов и отказом на настоящем проломе/unknown chunks. Принятые critical regressions сохраняются; не лечить маскировкой invalid GUI или одним декоративным formed.

В этом шаге выполнены только документальные/scope проверки, результаты приведены в HISTORY. Java/resources/Gradle/version/баланс не меняются. Десять прежних документов334 сохранены exactec3ba9d с Git/hash/bytes в [архиве](archive/editions/0.3.334/README.md); старые архивы неизменны. №9/deferred/88uncategorized не переоткрываются и не переупаковываются. [CURRENT](CURRENT-STATE.md), [HISTORY](HISTORY.md#reactor-review334).

Предыдущая редакция: [до авторской приёмки334](archive/editions/0.3.334/OUTDATED_BEFORE_REACTOR_REVIEW334_AUDIT.md); [новое решение](HISTORY.md#reactor-review334).

<a id="embedded-port335"></a>
## 0.3.335 — один микропатч: HUD-NODE1, REACT-VAL1, REACT-VAL2

**Область.** `gradle.properties` → `0.3.335`. Изменены 9 production-файлов (7 Java: `NodeClumpIndex`, `ReactorStructure`, `LevelChunkCleanRoomMixin`, `ReactorControlPanelBlockEntity`, `PipeFlowNetwork`, `PipeRouting`, `WrenchHud`; плюс lang-пары ru/en), добавлены `FormedPorts`, `EmbeddedPortMode`, `audit/embedded_port_335_test.py`, обновлён стаб `ReactorStructure.isMember` в `audit/stubs`. 12 docs-снимков до правок зарегистрированы в `docs/archive/editions/0.3.335/manifest.json` (schema 1, SHA-256/bytes/`source_commit=5426f74f…`). Решения зафиксированы в CURRENT/HISTORY/DEFFERED/RULES/DOCS_RULES и шести профилях; правило 26 соблюдено. Никаких новых PNG: новых айтемов/блоков/GUI нет.

**Новый тест.** `embedded_port_335_test.py` — это source-контракты и отрицательные пины: отдельный reactor-гвард в `kindOf` при сохранённой turbine/steamgen строке; `detachPorts` на всех boundary-портах; `onNodeChanged` после распада; `isCosmeticStateChange`/`onWorldEditStateOnly`/`isShellPart`; `PISTON_REVALIDATION`/`tickPistonRevalidation`/`revalidateSavedBounds`/`clearFormedVisuals` и запрет первичной автосборки; отделение `clumpSize` от `FlowEntry`, `<?>` ключ, фильтр `aimedPos`, один `AllFlowRequestPayload`; `PortInfoPayload`, реальное имя блока и три режима; паритет 1289/1289 lang-ключей; `EmbeddedPortMode.resolve -> current` и закомментированные (не активные) точки вызова в `PipeRouting`. Тест **отвергает** реализацию D-PORTMODE1 в этом патче.

**Границы доказательств (обязательны к цитированию).** Все проверки — локальные Python/ECJ/stubs/source, **не** нативная Gradle/NeoForge/JAR-сборка, не Minecraft-клиент/сервер, не применение Mixins, не 5000-узловой сценарий, не GPU/TPS/производительность, не GameTest и не авторская игровая приёмка 0.3.335. ECJ 3.39.0 взят из npm `@ctxo/lang-java-analyzer` 0.13.0 и репакован как `/tmp/gtvenv/ecj-3.39.0-repacked.jar` (14 659 037 байт); это **не** байт-пин Maven-jar SHA `01f5a92…`. Java 21 — JRE из `jdk4py==21.0.8.2`, без `javac`; Pillow 13.0.0. `SKIP` не считается `PASS`. Известные исторические FAIL `D-AUD1` (`autominer_markers_test.py:31`, `radiation_behavior_test.py:47`), `selfTest3FAIL`, неиспользуемый `G` в `third_grinder` не скрывались и не ослаблялись. `java_bracket_balance_test.py` и tree-sitter-разбор синтаксиса не заменяют компиляцию; полная компиляция всех 710 production-файлов против неполных NeoForge-стабов даёт ожидаемые ошибки отсутствующих MC-типов и потому не выдаётся за proof.

**Счёт полного набора 0.3.335:** `audit/run_all.py` → **98 PASS, 0 SKIP, 0 FAIL / 98**, exit 0 (97 прежних скриптов + новый `embedded_port_335_test.py`). `audit/typecheck_test.py` — 62 реальных файла + 163 стабов, 0 ошибок ECJ; `audit/validate.py` — JSON 3285, items 608, models/item 361, models/block 427, blockstates 294, loot 284, lang en=1289/ru=1289, ошибок 0/предупреждений 0; `audit/doc_links.py` — 1715 ссылок, 0 битых, 11 исторических цитат; `audit/docs_snapshots_test.py` — 13/13 OK; `audit/docs_structure_test.py` — 126 зарегистрированных хешей/байтов/Git-источников, 29 живых документов, 75 оригиналов, 4173 пункта, 69 dispositions; `audit/java_bracket_balance_test.py` — 710 файлов PASS. `git diff --check` чист. Native Gradle/NeoForge/JAR/GameTest/авторская приёмка не выполнялись; патч не утверждён до решения автора. [Хроника](HISTORY.md#v-0-3-335), [CURRENT](CURRENT-STATE.md), [D-PORTMODE1](DEFFERED.md#d-portmode1).

<a id="embedded-port335-accept"></a>
## 0.3.335 — приёмка автора: два замечания к HUD, закрытие трёх задач (05.11.2026)

**Область.** Z не менялся (`one_patch`, патч ещё не был утверждён). Изменены `FormedPorts.java` (`OWNER_*` и `ownerId`), `PipeFlowNetwork.java` (`PortInfoPayload(pos,int ownerId)`, `AllFlowRequestPayload(pos,boolean probe)`, `respondAll(...,probe)`, пачка по области `PORT_PROBE_RADIUS=3`/`PORT_PROBE_LIMIT=96`), `WrenchHud.java` (`Map<BlockPos,PortInfo>` без TTL, `ownerKey`, четыре короткие строки `&f`/`&7`, отсутствие заголовка при `info == null`), lang-пары (+8 ключей на язык), `audit/embedded_port_335_test.py`, живые документы по правилу 26. Новых PNG/ассетов нет.

**Замечание 1.** Длинная однострочная подсказка (`image-1.png`, ушла за край экрана) заменена компактной многострочной: белая `&f` строка «Вшитый корпусный порт <канал> — режим: <режим>», серые `&7` «Хост: <реальное имя блока>», «Принадлежит: <реальное имя сборки>» и (только для `AUTO`) отдельная короткая строка с пояснением «авто (обе стороны, делят общую проводимость порта)», снятая с первой строки. Владельцы — все пять formed-сборок; `OWNER_NONE` = обычный узел.

**Замечание 2.** `image-2.png` показал классическую подпись обычного узла на стене вшитых портов. Причина — не «запоздалая замена», а то, что классическая строка рисовалась ДО прихода `PortInfoPayload`. Владельца нельзя вывести из блокстейта узла (`third_universal_node.json` — одна пустая вариантная модель; `MODE/WATERLOGGED/AXIS/PRESENT` не несут признака formed). Теперь при отсутствии записи в кэше заголовок не рисуется вовсе, классическая строка допустима только при подтверждённом `ownerId == OWNER_NONE`; кэш по позиции живёт без срока жизни и не сбрасывается при смене цели, а сервер на смену цели присылает владельцев всей области разом. Инвалидация — только по замене блока. Это отдельный дефект, не возврат мигания `сшито узлов`.

**Регрессии.** `embedded_port_335_test.py` получил точные отрицательные пины удалённого (`portEmbedded`, `portInfoPos`, `PortInfoPayload(...,boolean)`, `respondAll(ServerPlayer,BlockPos)`, длинная строка с `(%s)`) и положительные пины нового (`portOwners`, `if (info == null)`, `ownerId`, `PORT_PROBE_RADIUS`, `ownerKey`, `wrench_embedded_port.host/.owner.*`, lang-паритет). Известные исторические FAIL не ослаблялись.

**Счёт полного набора после приёмки:** `audit/run_all.py` → **98 PASS, 0 SKIP, 0 FAIL / 98**, exit 0, прогон после всех правок повторён. `audit/typecheck_test.py` — **64** реальных файла + **168** стабов, 0 ошибок ECJ: в набор real-файлов добавлены `FormedPorts.java` и `EmbeddedPortMode.java`, а под них заведены минимальные стабы `BlastFurnaceLayout`/`BlastFurnaceStructure`/`FireboxBlockEntity`/`SiliconFactoryStructure`/`SiliconFactoryBlockEntity` и реальная сигнатура `BlockPos.offset(int,int,int)`; прежняя «недоказанная» нестыковка стаба снята. tree-sitter — 710 Java / 0 синтаксических ошибок; `audit/validate.py` — JSON 3285, items 608, models/item 361, models/block 427, blockstates 294, loot 284, lang **en=1297/ru=1297**, 0 ошибок/0 предупреждений; `audit/doc_links.py` — **1733** ссылок, 0 битых, 11 исторических цитат; `audit/docs_snapshots_test.py` — 13/13; `audit/docs_structure_test.py` — 126 хешей/байтов/Git-источников, 29 живых документов, 75 оригиналов, 4173 пункта, 69 dispositions; `audit/java_bracket_balance_test.py` — 710 файлов PASS. Квалифицированные константы `case FormedPorts.OWNER_* ->` проверены минимальной компиляцией ECJ 3.39.0. `git diff --check` чист.

**Границы (обязательны к цитированию).** Скриншоты автора — визуальное свидетельство двух дефектов, не GameTest и не нативная сборка. Не запускались и не заявляются: native Gradle/NeoForge/JAR, Minecraft-клиент/сервер, Mixins-рантайм, GPU/TPS, 5000-узловой сценарий, GameTest «29/29», повторная игровая проверка уже исправленного HUD, реализация D-PORTMODE1. ECJ — 3.39.0 из npm `@ctxo/lang-java-analyzer` 0.13.0 (14 659 037 байт), **не** байт-пин Maven-jar SHA `01f5a92…`. `SKIP` не считается `PASS`. [Хроника приёмки](HISTORY.md#v-0-3-335-accept), [CURRENT](CURRENT-STATE.md#d-portmode1), [D-PORTMODE1](CURRENT-STATE.md#d-portmode1).

<a id="portmode336"></a>
## 0.3.336 — D-PORTMODE1: реальные режимы вшитых портов (05.11.2026)

**Область.** `gradle.properties` → `0.3.336`. `EmbeddedPortMode.java` переписан из заглушки в серверную политику (`nodeMode`, `allowsIntake`, `allowsOutput`, `resolve`, `portCap`, `sharedRemaining`, `charge`, `cappedIntake`, `WeakHashMap` по миру). `PipeRouting.java`: гейт выдачи на исходном порту, `effectiveBudget` по общему остатку, списание факта, гейт приёма + `cappedIntake` на обоих BFS-сайтах `portRaw`; ordinary machine lanes под прежним `modeOf`. `WrenchHud.java` показывает `nodeMode`. Добавлен `audit/embedded_port_mode_336_test.py`, обновлён блок заглушек `audit/embedded_port_335_test.py`, в стабы добавлены реальные сигнатуры `gthPortLimit`/`steamPortLimit`/`outputPortLimit`. Новых PNG нет.

**Четыре решения автора (дословно в [HISTORY](HISTORY.md#v-0-3-336)):** общий бюджет на сумму приёма и выдачи с ротацией по `gameTime`; «Забор принимает, отдача отдаёт, через отдачу нельзя ввести в условный пароген воду»; один режим на весь узел; сохранение режима в блокстейте при распаде. Разные каналы не делят один счётчик — подробность реализации зафиксирована явно, а не выбрана молча.

**Без перебалансировки.** `REACTOR_GTH_PER_PORT_MILLI = 16_364×MILLI` (16 364 GTH/т на вывод), `gthPortLimit` — потолок реактора, трубы хранят свои лимиты, `PipeType.HEAT` (комментарий 388000 / код 256000) не тронут; `PipeFlowLedger` и левелинг дорожек не переписывались. Общий бюджет не вводит новый потолок выше существующего: `portCap = max(структурный потолок, лимит трубы)`.

**Регрессии.** `embedded_port_mode_336_test.py` пинует: `cap - slice.usedOutput - slice.usedInput`, `(gameTime & 1L) == 0L`, резерв `cap/2`, `if (!simulate …) charge`, `PortKey(pos,type)`, `WeakHashMap`/сброс на тике, `allowsIntake = AUTO||PULL`, `allowsOutput = AUTO||PUSH`, `"mode-output-blocked"`, два `cappedIntake`, `nodeMode` через `PipeType.values()`, отсутствие `setValue(...MODE)` в пяти структурах/индексе, `EmbeddedPortMode.nodeMode(state)` в HUD, точную константу 16 364 в `MachineDefs`, отсутствие дублирования числа в политике, снятые комменты `0.3.336+ D-PORTMODE1` и `// PipeMode effective = …`, lang-паритет RU/EN. Исторические FAIL не ослаблялись.

**Счёт полного набора 0.3.336:** `audit/run_all.py` → `99 PASS, 0 SKIP, 0 FAIL / 99`, exit 0; `audit/typecheck_test.py` — 64 реальных файла + 168 стабов, 0 ошибок ECJ (`EmbeddedPortMode.java`/`FormedPorts.java` в компилируемом ядре); `audit/validate.py` — JSON 3285, items 608, models/item 361, models/block 427, blockstates 294, loot 284, lang `en=1297/ru=1297`, 0 ошибок/0 предупреждений; `audit/doc_links.py` — `1784` ссылок, 0 битых, 11 исторических цитат; `audit/docs_structure_test.py` — `138` хешей/байтов/Git-источников, 29 живых документов, 75 оригиналов, 4173 пункта, 69 dispositions; `audit/docs_snapshots_test.py` — 13/13; `audit/java_bracket_balance_test.py` — 710 файлов PASS; tree-sitter — 710 Java / 0 синтаксических ошибок; `git diff --check` чист.

**Границы (обязательны к цитированию).** Все проверки — локальные Python/ECJ/stubs/source, **не** нативная Gradle/NeoForge/JAR-сборка, не Minecraft-клиент/сервер, не Mixins-рантайм, не GPU/TPS/5000 узлов, не GameTest «29/29», не игровая приёмка режимов 0.3.336. ECJ 3.39.0 из npm `@ctxo/lang-java-analyzer` 0.13.0 (14 659 037 байт), **не** байт-пин Maven-jar SHA `01f5a92…`; Java 21 — JRE из `jdk4py==21.0.8.2` без `javac`. `SKIP` не считается `PASS`. [Хроника](HISTORY.md#v-0-3-336), [CURRENT](CURRENT-STATE.md#d-portmode1), [снимок](archive/editions/0.3.336/README.md).

<a id="hud337"></a>
## 0.3.337 — HUD-FLOW2: стабильность строк и частота цифр (05.11.2026)

**Область.** `gradle.properties` → `0.3.337`. `WrenchHud.java`: `PENDING_GRACE_TICKS = 2`, `FLOW_REQUEST_INTERVAL = 2`, `flowWaitStartTick`, подавление `сшито узлов: <?>` до grace, `flowLine` без `return null` при нуле/устаревании (ноль и полная строка `поток: <?>`), `itemFlowLines` без гашения по свежести, удаление четвёртой строки `hud.gonzotech.wrench_port_mode.auto` (`ArrayList<>(3)`). `PipeFlowNetwork.java`: `FLOW_REQUESTS_PER_WINDOW = 24` для `pipeFlowAll`/`pipeItemFlow` вместо 8. `PortInfoPayload`, `PORT_PROBE_*`, `PipeRouting`, `EmbeddedPortMode`, `FormedPorts` и lang-пары не переписывались. Новых PNG/ассетов нет.

**Причина (диагностика по исходникам, не нативный профиль).** `flowLine` возвращал `null` при `total <= 0`, при ответе старше `FLOW_STALE_TICKS = 15` и до первого ответа; `сшито узлов: <?>` рисовалась до подтверждения. Отсюда «1 сек есть / 1 сек нет» в статике и вспышка в первый кадр. Серверный троттлинг 8/20 тиков при запросах раз в 5 тиков не был причиной, но мешал участить опрос до 1–2 тиков.

**Решения автора (дословно в [HISTORY](HISTORY.md#v-0-3-337)):** убрать вспышку `сшито <?>`; убрать мерцание «поток»; цифры раз в 1–2 тика; D-PORTMODE1 принят; нижнюю строку AUTO убрать целиком. Прежнее `stale_initial=show_before_first` снято: `<?>` только после grace, то есть только при реально медленном сервере/пересчёте; полная строка и запрет вечного скрытия сохранены.

**Регрессии.** `hud_337_test.py` пинует `PENDING_GRACE_TICKS = 2`, `flowWaitStartTick`, `awaitingFirst`, `if (!grace)`, отсутствие трёх старых `return null`, `Math.max(0L, e.pos3d + e.neg3d)`, `GtUnits.rate("<?>", …)`, `FLOW_REQUEST_INTERVAL = 2`, `REQUEST_INTERVAL = 5`, `FLOW_REQUESTS_PER_WINDOW = 24` (3 вхождения), оба троттлинга, `ArrayList<>(3)`, отсутствие `hud.gonzotech.wrench_port_mode.` в HUD при сохранении ключей в RU/EN, `**Статус: закрыто.** … Закрыто в 0.3.337. Удалить при 0.3.341.` и неизменность реализации режимов 0.3.336. Пины четвёртой строки в `embedded_port_335_test.py`/`embedded_port_mode_336_test.py` переведены в отрицательные по явному указанию автора; остальные исторические пины целы, `pipe_routing_perf_test.py` не изменялся.

**Счёт полного набора 0.3.337:** `audit/run_all.py` → `100 PASS, 0 SKIP, 0 FAIL / 100`, exit 0; `audit/typecheck_test.py` — 64 реальных файла + 168 стабов, 0 ошибок ECJ; `audit/validate.py` — JSON 3285, items 608, models/item 361, models/block 427, blockstates 294, loot 284, lang `en=1297/ru=1297`, 0 ошибок/0 предупреждений; `audit/doc_links.py` — `1836` ссылок, 0 битых, 11 исторических цитат; `audit/docs_structure_test.py` — `150` хешей/байтов/Git-источников, 29 живых документов, 75 оригиналов, 4173 пункта, 69 dispositions (проверена и пара 337→341); `audit/docs_snapshots_test.py` — 13/13; `audit/java_bracket_balance_test.py` — 710 файлов PASS; tree-sitter — 710 Java / 0 синтаксических ошибок; `git diff --check` чист.

**Границы ECJ-набора.** `audit/typecheck_test.py` компилирует 64 реальных файла и 168 стабов; `WrenchHud.java` в этот набор **не входит** (клиентские API без полного набора стабов), поэтому правки HUD проверены tree-sitter (710 Java / 0 синтаксических ошибок), балансом скобок (710), `audit/hud_337_test.py` и source-контрактами 335/336, а НЕ ECJ-компиляцией клиентского класса. Это ограничение гейта, а не доказательство native-сборки; `PipeFlowNetwork.java` при этом входит в ECJ-набор и проверен. **Границы (обязательны к цитированию).** Скриншот `image-3.png` — визуальное свидетельство четвёртой строки, не GameTest и не нативная сборка. Все проверки — локальные Python/ECJ/stubs/source, **не** нативная Gradle/NeoForge/JAR-сборка, не Minecraft-клиент/сервер, не Mixins-рантайм, не GPU/TPS/5000 узлов, не GameTest «29/29», не игровая приёмка HUD 0.3.337. ECJ 3.39.0 из npm `@ctxo/lang-java-analyzer` 0.13.0 (14 659 037 байт), **не** байт-пин Maven-jar SHA `01f5a92…`. `SKIP` не считается `PASS`; известные исторические FAIL не ослаблялись. [Хроника](HISTORY.md#v-0-3-337), [CURRENT](CURRENT-STATE.md#hud-flow2), [снимок](archive/editions/0.3.337/README.md).

<a id="hud337-accept"></a>
## 0.3.337 (приёмочная правка) — только текущие потоки >0, лечение среза, а не строки (05.11.2026)

**Замечание автора (дословно в [HISTORY](HISTORY.md#v-0-3-337-accept)):** «Почему-то узлы теперь показывают ВСЁ и сразу. То есть даже если потока нет всё равно висит кипа подсказок. Хотя должны отображаться только текущие потоки которые >0 ед./тик», и уточнение: «Мелькание 1 сек есть - 1сек нет … было не из-за потока! поток был 100% однородный и постоянный … речь шла именно о "багованном" мелькании». Визуальное свидетельство `image-4.png`: 15 строк, 14 нулевых.

**Диагноз.** `PipeFlowNetwork.respond` читал мгновенный `FlowTracker.get(...)` с окном ≤1 тик; при постоянном потоке пакет периодически попадал в фазу, когда срез обнулился, и сервер слал `FlowPayload` с нулём. Клиент гасил строку по `total <= 0` → секундное мелькание. Первая редакция 0.3.337 замаскировала дефект, сделав строку всегда видимой (`поток: 0.0`), и тем самым нарисовала кипу.

**Изменения.** `FlowTracker.HUD_WINDOW_TICKS = 2`, `getHud(...)` (последний подтверждённый срез в ед./тик за окно 1–2 тика, без усреднения/максимумов/замороженного числа), `ItemFlowTracker.getHud(...)`; `respond`, кламп-ветка и `respondItemFlow` переведены на `getHud`, предметы `count <= 0` отброшены. `get()` для компаратора/редстоуна остался с окном 1 тик, `FlowTracker.getLoss` и пин `second_pass_test.py` 0.3.104 не тронуты. `WrenchHud.flowLine` снова возвращает `null` при свежем подтверждённом нуле; `lastPositiveTick`/`itemFlowPositiveTick` дают `<?>` только типам с недавней подтверждённой передачей. Grace, частота 2 тика, лимит 24/20 и три строки подсказки сохранены. Версия **не поднималась** — `one_patch` внутри 0.3.337.

**Границы ECJ-набора.** `WrenchHud.java` не входит в ECJ-набор (клиентские API без полных стабов) и проверен tree-sitter/brackets/source-контрактами; `FlowTracker.java`, `ItemFlowTracker.java`, `PipeFlowNetwork.java` входят и откомпилированы. Все проверки — локальные Python/ECJ/stubs/source, **не** native Gradle/NeoForge/JAR, не Minecraft-клиент/сервер, не Mixins/GPU/TPS/5000 узлов, не GameTest «29/29», не игровая приёмка исправленного фильтра. `SKIP` не считается `PASS`; известные исторические FAIL не ослаблялись. [Хроника](HISTORY.md#v-0-3-337-accept), [CURRENT](CURRENT-STATE.md#hud-flow2), [снимок](archive/editions/0.3.337/README.md).

<a id="hud337-closed"></a>
## 0.3.337 — закрытие HUD-FLOW2 по игровой приёмке (05.11.2026)

**Основание:** «С кайфом по проверке в игре всё норм, закрываем? куда идём дальше? Что осталось в текущей задаче и в пакетах?» — прямая игровая приёмка автора, достаточная для закрытия по [DOCS_RULES](DOCS_RULES.md), как и в 334/335/336.

**Статусы.** HUD-FLOW2 → `**Статус: закрыто.** … Закрыто в 0.3.337. Удалить при 0.3.341.`; D-PORTMODE1 закрыт ранее в том же патче с той же парой. Обе записи перенесены в CURRENT в раздел «Принято и закрыто — краткое удержание»; активных незакрытых задач нет. HUD-NODE1/REACT-VAL1/REACT-VAL2 — до 0.3.339, CRIT-1/CRIT-2 — до 0.3.338; просроченные пометки «Удалить при 0.3.336» у PERF-001 и AUDIT-PACKAGES-332 оставлены без молчаливого удаления.

**Очередь.** Реестр не менялся: пять пакетов 332 (`D-PIPE2`, `D-SEC1`, `D-PERF12`, `D-CONTENT1`, `D-QA1`), ближайшая `D-PLOT1`, 88 некатегоризированных пунктов; перенос записи не разрешает реализацию. [DEFFERED](DEFFERED.md), [хроника](HISTORY.md#v-0-3-337-closed).

**Границы.** Код в коммите закрытия не менялся, полный локальный набор перезапущен: `run_all.py` `100 PASS, 0 SKIP, 0 FAIL / 100`, typecheck 64+168/0 ECJ, validate lang 1297/1297 0/0, doc_links `1881`/0/11, docs_structure 150/29/75/4173/69 (пара 337→341 проверена), snapshots 13/13, brackets 710, tree-sitter 710/0, `git diff --check` чист. Игровая приёмка **не** заменяет и не добавляет: native Gradle/NeoForge/JAR, Minecraft-клиент/сервер как агентский прогон, Mixins/GPU/TPS/5000 узлов, GameTest «29/29», количественный профиль кадров. `WrenchHud.java` не входит в ECJ-набор; `SKIP` не считается `PASS`; `D-AUD1`, `selfTest3FAIL`, неиспользуемый `G` в `third_grinder` не скрывались.
