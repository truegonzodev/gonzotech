# Контракты взаимодействия и интеграции

Срез кода: **0.3.311**, `c15083f`; документальная база: `42730c3`. Сверка/миграция: 27.10.2026.


Здесь capabilities — технические границы I/O, загрузки, сети и сторонних интеграций. Это не витрина возможностей мода и не общий backlog.

## Машины и транспорт

- Внутренние интерфейсы sinks/буферов GTU/GTH/жидкостей и Container/WorldlyContainer — разные API; не объявлять их все стандартными NeoForge capabilities.
- simulate не расходует материал/буфер и не переносит дозу. Commit повторно проверяет живой приёмник/вместимость, а не доверяет прошлому simulate.
- `PipeRouting`, `PipeFlowNetwork`, `ItemRouting` используют кэш/маршруты, но sink должен совпадать с текущим BE/типом/формой. Поломка/замена/разгрузка инвалидирует старое обещание.
- Виртуальные порты SteamGen/реактора не должны грузить чужие чанки. UNKNOWN не означает пустой порт. Обратный heat-путь в тот же реактор исключается.
- ItemRouting: live-проверка цепей/приёмников перед вставкой; усечённый BFS не сохраняется как полный маршрут. Оптимизация сохраняет порядок/политику, не меняет throughput/balance.
- IItemHandler, InvWrapper, воронки и custom handlers требуют правильной семантики insert/extract/остатка. `setItem` — замена. Остаток/rollback — реальный материал, не реконструкция по одному id/count.

## Радиационные адаптеры

`ExternalRadiation` не сериализуется автоматически произвольным ItemStack codec. Поддержаны явные owner adapters и стандартные transfer hooks; нестандартный non-Container IItemHandler/модовый serializer может потребовать собственного адаптера. Реальный список и ограничения — [RAD_ITEMS](RAD_ITEMS.md). Отсутствие универсальной поддержки не скрывается под словом «capability».

## WorldGuard и регионы

WorldGuard — внешняя server-side система Paper/Spigot, в NeoForge-среде нужен соответствующий bridge/источник. `WorldGuardFlags` выполняет прямой опрос доступного источника; при его отсутствии действует fail-open, событийная проверка — запасной канал. Не обещать региональную защиту standalone-серверу без интеграции. Для построек/добычи проверяется клетка и реальный результат операции, не только наличие события.

## Сетевые границы

Сервер авторитетен по инвентарю/прогрессии/избранному/дозе. Клиентские панели рисуют snapshots; fence не клиентский setter радиоактивности. Payload нужно проверять по отправителю, menu/state, индексу и границам; throttling не заменяет валидацию. Logout очищает session caches.

## История

0.3.58–97: потери/маршруты и оптимизация; 0.3.290–306: регионы и live sinks; 0.3.307: виртуальный порт/границы загруженности; 0.3.311: item transfer/owner adapters. Конкретные события — [HISTORY](HISTORY.md); источники проверок — [AUDIT](AUDIT.md).

## Владельцы связанных контрактов

Конкретные Sinks/receive*, классы буферов и реестр BE — [MACHINES](MACHINES.md). Root/геометрия/виртуальные порты и lifecycle — [MULTIBLOCK](MULTIBLOCK.md). Внешняя совместимость JEI/Sodium/Lithium/Simple Fog Control, риски shader/worldgen и отсутствие TerraBlender — [INTEGRATIONS](INTEGRATIONS.md). Internal Sinks не означают поддержку сторонней energy-capability.

0.3.311, DOC-002: внутренние границы отделены от реестра внешней совместимости; код не менялся.

## Единый радиационный проход — 0.3.314

`RadiationCluster` обслуживает физические инвентари через адаптеры `RadiationOwners`: Container BE/entity, PetBowl, ItemEntity, ItemFrame, LivingEntity equipment; игрок получает отдельные 41 слотов без меню/курcора/ender. Соединённый сундук объединяется без chunk force-load. Вложенные CONTAINER/BUNDLE_CONTENTS и cargo носителей участвуют в общем расчёте с глубиной 4. Математика O(S), максимум из предварительного снимка, никаких записей count/components. Это не автоматическая поддержка произвольного IItemHandler-only хранилища; сохранение и извлечение реальных слотов требуют адаптера. Каденс/экраны/исключения — [RAD_ITEMS](RAD_ITEMS.md).

При стандартном ItemStack.copy вложенные CONTAINER/BUNDLE_CONTENTS отделяются в новой копии до глубины 4 через RadiationCopies; значения item/components/count сохраняются, динамическая доза не сериализуется в них. Это предотвращает разделяемую наведёнку разных копий заполненного хранилища. Произвольные сторонние конструкторы/пересоздания по-прежнему требуют адаптера.


## Пучки и mastery — 0.3.315

В PipeBlock, UniversalFluidPipeBlock и CompositePipeBlock совместимая труба в руке добавляется в пучок раньше неявного ключа от mastery. Сохраняются проверки тира, занятого угла/типа, исключение узлов, серверный расход одного предмета и режимы существующих частей. При отсутствии подходящего добавления остаётся смена режима ключом/mastery; приседание без ключа поворачивает слой пучка, в том числе с пустой рукой. Интерфейс сформированной турбины сохраняет прежний приоритет.

CompositePipeBlock.SHAPE_CACHE общий для существующих тиров: ключ — presence пяти типов и AXIS/AXIS_LOWER, до 288 форм. MODE и WATERLOGGED не входят в ключ, поскольку не меняют геометрию. Новые типы пучков обязаны сохранять этот принцип; геометрически разные разновидности требуют отдельного признака ключа/кэша. Правило закреплено в RULES.

## Compile/wiring-проверки 0.3.315 — D-AUD1

Typecheck снова исполняется: 52 реальных файла + 131 compile-only boundary, без исключения прежних real sources; включён настоящий ProcessingRecipeDisplay. PipeLoss: 26 поведенческих проверок PASS; direct Lane с destination identity по-прежнему path=null/loss=0, diagnostic observer не заменяет расчёт потерь. Эти стабы не доказывают совместимость с произвольным MC/модом; runtime/I/O-параметры не менялись. Область и ограничения — AUDIT.

## Новые III-энергохранилища (0.3.316)

ThirdEnergyStorageBlockEntity реализует внутренний GtuSink, **не FE и не NeoForge energy capability**. Third accumulator: capacity230882000, intake/output294000, loss32 mGTU/t; battery: capacity68088000, intake244000/output188000, loss0. Новые intake/output — суммарный budget за gameTime независимо от числа вызовов, направлений и проводных lane; simulate полностью read-only. Выдача идёт через существующий PipeRouting WIRE и списывает его sender-paid сумму вместе с потерей пути. Старые I/II не перебалансированы; добавлена только cross-tier защита equalization с новыми хранилищами.

Серверный NBT EnergyMilli / переносимый CUSTOM_DATA GonzoBatteryGtuMilli — long, точные milli. Клиентские три ContainerData limbs ≤999, независимые SimpleContainerData; нельзя clamp промежуточного packet к capacity и тем самым потерять carry. Три слота аккумулятора — plain Container, без CarrierItem-unpack и без charging capability. Предмет батареи пассивен/nonstackable; сохранение charge — только обогащение обычного loot drop, без двойного выброса. MC loot lifecycle/API и клиентская сеть пока не проверены игрой; headless accounting/пины — AUDIT.

## Прямое облучение и визуальные layers — 0.3.317

Containment.insideDose(level, eyePos, observerPlayer) — read-only суммарный источник5%/с от той же замкнутой полости. Collector работает по loaded getChunkNow/physical BE/entity inventories, не по меню; один inspect с identity-dedup, без source tick/mutation. Собственный inventory наблюдателя и удалённые ender inventories не добавляются. Full total не заменяется chunkTarget. Новая доза не проходит через F, но все прежние выходы в чанк проходят; это два разных пути, не отмена защиты контура. Потоки/owner persistence unchanged; подробности RAD_WORLD/RAD_ITEMS.

Приборный HUD теперь registered GUI layer над CHAT на client mod bus, не RenderGuiEvent.Post на root depth. Общие мировые/серверные контракты приборов не меняются. Source API NeoForge1.21.4 проверено, boundary typecheck не означает MC/GPU integration proof.

## Входящий фон и свинцовый владелец — 0.3.318

RadiationCluster.Policy.NORMAL/LEAD используют прежний коэффициент 1/0,2 и для входа чанк→предметы. Сначала фон ×коэффициент владельца, затем строгий порог >30 mZt/s; свинцу нужен внешний фон >150 mZt/s. Вложения экранируются один раз; trace сообщает фактически принятый фон. Это не F контура: он остаётся односторонним и не защищает содержимое от уже заражённого чанка. Прямой путь 5%→игрок и все исходящие формулы сохранены. Изменение не вводит capability, нового storage/коэффициента или нового радиационного закона. Native MC integration/игровая приёмка не выполнены; ядро и граничные случаи проверены headless.
