# Контракты взаимодействия и интеграции

Актуализировано на runtime **0.3.332**,01.11.2026. Документальная база миграции:0.3.311/42730c3; действующие I/O-контракты ниже.


Здесь capabilities — технические границы I/O, загрузки, сети и сторонних интеграций. Это не витрина возможностей мода и не общий backlog.

## Машины и транспорт

- Внутренние интерфейсы sinks/буферов GTU/GTH/жидкостей и Container/WorldlyContainer — разные API; не объявлять их все стандартными NeoForge capabilities.
- simulate не расходует материал/буфер и не переносит дозу. Commit повторно проверяет живой приёмник/вместимость, а не доверяет прошлому simulate.
- `PipeRouting`, `PipeFlowNetwork`, `ItemRouting` используют кэш/маршруты, но sink должен совпадать с текущим BE/типом/формой. Поломка/замена/разгрузка инвалидирует старое обещание.
- Виртуальные порты SteamGen/реактора не должны грузить чужие чанки. UNKNOWN не означает пустой порт. Обратный heat-путь в тот же реактор исключается.
- ItemRouting: TTL-hit в322 требует совпадения версии **и** возраста≤40тиков; пустой кэш тоже истекает без block edit, world edit сразу делает hit непригодным. При исчерпанном BFS-бюджете сохраняется прежний fallback до доступного пересчёта; полнота/справедливость большой сети ещё не гарантирована. Повторные обёртки двойного сундука сверяются по физическим половинам;327 разрешает независимые входы/транзит с DAG-защитой; 1 закрыт по авторской приёмке328; sentinel/count/completion исправлены328; resumable3 отложен вD-PIPE2;pathvalidation13/cachelifecycle14 отложены вD-PIPE2. Live-проверка перед commit сохранена; throughput/balance не менялись; loaded-only границы добавлены в325(ниже).
- IItemHandler, InvWrapper, воронки и custom handlers требуют правильной семантики insert/extract/остатка. `setItem` — замена. Остаток/rollback — реальный материал, не реконструкция по одному id/count.

## Радиационные адаптеры

`ExternalRadiation` не сериализуется автоматически произвольным ItemStack codec. Поддержаны явные owner adapters и стандартные transfer hooks; нестандартный non-Container IItemHandler/модовый serializer может потребовать собственного адаптера. Реальный список и ограничения — [RAD_ITEMS](RAD_ITEMS.md). Отсутствие универсальной поддержки не скрывается под словом «capability».

## WorldGuard и регионы

WorldGuard — внешняя server-side система Bukkit/Paper, NeoForge требует совместимого hybrid bridge. В332 вместо прежних выдуманных API/алиасов реализованы optional `WorldGuardFlags`/`BukkitWorldGuardAdapter`/`PlotnickRegionPolicy`. Конкретную платформу выбирает создатель сервера; target WG7.0.13/WE7.4.2 либо подходящая7.4.x, а не гарантия всех сборок.

Только клетка предстоящей правки, никогда положение корпуса Плотника/полный сектор. Все его правки и боковой всос руды проходят одну ownerless-политику. Для **любой** правки нужны оба разрешения `block-place` и `block-break`: каждое — явный ALLOW либо, при UNSET, fallback от `build=ALLOW`. Любой явный DENY place/break запрещает всё; `build=DENY` — безусловный машинный veto. `pistons` обязан быть **явно ALLOW**; UNSET запрещает, хотя стандартный default WG равен ALLOW. Полное отсутствие этих флагов внутри региона не разрешает работу. Приоритеты, одинаковые priority, родители и region groups разрешаются движком WG; субъект всегда NON_MEMBER, без владельца/ближайшего игрока.

`__global__` считается регионом всего мира, не «вне регионов». По отдельному решению автора явный глобальный `build=ALLOW` **может дать fallback**, в отличие от legacy-правила самого WG. Исключение учитывает NON_MEMBER-группу и native priority/ignored-parent/implicit-build barriers: не перепрыгивает локальный build-барьер и не отменяет DENY/прочие флаги. Остальные значения разрешает WG.

Без WG обычный событийный путь NeoForge сохранён; отсутствие детектируется один раз на lifecycle сервера, без новых region/world scans на клетку. Вне регионов разрешено. Известный установленный WG с disabled/missing WE/WG, неправильным API/classloader, несовпавшим world handle либо недоступными/virtual region data — отказ + bounded-лог, не null/fail-open/nearby actor. Кэш manager/решения по dimension-string удалён; manager и флаги берутся живыми. Слабый identity-world mapping очищается при world unload, sessions — при server stop/start; присутствующий plugin проверяется на enable/смену объекта при попытке. После установки плагина, которого не было при запуске, требуется полный restart.

1326headlessassertions/9mutants/exact331negative и100000absentAPIcalls используют реальные production paths + pinned WG engine в отдельном plugin loader; host APIs/геометрия/Guava — doubles. Не native bootstrap/full BE/game acceptance. №9 закрыт по последующему прямому решению автора; native-сервера не было, отдельной активной приёмки не оставлено. [Контракт/проверки сервера](INTEGRATIONS.md#perf-001-wg332), [профиль](AUTOMINER.md#perf-001-wg332).

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

Endpoint identity и lifecycle должны учитывать double-wrapper/две половины, entity и заменённый BE; текущая долгоживущая Container-ссылка в WeakHashMap value может удерживать world. Автор отменил прежнее adjacent-endpoint loading: теперь вся предметная логистика loaded-only, без probes/tickets/known-endpoint loaders, включаяfilter/отсеиватель. Complete/visited/result разделены328; продолжение поиска — отложеннаяD-PIPE2(остаток3); shared path validation/lifecycle addresses — предложения13/14. Throttle Fence обязан сохранять допустимый end/ACK и provenance. 5принят автором по выгрузке/стопу/возобновлению;1 частично исправлен326, игра327 была отрицательной,328 принят автором, заявленный отказ1 принят как исправленный в328; logged BFS starvation исправлен328,остаток3 отложен вD-PIPE2;6/10 закрыты по игре330;9 закрыт решением автора без native-прогона;12 отложен отдельно,13/14 вD-PIPE2.

## PERF-001/7/8 — приём I/II после321

ReceiveGtu обоих старых аккумуляторов имеет per-owner/gameTime aggregate allowance, не на вызов/сторону. Simulation не расходует allowance и не меняет clock; commit списывает accepted, а не requested. I явно распознаёт II до общего GtuSink и применяет прежний half-positive-difference по абсолютным milli. NBT/числа/GTU API не меняются, III-ядро не переписывается. **Автор подтвердил в игре7/8: «Аккумуляторы - закрыто, всё работает» и «Правильный кэп BE max input/t тоже работает».** Пункты закрыты/архивированы, headless-границы прежних методов остаются отдельно от этой авторской приёмки.

### Ответы по границам PERF-001/5/6/9

5: последнее решение — никакой загрузки чанков предметными трубами/фильтром/отсеивателем, даже непосредственных endpoints. В325добавлены loaded-checks root/source/BFS/path/endpoint; vanilla double-chest lookup допускается только при загруженных обеих половинах. 6: operation-scoped10сек тихогоКД после завершения + дедуп/одинjob/cache, Stop немедленный, допустимый end внутри ingressпорога не имеет отдельногоthrottle; общий бюджет330 описан ниже. 9: optional-strict policy автора описана в INTEGRATIONS, bridge выбирают создатели серверов. 6дополнен330 глобальным допуском двух цельных расчётов кнопок/GUI и flood-disconnect;6/10 приняты автором по игре330 и закрыты в331;9реализован332/закрыт решением автора без native-прогона;5принят автором,1принят отдельно на328.

## PERF-001/4 — scope небольшого исправления322

Пригодность топологического cache hit требует version match AND age≤40; контейнер-сущность может приехать без block-edit stamp, версия не заменяет TTL. После age40при доступном budget — новый BFS. LiveSinks перед commit остаётся обязательным: положительный cache hit не разрешает использовать уехавшую сущность/сломанный путь. **Автор подтвердил в игре4: «Вагонетки - закрыто, идеально».** Запись закрыта/архивирована. Chunk loading5, stable double-chest identity1, resumable BFS3 и path-stamps13 этим не исправлены; жёсткое время discovery на насыщенной сети не обещается. [Игровая приёмка и архив4/7/8](archive/editions/0.3.322/README.md).

**Разные источники ограничений (напоминание автора28.10.2026):** пропускная способность каждого провода/пути и max input/t приёмного BE — разные ограничения. Два независимых провода с разных сторон по38GTU/t могут дать аккумуляторуIII суммарно76GTU/t, если его собственный input/t и свободная ёмкость позволяют. Если оставшийся суммарный лимит приёмника ниже, он принимает только допустимое; второй источник не создаёт второй BE allowance. Это не требование ограничить весь BE скоростью одного провода, не новый баланс и не повод менять исправный runtime. Учитываются также прежние ограничения источника и потери пути.

## Одноштучные слоты — PERF-001/2,0.3.323

SlotCapacity — внутренний контракт admission, не NeoForge capability. limit=минимум container max и slot capacity; уПлотника A/B1, остальные слоты с прежним лимитом, уSeismicSource TNT1. ItemRouting проверяет лимит до записи/merge. InvWrapper.getSlotLimit и SidedInvWrapper.getSlotLimit дополняются условными mixin hooks только для SlotCapacity; sided index переводится в физический индекс через getSlot, отрицательный/неизвестный индекс даёт0. Посторонние containers/special cases wrapper не переписываются, extraction permissions прежние.

HopperSlotCapacityMixin оборачивает tryMoveInItem через MixinExtras WrapMethod: при ограниченной вместимости оригинальный метод получает только допустимый offer; delegate вызывается один раз, непринятый остаток остаётся в возвращаемом stack. Путь обычного Container с валидным offer и обычным общим max остаётся прежним; globally-one containers тоже соблюдают свой max. Существующий radiation redirect/оригинальные проверки грани, типа/живого контейнера и cooldown не заменяются, радиационный закон не менялся. Новые вставки не используют setItem-обрезку/наземный сброс. ItemStack copies сохраняют прежние компоненты и проходят существующие copy hooks.

Raw setItem/setStackInSlot — replacement API, не bulk insert с остатком: новая запись>1в A/B/TNT вызывает явную IllegalArgumentException до изменения. Старый legacy stack той же ссылки может обновляться без миграции; прямое мутирование ItemStack/NBT сторонним кодом не является поддержанным insertion protocol. Это не обещание защиты от произвольного обхода API. TNT loadSurplus recovery старых NBT оставлен отдельно от нового приёма.

Пункт2подтверждён автором в игре30.10.2026 и [архивирован](archive/editions/0.3.323/README.md). Прежние354headlessassertions/10mutants и ручной Mixinwiring остаются отдельным evidence, не выдаются за native запуск агентом. Автор снял вопрос карт внеA/B: разрешён обычный товарный склад, функционал только вA/B; запрет по неверному комментарию не вводится.

Новые смешанные контейнеры с единичным слотом должны объявлять его серверный SlotCapacity; один GUI Slot.getMaxStackSize=1 недостаточен. Глобальный container max нельзя уменьшать до1для ограничения только одного слота.

## Virtual Container replacement —PERF-001/11,0.3.324

Plotnick.setVirtual — проверка входного replacement и запись только указанного virtual slot, затем dirty/stats; empty→empty иinvalidindex не трогают состояние. Скрытые возвраты прежнего типа/overflow через insert/dump удалены. SlotCapacity.requireReplacement проверяет новую отдельную стопку до изменения; не усечение/перенос в другую ячейку. Старый legacyin-place stack не мигрируется.

Setter не владеет прежним содержимым: при намеренном сохранении старого caller получает его через явный removeItem/removeItemNoUpdate, затем отдельно возвращает/вставляет либо делает rollbackсвоего snapshot. ItemRouting/NeoForgehandlers вставляют по своим admission/merge правилам и не рассчитывают на побочный возврат изsetter. Единственный private caller setVirtual — публичныйsetItemvirtualbranch; GUI виртуаль не показывает. **Пункт11принят автором31.10.2026 и [архивирован](archive/editions/0.3.324/README.md).** Прежние9363assertions/8mutations, original323не проходит — отдельное headless evidence; native запуск агентом не заявляется.

## Двойной сундук — physical identity и история переносов,0.3.327

На326 ordinary→double был принят, но cold double→ordinary и double→double ещё отказывали, в том числе без входящего потока и с правильными концами. После BFSисправления328 автор подтвердил штатную работу и разрешил закрыть пункт1. Полные54слота/порядок прежние. Выбор автора327 — разрешить несколько входов и same-tick forward, заменить blanket source freeze защитой от обратных/циклических переносов.

`sameContainerAt`: обычныйContainer/entity требует точного`live==expected`. Для разныхCompoundContainer обе разные текущиеContainerBE должны входить **в обе**обёртки черезpublic`contains`; companion должен быть загружен. Replacementnear/far/both/split/re-pair отвергают savedsource/target; адрес/размер не авторизация. Source проверяется повторно после discovery, `sameInventory` исключает alias у pipes/filter. Нет перестановки слотов/NBT/нового кэша физических владельцев; loaded/path/TTL40/BFSbudget сохранены.

`ItemTransferGuard`: WeakHashMap<Level,guard>, value только граф примитивных owner keys (block position/entity UUID bits), без world/container/stack refs. Per-gameTime история успешных source→target edges; compound даёт обе текущие физические половины, разрешённые только после live BOTH-owner проверки. Пересечение owner sets и достижимость target→source запрещают self/reverse/длинный цикл. Повторный прямой edge/несколько независимых входов/same-tick forward разрешены, receiver не монополизируется первым входом. Смена тика очищает граф/denial memo. Запись только после успешной вставки и списания; filter использует captured `TransferAttempt` той же истории. Guard только veto, не замена loaded/identity/path checks. История консервативно относится ко всему inventory, не отдельно к виду предмета; scheduler не обязан обеспечить транзит за один тик. Это не исправление остальных cache/lifecycle14 или BFS3/path13.

Диагностика `pipeRoutingDiagnostics` defaultfalse: item-стадии раз в20тиков, source/classes/physical owners/cache budget/BFS/live sinks/cycle/result; off-path без дополнительных диагностических world reads. Нативный холодный source отказ требует серверного лога, не объясняется снятым source freeze. Полный checklist/config ограничения — [CURRENT](CURRENT-STATE.md).

Wholeproduction regression: parallel4321assertions/9assertion-mutants/exact326negative; double7498/7mutants/exact325negative, loaded399/23mutants. Redundant BOTH-owner membership проверки при unsafe мутациях отключаются вместе; старые blanket-shield мутации заменены согласно новому контракту. Модельные cold/warm controls проходят, но это **не native MC**. Базовый suite327 был88PASS; актуальный полный331 —94PASS,1принят автором328,5принят ранее. [Происхождение](HISTORY.md#perf-001-transfer-dag), [предыдущий контракт](archive/editions/0.3.326/README.md#before-transfer-dag).

## Предметная loaded-only граница —PERF-001/5,0.3.325

Предметная логистика не загружает чанки: ни транзит, ни источник/приёмник, ни endpoint ранее известного маршрута. Нет probes/tickets/кэша повторной загрузки/собственного player-distance algorithm. Уже законно загруженные spawn/forceload/другим игроком чанки остаются обычной частью мира; остановка зависит от serverhasChunkAt, не видимости GUI.

ItemRouting: loaded-check root/source; до каждого BFSstate read проверяется chunk queuednode/соседа; перед push source проверяется снова, cached sink требует весь loadedpath/lastpipe и живой endpoint. SharedcontainerAt проверяет адрес и для ChestBlock.TYPE≠SINGLE companion черезgetConnectedDirection до vanillaHopperlookup, чтобы combiner не прочитал выгруженную половину. Без обеих половин операция ждёт, ничего не списывает.

ItemFilterRouting: все source/pass/reject lookups идут черезтотжеcontainerAt; loadedroot/frontier/queuednode доstate read, root+path+endpoint перед insertOne. Sink хранит только дополнительные координатыroot/цели для loaded-проверки; native owneridentity/полнаяпроверка топологии13не переделаны. Выгруженный poweredscavenger не разрешает deletion; загруженныйIIсохраняет прежнюю intentionaldeletion/защитуwaste/бюджеты веток. Недоставленное остаётся у источника/в буфереfilter. Нормальная loadedлогистика не меняет лимиты, компоненты, радиационные sidecars илиNBT.

После внешней загрузки маршрут открывается через прежнюю инвалидацию/TTL40и доступныйBFSbudget; новые bounded/resumable/SLA3не обещаны. Wholeproduction boundaryharness+exactoldnegative+23mutants —не nativeMC/GameTest/TPSдоказательство.5закрыт по авторскому подтверждению выгрузки/стопа/возобновления на325; identity1 частично исправлен326; после отрицательной игры327 автор принял328, 1 закрыт автором328.остаток3 отложен вD-PIPE2;13/14остаются открыты.

**Косвенный redstone-путь5, дополнительное решение автора:** неизвестное питаниеII — недоступность всегоendpoint, неOFF. ItemFilterRouting проверяет `canCheckPower` до постановки scavengerвсписок; уIдостаточен собственныйloadedchunk (нетredstone), уII`LoadedItemChunks.redstoneAreaLoaded` требует консервативный квадрат±3блока поX/Z. Он покрывает vanillaadjacentsignal/conductor/directsignal/wireconnection; fourcornerhasChunkAt нечитаетблоки и не грузит чанки. Это фиксированная окрестность чтения redstone, не player-distance rule. При недоступностиIIнетdeletion и нетfallbackrejectrouting; другие доступные endpoints могут работать.

UniversalNodeComparator передvanillaupdateNeighbourForOutputSignal проверяет ownchunk/тужечитаемуюокрестность: vanillaможетчесть второйблок заconductorи соседнийinputкомпаратора. Unsafeуведомление откладывается **до** записиLAST_SIGNALS, чтобы после внешнейзагрузки повторитьpublication. signal()/лимиты/перенос/числоактивныхпотоков неизменны. Это не обещание переделать сторонние произвольные moddedredstonecallbacks; обычная vanillaграница проверяется headless, nativeприёмка ещё требуется.

## Независимые item-пути и BFS budget —0.3.328

Автор подтвердил: закрытие одной линии «вентилем» включает другую; это не ошибка настройки. На327 не работают одновременно2sources→1receiver, A→B→C и две **отдельные** линии между одними inventory (не split с общим входом). Native ITEM-DIAG показал: complete обход2труб обнуляет2048budget, последующие source-ready54 получают deferred/fallback0; при доступном budget выход переносит1. Физические BOTH owners в этих записях совпадают.

Исправление 328 устранило ошибочное списание всего бюджета за complete-обход двух труб. В 338 этот механизм заменён сохраняемым поиском: незавершённая очередь продолжается, неполные приёмники не публикуются, глобальный worldVersion и лимит достижимости 2048 удалены. TTL 40 относится к динамическим предметным получателям; identity/DAG/alias и баланс переноса сохраняются. [Действующий контракт](#pipe338), [исходная редакция](archive/editions/0.3.338/README.md).

Независимые линии сохраняют собственные allowance; первый успешный input не блокирует остальные входы/выходы receiver. Разные API GTU/GTH sinks и item Container не смешиваются, новый item interface/фазовый планировщик/virtual batching **не вводятся**: автор отказался от переделки исправного переноса, оценка20–40% осталась идеей без измерения/разрешения. Суммы, вместимость и лимиты прежние; один общий вход/сегмент не превращается в несколько allowance из-за ветвления.

Wholeproduction340assertions/5mutants, exact327negative (Git/hash/bytes), two-node inputs/transit/parallel axes на обоих double контактах иcaps1/2/порядки/cache/budget0–3/partial cache. Старые проверки сохраняются, полныйsuite89PASS.1 принят автором в игре328; в показанных source-ready попытках причина локализована вBFS, **все** отдельные cold-source варианты этим не доказаны. Не native MC/Gradle/GameTests/TPS. Предыдущий раздел327 описывает сохранённую identity/DAG основу; его прежний статус уточнён этим свидетельством. [Текущий статус](CURRENT-STATE.md), [история](HISTORY.md#perf-001-two-node-bfs).

## Авторская приёмка328 и следующий этап

01.11.2026 автор: «окей теперь всё работает штатно», после обсуждения ограничений — «да окей закрываем». **Пункт1 закрыт** по этой игре; не перечислять неописанные автором отдельные restart/compatibility тесты как выполненные. Лимиты,54слота,перенос/sidecars/loaded-only/physical identity/DAG прежние. Отдельная защита отциклов ограничена текущимтиком и inventoryowners, не itemвидом; A→B→C допускается в одномтике, но зависит от порядка вызовов. Межтиковое качание этим не исключается, backpressure/грани сохраняются.

Большие сети/остаток 3 и пункты 13/14 входят в разрешённую реализацию [D-PIPE2](CURRENT-STATE.md#d-pipe2); снятие лимита поиска не объявляется выполнением всех целей производительности. Пункт 12 остаётся отдельно в D-PERF12. Автор выбрал6и10: FIFO/rowcache329–330, общий budget и ingress приняты автором в игре330;6/10 закрыты в331. В330 добавлены глобальные два цельных расчёта кнопок/GUI и pre-enqueue flooddisconnect; порционность сейчас не требуется по решению автора.9 закрыт решением автора без native-прогона;12 ведётся отдельно в D-PERF12. [Приёмка/хроника](HISTORY.md#perf-001-item-accepted), [предыдущая редакция](archive/editions/0.3.328/README.md).

## PERF-001/6/10 — admission, FIFO и кэш вычислений,0.3.329–330

Автор выбрал6/10 и уточнил: завершениеjob —расчёт/планирование, не исполнениеочереди; Stopтолькобурение,packUpотменяетрасчёт/очереди;начатаянеудача/отменатожеполучаетКД.Сохраняетсяодинpendingзапрос/BE ссохранениемпервогозаявителя, тихимcoalesce/conflictreject и исполнениемчерезобщуюserverFIFO. Передисполнениемкартывалидируютсяснова; отключённый/сменившиймирrequesterнеисполняется. Stopнемедленный(включаяснятиеpendingStartDrill),packUpсразугаситавтоматикудажеприсвоёмКД; повторныйсканразборкивКДне запускается. Deadline200gameTicksпо типу/режиму операции, TNTнеблокируетразведку, успешный/неудачныйначатыйрасчётставитКДвfinally;ключNBT`CalculationCooldowns`необязательный/старыеотсутствующие=нулевые. Pendingнеимеетресурсныхпобочек/неpersistится.

Pureкэш`scanSurface`: band+токены9чанковданногоLevel, сохраняетпорядокHashSet/List, исходныеchunks/вертикальнаяполосапрежние. Токеныинтересовнечитают/негрузятмир,invalidationsetBlockState/load/unload/levelunload/serverstop/datapack;одинаковыеправкивтомжетикене сливаются. Материалы/карты/приёмник/праваживые, никакогоreplayрасхода/выдачи/marker/импульсаизкэша.

FIFOочередиDequeсremoveFirst;retrybuild/dismantleвtail,TNTвhead/пауза, NBTпозиции/порядок/BuildQueueформатпрежние. Рудныйrowcacheне«однаждыпросмотрено»:толькослойбезpendingруды,стабильныеworldtokensвсехчанков5×5,Yсовпадает. Full/protection/immovable/worldfailоставляютretry;собственныеизмененияпоследобычитребуютдополнительногопроверочногопрохода. Новыеedit/load/unload/tagsне теряются. Энергия/4–8build/3packTNT/40digцикл/лимиты/склад/дропправилапрежние.

**Бюджет330:** два начатых цельных расчёта кнопок/GUI на MinecraftServerтик суммарно для всех BE/игроков/измерений, FIFO безdiscard/reclick; одинticket/BE, дубликат не меняет requester/место. Не более64 preflightchecks/тик, invalid/preflight/новыйCD не списываетheavy-start. Текущаякарта/режим проверяются передстартом, КД отсчитывается от завершения, а не ожидания. SetRemoved/chunkunload отменяютticket/pending, levelunload очищает только своймир, serverstop —всё. GUIпланирование не обходит бюджет. Автоматическая40тиковаяперекличка/добыча/энергиябуров оставлены внеочереди **по отдельному ответу автора**; это не потолок всей работы Плотника/миллисекунд. Counters: `PlotnickServerBudget.snapshot(server)` и DEBUG `[PlotnickBudget] started=…/2 checked=…/64 waiting=… totalStarted=…`.

**Сетевой допуск330:** только PlotnickButtonPayload/Fence C2S перенесены в HandlerThread.NETWORK, общий perconnectionпорог до mainenqueue. Server TOML `floodRequestThreshold = 128`, диапазон1–4096: столько запросов в любойскользящейсекунде и столько outstanding mainзадач; превышение любого —всегда disconnect с RU/EN причиной. Повторы/уникальные/невалидные значения считаются одинаково, begin/endобаучитываются. Принятый Fenceend отдельно не отбрасывается, порядок относительноvanilla inventory сохранён безtickbuffer. Закрытыйbacklog не обращаетсякменю/миру; logout/stop закрываютдопуск, provenancecleanup прежний. Configevents публикуютvolatileint, Netty не читает mutableconfig/world/menu. Это не универсальнаязащитаvanilla/другихмодов/decoding/DDoS и не оптимизацияsnapshotcopies12.

**Приёмка:** автор протестировал330 и подтвердил штатную работу; PERF-001/6/10 закрыты в331 по его прямому разрешению. Не перечислять неназванные отдельные сценарии как пройденные. В331 исправлена только RU/EN подсказка настройки порога, механика330 неизменна. [Хроника приёмки](HISTORY.md#perf-001-accepted331). Разбиение расчётов не требуется по решению автора; budget не hard-msгарантия. PlotnickBlock/election/прогрев5×5/PNG/баланс/radiationlaw/legacy/перенос unchanged.

**Дополнительные ответы автора перед доставкой329:** разные режимы одной кнопки —разные типы задач/КД (оборудование↔руды дляMARK/NETWORK; LINKсамостоятелен). При смене карты доbegin использовать **текущую** карту, а не отменять запрос. Pendingперепривязываетсякновомутипу доbegin и повторнопроходитегоКД; обходаdeadlineпереключениемкарты нет. Невалиднаяактуальнаякартаотсекаетсядоbegin безКД, начатая ошибкапо-прежнемуставитКД.

**Локализация настройки331:** `floodRequestThreshold.tooltip` имеет отдельныеRU/EN тексты; в русском GUI больше не требуется английский TOMLcommentfallback. Лимит128, диапазон1–4096, slidingwindow/outstanding/disconnect неизменны. Будущее отображение сообщений Плотника — только [D-PLOT1](DEFFERED.md#d-plot1), не реализация этого шага.

**Смена статуса01.11.2026:** №9 закрыт решением автора без нового native-доказательства;13/14 → [D-PIPE2](DEFFERED.md#d-pipe2),12 → [D-PERF12](DEFFERED.md#d-perf12). Реализация deferred не разрешена. [Хроника](HISTORY.md#status-reset332).

<a id="crit-2"></a>
## CRIT-2 — side-aware перенос наведёнки

**Закрыто в 0.3.334 по авторской приёмке01.11.2026:** «Оба теста прошли штатно, фиксы применены, уносим в закрытое». Новый агентский native-прогон/точное число GameTest не заявляются. Внешний опыт 332:4×6+4×2mZt/s давали 16/48 вместо 32 при неизменном count8; обычный InvWrapper сохранял 32. Это ошибка наведёнки, не количества; прежнее сообщение о native-воспроизведении не выдаётся за новый агентский запуск.

SidedInvWrapper имеет собственный insertItem; поэтому зарегистрирован отдельный SidedInvWrapperRadiationMixin. Redirect требует обе commit-точки grow: accepted-copy с дозой входной части объединяется с существующим слотом до grow на его старый count, через текущий ExternalRadiation.merge. simulate/reject не вызывают hook, original offer/остаток не меняют дозу; empty insert использует обычный copy/owner путь. SidedSlotCapacity-mixin и обычный InvWrapper не переписывались. Внешняя MenuDoseTransaction восстанавливает ту же weighted величину из исходных lots, не удваивает эффект.

Без тика распада/облучения оба порядка 4×6+4×2 дают 8×4=32mZt/s. Headless2566 assertions охватывают empty/partial/full, лимиты/face remap/reject, original offer, simulate без dirty/rate/count mutation, несовместимый тип и внешнюю транзакцию; baseline462183e и 2 dose mutants assertion-killed. Добавлены native matrix/outer-transaction тесты; новый запуск здесь не заявляется. Автор принял оба фикса; компоненты/cursor/save-load и соседние InvWrapper/воронка/трубы остаются регрессионным контрактом, не открытой очередью приёмки. Радиационная физика 313–318, sidecar-формат, баланс, NBT/components, паузы/count-trim и legacy не менялись. [CURRENT](CURRENT-STATE.md#crit-2--sidedinvwrapper-создаёт-или-стирает-наведёнку-при-объединении), [AUDIT](AUDIT.md#crit333), [HISTORY](HISTORY.md#crit333).

## Пакеты 332: проверенные source-основания, не новые исправления

A3/PIPE-II-1: perItemMap создаётся в ItemRouting.pushItems, который вызывается отдельно для входных граней. Требование автора — лимит Item на весь узел за проход, отдельно от общего бюджета и независимо от других узлов. Внешняя native-проба сообщает 2 переноса при cap1/сумме 10; это превышение throughput, не count-dupe. A5/PIPE-II-2: ItemFilterRouting ищет sinks до no-work return; его поиск не покрыт общим BFS-budget. TPS не измерен. Оба пункта вместе с 13/14/остатком 3 — [D-PIPE2](DEFFERED.md#d-pipe2), не реализованы.

A6/SEC-1: инвентаризировать bounded ingress остальных C2S, enqueue/decode/размеры/полномочия/menu/distance/loaded target; существующие Plotnick/Fence330 не переоткрываются. A7/SEC-2: crisis-click notification требует отдельной server truth/повторной валидации, не только throttle. Это source-риски, не измеренная DoS-атака: [D-SEC1](DEFFERED.md#d-sec1). №9 закрыт без native-claim, SEC-3 не открыт. Room snapshots12 — отдельно [D-PERF12](DEFFERED.md#d-perf12); CRIT-2 закрыт по приёмке334; это корректность переноса, не выполненная широкая оптимизация.


Предыдущая редакция: [до CRIT333](archive/editions/0.3.332/OUTDATED_BEFORE_CRIT333_CAPABILITIES.md); [решение и проверки](HISTORY.md#crit333).


<a id="reactor-review334"></a>
## Новые наблюдения после приёмки334

**HUD-NODE1, текущая задача:** по уточнению автора мигает счётчик у **любой сшитой группы**, не только реакторных портов. Стабильная топология должна давать стабильное число между ответами; проверить WrenchHud FlowEntry/clumpSize, свежесть и смену адресата без фиктивных размеров. Потоковые данные не объявляются свежими бессрочно ради строки. Это не активация D-PIPE2.

**REACT-VAL1/2, текущие задачи:** standalone-режим встроенного узла не должен определять валидность реактора; в корпусе узел — порт. После завершённого возврата обшивки поршнем ранее собранный реактор должен иметь согласованные root/bounds/member index/живые endpoints и реальное GUI, без ручного break/place. Подтвердить heat/water/universal варианты и обычные узлы вне корпуса; не лечить снятием loaded/identity guards и не сохранять stale receiver. Портовый gthPortLimit16 364GTH/т и внешние трубные ограничения не менять, поток не возвращать в ту же структуру.

[CURRENT](CURRENT-STATE.md), [решение и доказательства](HISTORY.md#reactor-review334), [предыдущая редакция](archive/editions/0.3.334/OUTDATED_BEFORE_REACTOR_REVIEW334_CAPABILITIES.md). Код в этом документальном шаге не менялся.


<a id="embedded-port335"></a>
## 0.3.335: что изменилось в возможностях

- **Стабильный счётчик любой сшитой группы.** Строка «сшито узлов: N» больше не зависит от свежести потока; до первого ответа — «сшито узлов: <?>», сразу с первого наведения. Источник — отдельный серверный пересчёт `NodeClumpIndex.sizeAt` по внешним соседям порта, а не кэшflow-метрик.
- **Вшитый порт сформированного мультиблока.** Узел в обшивке перестал быть самостоятельным сетевым узлом: он не сшивается с обшивкой/внешними узлами, при формировании ранее сшитые порты отстёгиваются, ранее собранная сборка восстанавливается без ручного break/place. «Сшито 2» означает ровно два узла **снаружи** обшивки (эталон парогенератор); оставшиеся каналы не самостоятельная сеть.
- **Возврат обшивки поршнем.** Ранее собранный реактор с сохранёнными границами действительно валидируется после завершённого обратного хода поршня, а не только получает formed-текстуру; недопустимая оболочка и недоступная зона остаются невалидными.
- **Подсказка** с настоящим именем блока и тремя режимами для **всех** formed сборок.
- **Не изменилось:** `REACTOR_GTH_PER_PORT_MILLI = 16_364×MILLI` (16 364 GTH/т на вывод), `gthPortLimit`, лимиты труб, первичная сборка от `onPlace(movedByPiston=true)`, исключение поршня/head из whitelist, баланс/радиация/рецепты, `D-PIPE2`. Конкретная логика режимов вшитого порта — [D-PORTMODE1](DEFFERED.md#d-portmode1) на 0.3.336+.

Границы доказательств: локальные Python-аудиты/source-контракты, не нативная сборка и не GameTest; авторская игровая приёмка 0.3.335 получена и зафиксирована отдельно; см. [AUDIT](AUDIT.md#embedded-port335) и [хронику](HISTORY.md#v-0-3-335). **Приёмка автора 05.11.2026:** HUD-NODE1/REACT-VAL1/REACT-VAL2 закрыты («Остальное хорошо, штатно»), подсказка вшитого порта приведена к компактной многострочной ГОСТ-форме с реальными хостом/владельцем, а классическая строка обычного узла больше не появляется в первом кадре (кэш `BlockPos → ownerId` без TTL + пачка ответов по области). Скриншоты автора — визуальное свидетельство двух дефектов, не нативная проверка. D-PORTMODE1 выведен в [активную работу](CURRENT-STATE.md#d-portmode1); см. [приёмку](HISTORY.md#v-0-3-335-accept) и [AUDIT](AUDIT.md#embedded-port335-accept).

## 0.3.336: реальные режимы вшитых портов

У вшитых корпусных портов всех formed-структур заработали «забор / отдача / авто». «Забор» только принимает ресурс внутрь сборки (GTH реактора наружу не идёт), «отдача» только выдаёт (через неё нельзя ввести воду в пароген), «авто» разрешает обе стороны, но они делят ОДНУ общую проводимость порта: сумма за тик не выше потолка, первый занявший получает лимит, `gameTime` ротирует приоритет. Режим один на весь узел и сохраняется при распаде. Без перебалансировки: 16 364 GTH/т на вывод, `gthPortLimit`, лимиты труб и `PipeType.HEAT` не тронуты. Границы доказательств прежние: source/ECJ/Python, не нативная сборка и не игровая приёмка режимов. [CURRENT](CURRENT-STATE.md#d-portmode1), [AUDIT](AUDIT.md#portmode336), [хроника](HISTORY.md#v-0-3-336).

## 0.3.337: закрытие режимов порта и стабильность HUD

D-PORTMODE1 **закрыт** по игровой приёмке автора («Режимы работы вшитых портов приняты, всё работает»), удержание в CURRENT до 0.3.341; реализация 0.3.336 (`nodeMode`/`allowsIntake`/`allowsOutput`, общий бюджет `sum_cap`, `whole`, `keep_mode`) не переписывалась и не перебалансировалась. Новый патч чинит только подсказку ключа, сетевой троттлинг и серверный срез потока: строка «поток» рисуется только при передаче >0 ед./тик, `сшито <?>` не вспыхивает в первый кадр (grace 2 тика), цифры идут раз в 1–2 тика при серверном лимите 24/20, четвёртая AUTO-строка удалена. По уточнению автора багованное «1 сек есть / 1 сек нет» при постоянном потоке лечится `FlowTracker.getHud`/`HUD_WINDOW_TICKS = 2`, а не «строкой априори»; поточное мелькание при прерывистом потоке допустимо. Никаких новых предметов, блоков, PNG, GUI и каналов; режимы и лимиты труб/`PipeType.HEAT` прежние. [AUDIT](AUDIT.md#hud337), [HISTORY](HISTORY.md#v-0-3-337).

**Приёмочная правка в том же 0.3.337.** По `image-4.png` убрана кипа нулевых строк: HUD показывает только текущие потоки >0, предметы с `count <= 0` отбрасываются, `<?>` при задержке — только для типов с недавней подтверждённой передачей. Игровая семантика `FlowTracker.get()` (компаратор/редстоун) и `getLoss` не изменены. Версия не поднималась. [HISTORY](HISTORY.md#v-0-3-337-accept), [AUDIT](AUDIT.md#hud337-accept).

**Итог 0.3.337.** D-PORTMODE1 и HUD-FLOW2 закрыты по игровой приёмке автора, удержание до 0.3.341. RAD-VALID1 ожидает приёмки; D-PERF12 переведена в работу, runtime-оптимизация не начата. [HISTORY](HISTORY.md#v-0-3-337-closed), [AUDIT](AUDIT.md#hud337-closed), [RAD-VALID1](CURRENT-STATE.md#rad-valid1).

<a id="rad-valid1"></a>
## RAD-VALID1 — границы доказательств радиации (0.3.337)

Полная [матрица взаимодействий](../RADIATION_INTERACTIONS.md#rad-matrix337) (A–H, формула/ожидание/`файл:строки`) доставлена в ветку; это рабочий артефакт валидации в корне репозитория, не новый профиль docs. Подтверждено 11 радиационных сьютов и полный локальный набор (101 PASS / 0 SKIP / 0 FAIL / 101), `doc_links`/`docs_structure`/ECJ/validate/brackets/tree-sitter. **Не** подтверждено: native Gradle/NeoForge/JAR, Minecraft-клиент/сервер, Mixins-рантайм, GPU/TPS/аллокационный профиль, GameTest «29/29» (23 `@GameTest` не запускались), полное детерминированное old/new сравнение. `SKIP ≠ PASS`; исторические `D-AUD1`, `selfTest3FAIL`, неиспользуемый `G` в `third_grinder` не скрыты; ECJ-repack 14 658 971 байт не выдаётся за Maven SHA `01f5a92…`. Порядок автора: радиация → крафты/баланс/цепочки → трубы → серверная валидация; D-PERF12 [в работе](CURRENT-STATE.md#d-perf12), Этап 1 — измерение без баланса. [CURRENT](CURRENT-STATE.md#rad-valid1), [HISTORY](HISTORY.md#rad-valid1), [AUDIT](AUDIT.md#rad-valid1).

<a id="pipe-ii-design337"></a>
## Проектирование D-PIPE2 — 08.11.2026

[Проект реформы](<../info/07 — Проект Великой реформы труб II.md>) одобрен автором; в 338 начата реализация D-PIPE2. Действующий контракт и честные ограничения — [ниже](#pipe338). Игровая сшивка NodeClumpIndex остаётся отдельной механикой; для измерения выигрыша автор разрешил эксперимент339 с ограниченным кэшем коридоров (см. ниже). Источники закона: PipeCarrier, PipeRouting/ItemRouting, PipeFlowLedger/PipeLoss, ItemTransferGuard и EmbeddedPortMode.

<a id="pipe338"></a>
## D-PIPE2 — маршрутизация 0.3.338

`PipeNetworkService` хранит только геометрию/адреса, не ресурсы и не receiver/Container. Ячейки 8×8×8 — индекс наблюдений и зависимостей, не предположение о связности внутри куба. BFS сохраняет очередь, родителей и порядок Direction между квантами; нет отсечения по размеру сети/числу получателей. Общая серверная квота — 4096 обработанных вершин и ориентир 3 ms за тик, с контрольной точкой каждые 16 вершин; это бюджет поиска, не обещание полной стоимости transfer/GC/первого JIT. READY означает полный план загруженной достижимой области; неизвестные границы отслеживаются до chunk-load. PENDING не отдаёт неполный список получателей. Синхронный запрос и ServerTick.Post обслуживают одну ротацию: ранний в порядке BE источник не может каждый тик вытеснять поздний; при исчерпанной квоте необслуженная работа сохраняет место.

Изменения наблюдаемой области немедленно отзывают старый план; повторные изменения объединяются в одну dirty-запись, следующая попытка — после смены серверного тика. Изменения несвязанных ячеек не перезапускают запрос. Простое пустое листовое ответвление можно добавить/убрать локально по шести граням; при петле, приёмнике, неизвестной границе или политике порта применяется обычная инвалидация. Это базовый режим A: не универсальное динамическое разбиение графа. Дополнения экспериментального B описаны ниже; B выбран автором в340; пределы сравнения указаны ниже.

Сохраняются прежние per-channel carries/opens/mode, кратчайший физический BFS-путь и порядок обхода, предметные лимиты/sidecar/защита возврата, resource lane-параллели, max-min, PipeLoss и физический ledger. Получатели разрешаются заново при использовании: запись в заменённый BE запрещена. Фильтр/отсеиватель используют тот же планировщик; пустой буфер и отсутствие извлекаемых предметов прекращают обработку до поиска. Лимит точного Item действует на весь предметный узел за тик, включая несколько граней и повторный вызов.

Чанки не подгружаются маршрутизатором. Load/unload, block/BE edit и смена индекса владельца встроенного порта инвалидируют зависимости; Level unload/server stop удаляют jobs/состояния. Контейнеры-сущности остаются динамической границей: предметный список проверяется/обновляется отдельно. Через 40 тиков запускается отдельное порционное обнаружение под той же квотой; на большой сети его завершение может занять дополнительные тики. Старый полный снимок до завершения остаётся только с живой проверкой владельца; новый публикуется атомарно. Геометрические пути ресурсных lanes кэшируются без захвата BE/мира; потери/остатки ресурса вычисляются живыми. HUD показывает только реально прошедший поток, правила 337 не менялись.

**Граница проверки:** headless-стенд поиска не является native-TPS/FPS-профилем. Холодная цель <30 ms ещё не достигнута; плотные сети с большим числом источников/получателей требуют отдельного профиля стоимости распределения. [Статус и проверки](AUDIT.md#pipe338).

<a id="pipe339"></a>
## D-PIPE2 / 0.3.339 — сравниваемые режимы A/B

`PipeExperiment` фиксирует режим на весь процесс: embedded properties `variant=baseline|experimental`, `profile=true|false`; JVM override `gonzotech.pipe.variant`/`gonzotech.pipe.profile`. Смена на лету запрещена. Сборка задаёт `-PpipeVariant`/`-PpipeProfile`; по умолчанию experimental без агрегатного профиля. Архивные A/B339 JAR включают профиль; актуальная340 по умолчанию использует B с выключенным агрегатным профилем. A сохраняет поиск338 с общими диагностическими обёртками; это не побайтово исходный338, который сохранён отдельно.

В B `Query.watch` хранит BitSet512 по каждой реально наблюдавшейся ячейке. Событие в другой позиции той же ячейки не отзывает запрос. Unknown-границы также наблюдаются; chunk lifecycle по-прежнему отзывает связанные результаты. `rememberCorridor` строит запись только попутно обычному BFS: минимум4 позиции, максимум квант64, единственный фронт, ровно два связанных соседа, есть родитель, нет открытой границы/получателя/виртуального порта. Запись может пересекать несколько ячеек; владелец — ячейка начала. LRU максимум3 **всего** на владельца, включая типы/направления; idle200. Это кэш цепочек, не сварка произвольных сетей в иерархический граф.

`corridor`/`replay` требуют тот же вход, предыдущую позицию, канал и вид запроса, единственный фронт. Возвращаются все физические родители/длины; каждая позиция расходует бюджет4096/тик. При конкурирующих фронтах идёт обычный BFS, поэтому порядок кратчайших путей сохранён. На правке наблюдённой геометрии/BE или unload `invalidateCorridors`/`removeCorridor` удаляют затронутые записи; остальные остаются. PORT исключён из replay из-за политики принадлежности структуре. RESOURCE допускается только без виртуального порта внутри цепочки.

`keepUnusedEdgeClosure` может оставить готовый план, если блок и режим прежние, меняется только закрытие рёбер, оба конца уже в дереве и ни одно закрытие не затрагивает родительское ребро. Новое соединение, удаление выбранного пути, неизвестность или PORT требуют прежнего безопасного поиска. Это не общий алгоритм ремонта BFS из границы разрыва. Структурный reset продолжает отзывать всё связанное.

`PipeBenchmark` считает поиск, вызовы переносов, рестарты/чтения/точные пропуски, построение/попадания коридоров, ресурсы и предметы. Одна агрегатная строка/200 серверных тиков, UUID сессии и `pipe-II-339-A/B`; на stop частичное окно. Детальные ITEM/CLUMP/ROUTE-логи тоже несут identity. Дозы, ресурсы, тарифы/распределение и HUD не меняются. Точный протокол и границы таймеров — [A/B](../info/PIPE-AB-339.md), доказательства — [AUDIT](AUDIT.md#pipe339).

<a id="pipe340"></a>
## 0.3.340 — выбран B, исправлен персист сшивок

Автор выбрал режим B базой дальнейшей разработки. Алгоритм339B и его бюджеты/ограничения сохранены; техническое имя variant=experimental оставлено для совместимости переключателя, штатная сборка выбирает его. Snapshot теперь pipe-II-340-B; baseline override помечается340-A и не является сохранённым339-A. Старые JAR/ZIP338–339 не перезаписаны. По умолчанию profile=false, для измерений -PpipeProfile=true. Сравниватель принимает версии в identity, но запрещает смешивать разные версии в одной паре.

NodeClumpIndex.clumpsOf теперь применяет kindOfBlock ко всем корням, включая UniversalNodeBlock, который не наследует NodeBlock. В NBT сохраняются U1/U2/U3, формат Root/Kind/Members и потери прежние. Это исправление реального серверного индекса: его членство влияет на пропуск внутренних ограничений маршрута, не только на sizeAt для HUD. Старые отсутствующие записи восстанавливаются recoverUniversal из существующего scheduled tick универсального узла. Он не трогает уже индексированные группы, не force-load-ит старт и обходит только загруженных соседей по прежнему same-kind закону. Негативные результаты singleton/port привязаны к слабому ключу LevelChunk, без удержания выгруженного мира; stop очищает кэш. Первое восстановление большой старой группы всё ещё имеет цену обычного onNodeChanged BFS; новый слой не объявлен безлимитно дешёвым.

Порты трёх сформированных структур исключаются kindOf, не становятся членами внешней группы. Соседний порт не нужен для воспроизведения найденной потери NBT. Профиль и приёмка — [AUDIT](AUDIT.md#pipe340), [PIPE-LOAD1](CURRENT-STATE.md#pipe-load1).
