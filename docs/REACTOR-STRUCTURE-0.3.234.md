# Реактор I поколения: структура и ресурсы 0.3.234

Дата: 16.10.2026. Это первый structural MVP: проверка кубической формы, сохранение bounds на панели, formed-визуал casing и диагностика по ПКМ. **Топливный, нейтронный, тепловой и GTH-циклы не реализованы.**

## Контракт формирования

| Проверка | Реализованное правило |
|---|---|
| Геометрия | Внешний габарит — куб со стороной 4–16 блоков включительно; casing — один блок толщиной. Bounds выводятся из связной оболочки от control panel. |
| Панель | Ровно одна `third_uranium_reactor_control_panel`; блок должен быть на boundary внешнего куба. Blockstate панели сохраняет шесть направлений, но GUI пока нет. |
| Shell | Разрешены `third_uranium_reactor_casing`, control panel и третьетирные `third_heat_node`, `third_universal_fluid_node`, `third_universal_node`. Любая другая shell-клетка отклоняет структуру. |
| Узлы | Нужно не менее одного `third_universal_node` **или одновременно** не менее одного `third_heat_node` и одного `third_universal_fluid_node`. Узлы находятся на внешнем слое. |
| Interior | Все внутренние клетки должны быть не-air и принадлежать whitelist: `third_uranium_reactor_fuel_sheath`, `graphite_block`, `boron_block`, `barium_block`, вода (`minecraft:water`), `third_lead_piston`, `third_sticky_lead_piston` или `third_lead_piston_head`. |
| Чанки | Проверка не force-load чанки. Если нужная часть поиска не загружена, проверка откладывается; панельный BE повторяет её при доступности чанков. |
| Обновление | Размещение/слом игроком повторно проверяет близкие панели, включая изменения внутреннего whitelist. Потеря формы снимает `formed` у casing. ПКМ по панели показывает результат, причину отказа либо ожидание загрузки чанков. |

Проверка не требует минимума чехлов/топлива, не считает слоты и не запускает реактор. Допустимое заполнение внутренней зоны пока является только block whitelist и условием отсутствия воздуха.

## Изменения кода

- `machines/reactor/ReactorStructure.java` — ограниченный поиск связной shell-компоненты, вычисление bounds, кубическая геометрия, whitelist, условия узлов, диагностика и установка/снятие formed-визуала.
- `machines/block/entity/ReactorControlPanelBlockEntity.java` — сохраняет formed-флаг и два bounds в NBT; откладывает revalidation, если части структуры находятся в незагруженных чанках.
- `core/block/ReactorControlPanelBlock.java` — six-way blockstate, BE ticker и сообщение со статусом по ПКМ.
- `core/block/ReactorCasingBlock.java` — blockstate `formed` и callbacks изменения shell.
- `core/block/ReactorFuelSheathBlock.java` — callbacks изменения внутреннего whitelist.
- `GonzoTechMod.java` — server listeners на размещение/слом блоков и очистку transient индекса при выключении сервера.
- `machines/client/ctm/TurbineSmartCtmBakedModel.java` — общий Smart CTM renderer теперь учитывает сформированную реакторную обшивку.
- `machines/registry/ModMachines.java`, `ModBlockEntities.java` — регистрация класса casing и контроллерной BlockEntity.

Bounds сохраняются только на панели; transient index содержит загруженные панели текущего мира. При загрузке формы bounds заново валидируются по актуальным блокам. Структура с незагруженной областью не объявляется заведомо сломанной. Этот механизм — только форма/визуал: никаких fuel slots, температуры, burnup, нейтронного переноса, портов или генерации GTH он не создаёт.

## Модели, blockstates и texture paths

| Registry ID / роль | Blockstate → block model | Texture path | Основа модели |
|---|---|---|---|
| `third_uranium_reactor_casing`, unformed | `blockstates/third_uranium_reactor_casing.json` → `models/block/third_uranium_reactor_casing.json` | `textures/block/third_1reactor/third_1reactor_casting.png` | `minecraft:block/cube_all`; одна собственная текстура на всех шести гранях |
| `third_uranium_reactor_casing`, formed | тот же blockstate, `formed=true` → `models/block/third_uranium_reactor_casing_ctm.json` | base `third_1reactor_casting_formed.png`; CTM `1reactor_ctm_line.png`, `1reactor_ctm_outer_corner.png`, `1reactor_ctm_inner_corner.png` | `gonzotech:smart_ctm`; filled base, line/border strips, inner/outer corners; CTM-соседство считается renderer'ом |
| `third_uranium_reactor_control_panel` | six-way blockstate → `models/block/third_uranium_reactor_control_panel.json` | `textures/block/third_1reactor/third_1reactor_control_panel.png` | `minecraft:block/cube_all`; отдельная текстура на все стороны |
| `third_uranium_reactor_fuel_sheath` | axis blockstate → `models/block/third_uranium_reactor_fuel_sheath.json` | `textures/block/third_1reactor/third_1reactor_fuel_sheath.png` | `minecraft:block/cube_all`; отдельная текстура на все стороны |

Три разрешённых узла shell тоже имеют индивидуальные текстуры на всех шести гранях через `models/block/node_cube.json`:

- `third_heat_node` → `textures/block/third/third_heat_node.png`
- `third_universal_fluid_node` → `textures/block/third/third_universal_fluid_node.png`
- `third_universal_node` → `textures/block/third/third_universal_node.png`

У всех трёх node texture paths собственные байтово-различные PNG. Остальные item definitions, blockstates, loot tables и переводы сверяются `audit/reactor_radiation_trace_test.py` и общим `audit/resource_texture_coverage_test.py`.

Fuel sheath использует минимальную прозрачную **16×16 RGBA8** PNG-заглушку. Это временный собственный asset, не итоговая графика.

## Дубликаты и прежние пути

Проверка SHA-256 всех **928** PNG в `assets/gonzotech/textures/` обнаружила **18 групп точных дубликатов**. В контексте реактора:

1. `third_1reactor/1reactor_ctm_inner_corner.png` и `third_1reactor/1reactor_ctm_outer_corner.png` байтово совпадают (SHA-256 `647721ab590a1ea9134c4a7830eda9f9e694717c3ae446d6c0d747da1817686c`). Эта пара также идентична двум corner PNG парогенератора. Оба reactor resource IDs оставлены отдельно: Smart CTM читает их как разные semantic slots, и каждый файл реально используется моделью.
2. `third_1reactor/1reactor_ctm_line.png` совпадает с `steamgen/steamgen_ctm_line.png`. Reactor-файл оставлен отдельным, поскольку является dedicated-путём набора реактора.
3. Дубликаты относятся к рисункам placeholder-уровня; удаление одного из них без изменения renderer/model paths было бы небезопасно. Пользовательские итоговые рисунки должны заменить их при художественной доработке.

Остальные 16 duplicate groups лежат в других семействах проекта: CTM corners fireclay/turbine, third-tier steam/water pipes, несколько environment sun variants, reused legacy GUI backgrounds, две пары filter classic/NEO, GUI-пары turbine/second pump и SteamGen/Stirling, noble moss block/slab/stairs и 56 одинаковых NEO red GUI placeholder copies. Они находятся вне реакторной задачи и в этом патче не удалялись: часть повторов требуется отдельными resource paths, часть выглядит как намеренный placeholder/reuse и требует решения автора/художника.

Старые имена `textures/block/first/uranium_reactor_control_panel.png`, `third_1reactor/casing.png`, `formed_casing.png`, `line.png`, `corner.png`, `border.png`, `casing_top.png`, `casing_bottom.png` не используются текущими моделями. Control-panel PNG был перемещён пользовательским commit `82ee30a`; в текущих reactor model JSON нет ссылок на прежние texture paths.

## Проверки

Добавлены/обновлены:

- `audit/reactor_radiation_trace_test.py` — новая панельная EntityBlock/BE/ПКМ-диагностика и RGBA PNG пути.
- `audit/reactor_structure_assets_test.py` — правила формы/узлов, уникальные model texture paths, шесть граней, blockstates, CTM slots и размеры PNG.
- `audit/resource_texture_coverage_test.py` — целостность моделей/ресурсных путей по всему assets namespace.

Результаты: `audit/run_all.py` — **44 PASS / 17 SKIP / 0 FAIL** (61 suite); `audit/validate.py` — **3213 JSON, 1155 EN/RU language entries, 0 ошибок/предупреждений**; `audit/resource_texture_coverage_test.py` — **587 item definitions, 284 blockstates, 765 models**, все model/texture paths разрешаются; `audit/doc_links.py` — **333 ссылки, 0 битых**. Java/Gradle-компиляция и in-game визуальная проверка не запускались: среда без JDK, а Minecraft runtime недоступен.
