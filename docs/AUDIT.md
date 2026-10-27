# Проверки проекта и история использования

**Последний полный локальный прогон, HUD-RAD-001 / 0.3.317: 80 PASS / 0 SKIP / 0 FAIL.** Среда и границы — в конце документа; прежние прогоны ниже сохраняются как история, не текущий blocker.

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
