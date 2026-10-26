# Предметная радиация — 0.3.311

**Сверено с кодом 27.10.2026.** Основание: авторский коммит `c15083f` (`rad global recoding`), интегрированный поверх 0.3.310. Это описание реализации, не новый баланс и не заявка на изменение механики. Версия в `gradle.properties` остаётся **0.3.311**.

Действующая спецификация. История — [HISTORY](HISTORY.md); мир/доза — [RAD_WORLD](RAD_WORLD.md); правила — [RULES](RULES.md). Происхождение реализации: исходные отчёты автора сохранены в архиве.

## 1. Что изменилось

- Динамические **наведённая эмиссия одного предмета и время последнего расчёта** живут вне NBT/data components ItemStack. Они не участвуют в ванильном сравнении компонентов, не требуют последующего flush в предмет и не меняют количество предметов.
- `ExternalRadiation` хранит `Dose(rate, tick)` по экземплярам ItemStack в слабых картах `VIEWS` и `CONTENTS` (вторая — груз щипцов/ковша). Это runtime-состояние с адаптерами копирования/переноса и сохранением у владельцев, **не самостоятельный список источников мира**.
- В коде больше нет `RadDoseIndex`, `RadReequipGate` и `ItemInHandRendererMixin`. Нет прежних quiet-окон записи, held-cache/отложенного flush и радиационного обрезания количества. `RadiationClickWatch` также не является действующим механизмом.
- Радиоактивность рисуется отдельной paint-only панелью. Обычный `RadTooltip` добавляет только токсичность. Панель, сетевой fence и снимки не записывают дозу в ItemStack и не исправляют инвентарь.
- Природные пресеты, экранирование, ядро роста/спада, лекарства и шкала игрока остаются отдельными механизмами. Новый способ хранения не означает автоматически нового игрового баланса.

Источники: [ExternalRadiation](../src/main/java/com/gonzotech/radiation/engine/ExternalRadiation.java), [ItemRadioactivity](../src/main/java/com/gonzotech/radiation/ItemRadioactivity.java), [список миксинов](../src/main/resources/gonzotech.mixins.json).

## 2. Перенос: значение на предмет, не сумма стопки

Пусть `a`, `b` — наведённые эмиссии **одного** предмета, `n`, `k` — количества. При объединении:

```text
merged = (a × n + b × k) / (n + k)
```

Сохраняется `rate × count` переносимого материала, а не сумма двух per-item значений. Например, 8 предметов по 2 mZt/s + 8 по 6 mZt/s → 16 по 4 mZt/s (полная наведённая эмиссия 64 mZt/s). При split обе части наследуют per-item rate исходной стопки. Рост/спад радиации и создание нового материала — отдельные операции, не нарушение этого инварианта переноса.

- `copy` переносит внешний rate/tick и состояние груза; `copyTree` сопоставляет вложенные слоты до глубины 4. Миксин ItemStack.copy обслуживает стандартную цепочку копирования/разделения.
- `ExternalRadiation.merge(destination, source, added)` вызывается **до** увеличения количества назначения; tick берётся как максимум двух времён. Этот метод сам не потребляет и не создаёт предметы.
- Новый продукт рецепта по умолчанию имеет induced = 0 (его природная эмиссия при этом никуда не исчезает). При добавлении чистого продукта к существующему выходу `dilute` разбавляет наведёнку по той же взвешенной формуле. Это не правило «вся радиация сырья переходит в продукт».
- Разные новые внешние значения наведёнки больше не мешают обычному стакингу. Все **остальные** ограничения ванильного сравнения предмета/компонентов сохраняются.
- `DoseMath.finite` отбрасывает неположительные/нефинитные значения. Порог очистки 1 mZt применяется в `ItemRadioactivity.setInduced`; не следует подменять им взвешенное усреднение в `DoseMath`.

Адаптеры охватывают стандартные copy, Inventory, Hopper, SimpleContainer, Slot.safeInsert, ItemStackHandler (с учётом simulate), InvWrapper, объединение ItemEntity и BundleContents.Mutable; есть явные вызовы в станках, печах, маршрутизации и носителях GonzoTech. Это **не** гарантия для любого стороннего способа пересоздания предметов.

Источники: [DoseMath](../src/main/java/com/gonzotech/radiation/engine/DoseMath.java), [ExternalRadiation](../src/main/java/com/gonzotech/radiation/engine/ExternalRadiation.java), [ItemRadiationCopyMixin](../src/main/java/com/gonzotech/mixin/ItemRadiationCopyMixin.java), [BundleRadiationTransferMixin](../src/main/java/com/gonzotech/mixin/BundleRadiationTransferMixin.java).

## 3. Сохранение у владельца, не в item codec

`RadiationOwners` пишет sidecar **`GonzoItemRadiationV2`** в сохраняемые данные владельца. Поддержаны инвентарь и эндер-сундук игрока, PetBowl, владельцы `Container`, ItemEntity, ItemFrame и экипировка LivingEntity. Используются стабильные индексы слотов и проверка **Item id + count** при восстановлении; это не проверка полного произвольного набора компонентов.

Запись слота содержит `Slot`, `Item`, `Count`, `Rate`, `Tick`; при необходимости — вложенные `Contents` до глубины 4 и `CargoRate`/`CargoTick` для груза носителя. `RadiationOwners.children` включает пустые слоты CONTAINER, чтобы их адреса не смещались, и содержимое BUNDLE_CONTENTS; для ItemContainerContents есть accessor к реальным слотам. В вычислениях/пассивном спаде вложения доступны через `RadSources.contentsOf`. Сохранение владельца переживает выгрузку/загрузку без добавления дозы в сериализованный ItemStack.

- BlockEntity hook: `saveWithFullMetadata` / `loadWithComponents`. `saveToItem`/`saveCustomOnly` не должны вкладывать этот sidecar в предмет.
- Entity hook: `saveWithoutId` / `load`; игрок включает и ender inventory.
- Слабые карты сами по себе не являются дисковым хранилищем. Произвольный non-Container `IItemHandler` или собственный сторонний item codec требует адаптера владельца/переноса.
- Старые item-NBT значения **не мигрируют, не читаются как новая наведёнка и автоматически не очищаются**. Различающиеся legacy-компоненты всё ещё могут мешать ванильному стакингу. Проверка новой архитектуры проводится на свежих предметах; мира пользователя этот документ не изменяет.
- Щипцы/ковш по-прежнему хранят вид/количество содержимого, а не полный произвольный набор его компонентов. Внешний перенос дозы не является переделкой общего формата их груза.

Источники: [RadiationOwners](../src/main/java/com/gonzotech/radiation/engine/RadiationOwners.java), [BlockEntityRadiationSaveMixin](../src/main/java/com/gonzotech/mixin/BlockEntityRadiationSaveMixin.java), [EntityRadiationSaveMixin](../src/main/java/com/gonzotech/mixin/EntityRadiationSaveMixin.java), [ContainerContentsRadiationMixin](../src/main/java/com/gonzotech/mixin/ContainerContentsRadiationMixin.java).

## 4. Меню и креатив

`MenuDoseTransaction` оборачивает **серверный** `AbstractContainerMenu.clicked`: снимает прежние партии и согласует результат по объектам/происхождению копий, затем по доступному материалу. В QUICK_CRAFT старая партия целевого слота резервируется отдельно от добавленного материала. Bundle/носители сообщают custom transfer, чтобы общий reconcile не считал их операцию второй раз. Это учёт **внешней дозы**, не замена vanilla click/count logic; обрезания излишка как «лечения дюпа» нет.

`CreativeRadiationBridge` использует только серверу известные партии. Образец из каталога — новый чистый материал, а не клиентский источник наведёнки. Для cursor metadata принимаются только ограниченные count 0…64 и fingerprint, **не переданная клиентом доза**. Приоритет сопоставления — идентичность объекта, прежний слот, перенос доступной партии.

Ванильный creative-пакет не сообщает намерение при перестановке двух полностью одинаковых по item/components/count стопок с разной внешней дозой. В этом неоднозначном случае нельзя обещать индивидуальную историю каждой стопки; сохранение совокупной эмиссии и количества не доказывает такую идентичность. Старый открытый creative-сценарий из истории дюпа не объявляется автоматически закрытым игровым тестом.

Источники: [MenuDoseTransaction](../src/main/java/com/gonzotech/radiation/engine/MenuDoseTransaction.java), [CreativeRadiationBridge](../src/main/java/com/gonzotech/radiation/engine/CreativeRadiationBridge.java), [MenuRadiationTransferMixin](../src/main/java/com/gonzotech/mixin/MenuRadiationTransferMixin.java).

## 5. Сеть и отдельная панель

`RadiationViewNetwork` передаёт S2C `item_radiation_view`: menu, state, ack и списки inventory/slots плюс carried, с `slot`/`fingerprint`/`rate`. Здесь `Entry.rate` — **полная deep-эмиссия стопки** (`RadSources.emissionDeep`), включая природную эмиссию и count, не чистая per-item наведёнка из ExternalRadiation. C2S `item_radiation_fence` несёт menu, sequence, begin и cursor count/fingerprint. Это согласование отображения и происхождения серверных партий, а не клиентское управление дозой.

- Снимок проверяется сразу при смене menu/state/ack, иначе раз в 5 тиков; одинаковые снимки повторно не отправляются. Лимит списка — 1024 слота. При logout/остановке сервера состояние очищается.
- `RadiationPanel` ставит fence вокруг мыши/клавиатуры; незакрытый жест закрывается в ClientTick.Post. До актуального ack, совпадения menu/state и fingerprint слота показывается **«…»**, а не устаревшая цифра. Для creative state-проверка отличается от обычного меню; образцы каталога показывают природную эмиссию.
- Панель находится над tooltip, при нехватке места — под ним, с ограничением границ экрана. У подтверждённой эмиссии < 1 nZt/s панели нет. Вне сопоставленного слота/для образца возможен fallback `RadSources.emissionDeep`, а не универсальная серверная синхронизация произвольного tooltip.
- Подпись «Радиоактивность:», число/единица — `#CEEB2D`; форматтер `GtUnits.zt`, суффикс **`/s`**, не `/t`. Токсичность остаётся строкой стандартного tooltip (`RadTooltip`), цвет/формат — `GtUnits.tx`.
- Панель предмета и приборная визуализация частиц — разные каналы. [План/реализация частиц](archive/legacy-0.3.311/RADIATION-VISUAL-PLAN-2026-09-25.md) не описывает этот slot snapshot.

Источники: [RadiationViewNetwork](../src/main/java/com/gonzotech/radiation/engine/RadiationViewNetwork.java), [RadiationPanel](../src/main/java/com/gonzotech/radiation/client/RadiationPanel.java), [RadTooltip](../src/main/java/com/gonzotech/radiation/client/RadTooltip.java), [GtUnits](../src/main/java/com/gonzotech/core/text/GtUnits.java).

## 6. Актуальное математическое ядро

Внутренние rates — **nZt/s**; `1 mZt = 10⁶ nZt`, `1 Zt = 10⁹ nZt`. Основной шаг — 20 игровых тиков (1 игровая секунда при обычных 20 TPS); `/tick rate 200` ускоряет шаги по реальному времени, но не меняет суффикс единиц.

| Параметр ItemRadioactivity | Значение |
|---|---|
| `GROWTH` | 1.25 |
| `INDUCED_CAP` | 5 Zt на предмет |
| `UPTAKE_COST` | 0.00097 на каждый mZt текущей наведёнки |
| `SELF_DISCHARGE` | 0.25 |
| `DISCHARGE_ANCHOR` | 33 mZt |
| `CLEANUP_FLOOR` | 1 mZt |
| `INDUCED_REEMIT` | 0.75 только в передаче наведёнка → другой предмет |

Для текущего per-item `L` и локального источника `sourceNzt`:

```text
target = clamp(sourceNzt − intrinsicPerItem, 0, 5 Zt)
seeded = max(L, 1 mZt)
uptake = (1 − 0.00097) ^ (seeded / 1 mZt)
discharge(x) = x × (1 − 0.25 × tanh(x / 33 mZt))
```

При источнике, `target ≥ 1 mZt`, положительном factor и `L ≤ target`:

```text
next = discharge(min(seeded + seeded × 0.25 × (1 − seeded / target) × factor × uptake, target))
```

При `L > target` либо отсутствии пригодного источника применяется саморазряд текущего `L`. После шага `setInduced` убирает значения ниже 1 mZt. Деления роста на count стопки нет: это per-item вычисление. Собственный intrinsic носителя в этой формуле — 0; собственная стопка исключается из локального источника через второй максимум. Чанк участвует в наведении только при полном фоне **строго больше 30 mZt/s**; локальная цель выбирается из чужого инвентарного источника (с reemit) и такого фона чанка, а не из суммы всех собственных слотов.

`restDecay` догоняет пассивный спад максимум 1024 итерациями; длинный остаток при необходимости обнуляется. `decayInducedDeepLazily` обрабатывает **и CONTAINER, и BUNDLE_CONTENTS** до глубины 4, а также внешний cargo носителя, не переписывая компоненты содержимого. Сундук/россыпь не становятся инвентарём с взаимным наведением. **Плато 10 mZt и прежний порог очистки наведёнки 100 nZt не действуют.** Это не отменяет отдельный `DOSE_CLEAR_FLOOR = 100 nZt/s` естественного восстановления шкалы игрока.

Полная эмиссия для игрока/чанка учитывает наведёнку с весом 1.0. Только наведёнка → другой предмет получает ×0.75. Содержимое щипцов/ковша получает ×0.6 по радиоактивности (наведёнка груза → предмет: ×0.6×0.75 = 0.45); токсичность содержимого — ×0.2. Обычные вложенные хранилища такой защиты не дают.

Источники: [ItemRadioactivity](../src/main/java/com/gonzotech/radiation/ItemRadioactivity.java), [RadSources](../src/main/java/com/gonzotech/radiation/RadSources.java), [CarrierItem](../src/main/java/com/gonzotech/radiation/CarrierItem.java), [RadUnits](../src/main/java/com/gonzotech/radiation/RadUnits.java).

## 7. Потоки игрок/предметы → чанк

Пусть `C` — contamination чанка (не baseline + contamination), `M` — полная deep-эмиссия рассматриваемого инвентаря/контейнера/стопки, `F` — фактор контура у источника, `S` — эмиссия сильнейшей верхнеуровневой стопки инвентаря **после** обновления наведёнки.

| Источник | Текущий вклад в contamination |
|---|---|
| Инвентарь игрока | `0.01 × 0.95 × F_игрока × max(min(M, S) − C, 0)` |
| Обычный контейнер | `0.01 × F × max(M − C, 0)` |
| Свинцовый ящик | `0.01 × max(0.2 × M × F_ящика − C, 0)` |
| Конечная стопка ItemEntity/ItemFrame | `0.01 × F × max(M − C, 0)` |
| Радиоактивный BlockItem на полу/в рамке | Отдельная placed-like цель: `blockEmission × count × F`, не обычный перенос 1%; сходимость обслуживает ChunkRadiationData |

Контейнеры/предметы сканируются не чаще одного раза за 20 тиков на чанк. Вложенное содержимое входит в deep-эмиссию верхней стопки, а не ещё одним параллельным владельцем-источником. Курсор меню не добавлен как новый источник облучения. Наличие сохранённого owner sidecar или отправленного UI snapshot само по себе не добавляет источник миру.

Доза игрока складывается из полной эмиссии инвентаря, 0.1 полного фона чанка и `Containment.insideDose` (40% реальной эмиссии блоков в той же закрытой полости), затем применяются psyche multiplier, hazmat и абсорбция дозы. Приращение шкалы и перенос в contamination — разные расчёты; не следует применять ×0.75 ко всей дозе игрока.

Лекарства не переработаны этим движком: прямое снятие текущей дозы у ДТПА/Пентасина не тождественно `RadCleanse`; абсорбция предотвращает входящую дозу. Очищенная `RadCleanse`/естественным спадом доза обрабатывается отдельным путём передачи в чанк с потолком 100 mZt. [Таблица лекарств и подробности](RAD_WORLD.md).

Источник: [RadiationSystem](../src/main/java/com/gonzotech/radiation/RadiationSystem.java), методы `onPlayerTick`, `scanContainersInto`, `scanDroppedItemsInto`, `transferTowardChunk`; [ChunkRadiationData](../src/main/java/com/gonzotech/radiation/ChunkRadiationData.java).

## 8. Проверки и честные границы доказательств

В [RadiationGameTests](../src/main/java/com/gonzotech/radiation/engine/RadiationGameTests.java) **11** GameTests: copy/split/no item writes; PICKUP/QUICK_CRAFT; автоматика/дроп/bundle; owner persistence; wrappers/carriers; creative server-known dose; карты Плотника; rapid menu stress; nested persistence/placement; эквивалентность геометрии труб; rollback резерва Плотника. Последний возвращает реально изъятый стек с компонентами/дозой; порядок активные слоты → депо → добытое сохранён.

Запуск из [build.gradle](../build.gradle): `./gradlew build runRadiationTest --offline --no-configuration-cache`, JDK 21 и локально доступные зависимости; рабочий каталог запуска **`run-radiation-merge-tests-311`**, namespace `gonzotech`. Старые «10 тестов»/`run-radiation-tests` относятся к авторской копии 0.3.306.

**Автор сообщает** об успешных build + 11 GameTests в отчёте интеграции. В документальном проходе 27.10 это **не было независимо повторено**: в среде нет JDK. Интерактивная приёмка реальным клиентом (ЛКМ/ПКМ/Q, `/tick rate 200`, прогнозирование количества, актуальность/положение панели, creative-неоднозначность, сохранение и перезаход) остаётся отдельной ручной проверкой. Наличие тестов в исходниках не равно выполнению этих сценариев.

Базовый `python3 audit/run_all.py` на `c15083f`: **53 PASS / 19 SKIP / 2 FAIL, всего 74**. Первые фактические падения:

- `autominer_markers_test.py:31` ожидает `mod_version=0.3.310`, хотя код уже 0.3.311; далее в том же файле остаются старые пины `RadDoseIndex`.
- `radiation_behavior_test.py:47` ожидает `ItemRadioactivity.setInduced(stack, state.value)` старого held-state; дальше также есть контракты прежней архитектуры.

Это устаревшие source-контракты, не доказанные игровые дефекты нового движка. Их обновление требует отдельной сверки с новой реализацией, а не удаления проверок ради зелёного отчёта. Это baseline прежнего документального прохода. Текущие проверки/миграция путей — [AUDIT](AUDIT.md). Радиационная математика и runtime не меняются.


## История предметной системы

0.3.249/268/298 — развитие ядра, не новая очередь; 0.3.294 — прежний RadDoseIndex; 0.3.300–301 — прежний anti-bob. 0.3.311 (`c15083f`) заменяет эту архитектуру внешним движком/owner sidecar. `42730c3` сверяет документы; следующий docs-only шаг переносит эту спецификацию сюда без изменения runtime. Полный лог — [HISTORY](HISTORY.md).
