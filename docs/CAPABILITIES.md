# Контракты взаимодействия и интеграции

Срез кода: **0.3.311**, `c15083f`; документальная база: `42730c3`. Сверка/миграция: 27.10.2026.


Здесь capabilities — технические границы I/O, загрузки, сети и сторонних интеграций. Это не витрина возможностей мода и не общий backlog.

## Машины и транспорт

- Внутренние интерфейсы sinks/буферов GTU/GTH/жидкостей и Container/WorldlyContainer — разные API; не объявлять их все стандартными NeoForge capabilities.
- simulate не расходует материал/буфер и не переносит дозу. Commit повторно проверяет живой приёмник/вместимость, а не доверяет прошлому simulate.
- `PipeRouting`, `PipeFlowNetwork`, `ItemRouting` используют кэш/маршруты, но sink должен совпадать с текущим BE/типом/формой. Поломка/замена/разгрузка инвалидирует старое обещание.
- Виртуальные порты SteamGen/реактора не должны грузить чужие чанки. UNKNOWN не означает пустой порт. Обратный heat-путь в тот же реактор исключается.
- ItemRouting: TTL-hit в322 требует совпадения версии **и** возраста≤40тиков; пустой кэш тоже истекает без block edit, world edit сразу делает hit непригодным. При исчерпанном BFS-бюджете сохраняется прежний fallback до доступного пересчёта; полнота/справедливость большой сети ещё не гарантирована. Wrapper identity двойного сундука, sentinel усечения, стоимость path validation и cache lifecycle остаются PERF-001/1/3/13/14. Live-проверка перед commit сохранена; throughput/balance и loaded-политика не менялись.
- IItemHandler, InvWrapper, воронки и custom handlers требуют правильной семантики insert/extract/остатка. `setItem` — замена. Остаток/rollback — реальный материал, не реконструкция по одному id/count.

## Радиационные адаптеры

`ExternalRadiation` не сериализуется автоматически произвольным ItemStack codec. Поддержаны явные owner adapters и стандартные transfer hooks; нестандартный non-Container IItemHandler/модовый serializer может потребовать собственного адаптера. Реальный список и ограничения — [RAD_ITEMS](RAD_ITEMS.md). Отсутствие универсальной поддержки не скрывается под словом «capability».

## WorldGuard и регионы

WorldGuard — внешняя server-side система Paper/Spigot; NeoForge требует совместимого bridge/провайдера мира. Baseline320 WorldGuardFlags не является проверенным адаптером стандартного WG7: неверны world type/методы/политика флагов, возможен actor fallback. PERF-001/9 активна: машина без владельца проверяет клетку и все три требуемых разрешения; mapping/политика отсутствия и неисправности bridge ещё согласуются. Наличие WG само по себе не доказывает защиту; до исправления не обещать работоспособность стандартной интеграции.

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

## PERF-001 — границы нового лечения

Endpoint identity и lifecycle должны учитывать double-wrapper/две половины, entity и заменённый BE; текущая долгоживущая Container-ссылка в WeakHashMap value может удерживать world. Автор разрешил только endpoint-chunk загрузку ItemRouting (не фильтр/сепаратор и не транзитную цепь); механизм определения unloaded endpoint/границы tickets ещё не выбран. Complete/visited/result и продолжение поиска — требование3; shared path validation/lifecycle addresses — предложения13/14. Throttle Fence обязан сохранять допустимый end/ACK и provenance. На этом документальном шаге ни одна runtime-гарантия не объявляется исправленной.

## PERF-001/7/8 — приём I/II после321

ReceiveGtu обоих старых аккумуляторов имеет per-owner/gameTime aggregate allowance, не на вызов/сторону. Simulation не расходует allowance и не меняет clock; commit списывает accepted, а не requested. I явно распознаёт II до общего GtuSink и применяет прежний half-positive-difference по абсолютным milli. NBT/числа/GTU API не меняются, III-ядро не переписывается. **Автор подтвердил в игре7/8: «Аккумуляторы - закрыто, всё работает» и «Правильный кэп BE max input/t тоже работает».** Пункты закрыты/архивированы, headless-границы прежних методов остаются отдельно от этой авторской приёмки.

### Ответы по границам PERF-001/5/6/9

5: разрешён только непосредственный соседний endpoint через границу загруженного чанка трубы, не транзитная цепь; filter/separator не endpoints. Механизм bounded unloaded lookup/подтверждения и lifecycle ещё исследуется, наивный BFS getBlockState не считается лечением. 6: operation-scoped10сек тихогоКД после завершения + дедуп/одинjob/cache, Stop/end приоритетны; budgets отдельно. 9: optional-strict policy автора описана в INTEGRATIONS, bridge выбирают создатели серверов. В321этот блок ещё не реализован.

## PERF-001/4 — scope небольшого исправления322

Пригодность топологического cache hit требует version match AND age≤40; контейнер-сущность может приехать без block-edit stamp, версия не заменяет TTL. После age40при доступном budget — новый BFS. LiveSinks перед commit остаётся обязательным: положительный cache hit не разрешает использовать уехавшую сущность/сломанный путь. **Автор подтвердил в игре4: «Вагонетки - закрыто, идеально».** Запись закрыта/архивирована. Chunk loading5, stable double-chest identity1, resumable BFS3 и path-stamps13 этим не исправлены; жёсткое время discovery на насыщенной сети не обещается. [Игровая приёмка и архив4/7/8](archive/editions/0.3.322/README.md).

**Разные источники ограничений (напоминание автора28.10.2026):** пропускная способность каждого провода/пути и max input/t приёмного BE — разные ограничения. Два независимых провода с разных сторон по38GTU/t могут дать аккумуляторуIII суммарно76GTU/t, если его собственный input/t и свободная ёмкость позволяют. Если оставшийся суммарный лимит приёмника ниже, он принимает только допустимое; второй источник не создаёт второй BE allowance. Это не требование ограничить весь BE скоростью одного провода, не новый баланс и не повод менять исправный runtime. Учитываются также прежние ограничения источника и потери пути.

## Одноштучные слоты — PERF-001/2,0.3.323

SlotCapacity — внутренний контракт admission, не NeoForge capability. limit=минимум container max и slot capacity; уПлотника A/B1, остальные слоты с прежним лимитом, уSeismicSource TNT1. ItemRouting проверяет лимит до записи/merge. InvWrapper.getSlotLimit и SidedInvWrapper.getSlotLimit дополняются условными mixin hooks только для SlotCapacity; sided index переводится в физический индекс через getSlot, отрицательный/неизвестный индекс даёт0. Посторонние containers/special cases wrapper не переписываются, extraction permissions прежние.

HopperSlotCapacityMixin оборачивает tryMoveInItem через MixinExtras WrapMethod: при ограниченной вместимости оригинальный метод получает только допустимый offer; delegate вызывается один раз, непринятый остаток остаётся в возвращаемом stack. Путь обычного Container с валидным offer и обычным общим max остаётся прежним; globally-one containers тоже соблюдают свой max. Существующий radiation redirect/оригинальные проверки грани, типа/живого контейнера и cooldown не заменяются, радиационный закон не менялся. Новые вставки не используют setItem-обрезку/наземный сброс. ItemStack copies сохраняют прежние компоненты и проходят существующие copy hooks.

Raw setItem/setStackInSlot — replacement API, не bulk insert с остатком: новая запись>1в A/B/TNT вызывает явную IllegalArgumentException до изменения. Старый legacy stack той же ссылки может обновляться без миграции; прямое мутирование ItemStack/NBT сторонним кодом не является поддержанным insertion protocol. Это не обещание защиты от произвольного обхода API. TNT loadSurplus recovery старых NBT оставлен отдельно от нового приёма.

Проверено headless354assertions/10mutants на production methods и upstream wrapper bodies; Mixin hook wiring вручную, не runtime bootstrap. Требуется игра/запуск автора, пункт2не закрыт. Расхождение комментария про карты внеA/B вынесено вCURRENT, политика товарного склада не менялась.

Новые смешанные контейнеры с единичным слотом должны объявлять его серверный SlotCapacity; один GUI Slot.getMaxStackSize=1 недостаточен. Глобальный container max нельзя уменьшать до1для ограничения только одного слота.
