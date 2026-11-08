<a id="rad-matrix337"></a>
# Матрица взаимодействий радиации — валидационный срез 0.3.337

**Основание:** автор выбрал порядок `радиация → крафты/баланс/цепочки → трубы → серверная валидация и защита сверх всего`; по радиации приоритетно проверить все виды взаимодействий и валидировать.

**Срез:** ветка `arena/a5aec272-gonzotech`, HEAD `e0e8cb7e4e3f89bfd46074bd2d758d2a0544f8ee`, `mod_version=0.3.337`. Код радиации не менялся; это вычитка + прогон существующих проверок. Матрица — рабочий артефакт валидации, **не** новый профиль `docs/`: по `DOCS_RULES.md` корневые файлы docs фиксированы 29 темами, а «актуальную сводку» поверх `RAD_WORLD.md`/`RAD_ITEMS.md` создавать запрещено.

**Метод:** для каждой строки указаны формула, ожидаемое поведение, точное кодовое подтверждение (`файл:строки`) и статус покрытия. Статусы: `✓` — исполняемая/компилируемая проверка, `~` — только source/static-пин, `native` — есть GameTest, но агентский нативный прогон не выполнялся, `✗` — отдельной проверки нет.

---

## 1. Обозначения и константы

Все внутренние значения — **nZt/с** (`RadUnits.java:15-22`: `MILLI=10⁶`, `UNIT=10⁹`).

| Символ | Смысл | Источник |
|---|---|---|
| `E` | пресетная эмиссия одного блока/предмета | `RadSources.java:35-145` |
| `C` | `contamination` чанка | `ChunkRadiationData.java:72,161-163` |
| `V` | полный фон чанка `= baseline + C` | `ChunkRadiationData.java:104-107` |
| `F` | `Containment.factor` — исходящий экран контура, `0…1` | `Containment.java:101`, `RadiationContour.java:101-103` |
| `P` | `Σ preset_i × shielding_i × count_i` | `RadiationCluster.java:115-127` |
| `Lmax` | `max(induced_i × shielding_i)` — **на предмет**, без count | `RadiationCluster.java:119-122` |
| `T` | `chunkTarget = P + Lmax` | `RadiationCluster.java:26` |
| `S` | `total = P + Σ induced_i × shielding_i × count_i` — полная доза | `RadiationCluster.java:126` |
| `k` | `RadMaterials.itemFactor` — приём наведёнки предметом | `RadMaterials.java:175-204` |

Ключевые числа: `INVENTORY_TO_CHUNK_RATE=0.01`, `INVENTORY_TRANSFER_EFFICIENCY=0.95`, `CHEST_TO_CHUNK_RATE=0.01` (`RadiationSystem.java:63-65`); `CHUNK_DOSE_WEIGHT=0.1`, `NZT_PER_PERMILLE=1.35e8`, `DOSE_CLEAR_FLOOR=100`, `RECOVERY_RATE=0.002`, `SHED_TO_CHUNK=0.2`, `SHED_CEILING=100 mZt` (`RadiationSystem.java:55-74`); `CHUNK_THRESHOLD=30 mZt`, `Policy.NORMAL=1 / LEAD=0.2` (`RadiationCluster.java:11-20`); `GROWTH=1.25`, `SELF_DISCHARGE=0.25`, `UPTAKE_COST=0.0013`, `INDUCED_CAP=5 Zt`, `DISCHARGE_ANCHOR=33 mZt`, `CLEANUP_FLOOR=1 mZt`, `INDUCED_REEMIT=0.75` (`ItemRadioactivity.java:11-31,263`); `CONTENT_CHUNK_FACTOR=0.2` (`ThirdLeadChestBlock.java:42`); `CarrierItem.RADIOACTIVITY_FACTOR=0.60` (`CarrierItem.java:21`); `PLAYER_PERIOD_TICKS=20`, `PLACED_UPDATE_PERIOD_TICKS=400` (`RadiationSystem.java:79`, `ChunkRadiationData.java:58`).

`transferTowardChunk(C, X, r) = max(X − C, 0) × r` (`RadiationSystem.java:456-458`).

---

## 2. Схема потоков

```text
  preset блока ──► placed/chunk ──(×F, 5%/20с)──► C ──(×0.2 порог 30mZt)──► наведёнка предмета
  preset предмета ─┐                                      ▲                        │
  induced  ────────┼─► кластер владельца ──(0.01×F)───────┘                        │
                   │        │                                                      │
                   │        ├──► S (полная эмиссия) ──► доза игрока ◄── 0.05×(E+ΣS_чужих)
                   │        └──► T = P+Lmax ──► исход в чанк                      │
                   └──► 0.75 (INDUCED_REEMIT) только на перенос «предмет→предмет»
  контур F: ТОЛЬКО исходящие потоки; внутренняя доза и входящий фон чанка через F не режутся
```

---

## 3. A. Блок ↔ чанк ↔ контур

| ID | Взаимодействие | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| A1 | Радиоактивный блок поставлен в открытом чанке | `E = RadSources.blockEmission(state)`; `placed[key] += E`; раз в 400 тиков `C ← max(0, C + (Σplaced×F − C)×0.05)`, затем `C ← 0.99·C`, затем `Σ(C−C_сосед)×0.025` | Индекс идемпотентен; заражение сходится к эмиссии, но **всегда** теряет 1 % за проход; при `F=1` защиты нет; диффузия консервативна | `RadSources.java:127-136`; `RadiationSystem.java:649-686`; `ChunkRadiationData.java:273-298,312-413` | `~` `node_perf_rad_test.py`, `reactor_radiation_trace_test.py` |
| A2 | Блок сломан / заменён / уехал поршнем | `placed[key] = max(0, placed − E)`; `reconcilePlacedSources` сверяет индекс с миром, поиск смещения `±12` | Выгруженный чанк = UNKNOWN, не «потеря»; эмиссия берётся из `placedEmission`; перенос не принуждает загрузку | `RadiationSystem.java:656-686`; `ChunkRadiationData.java:421-502` | `~` `#24/#25` пины, `radiation_contour_test.py` |
| A3 | Блок **в замкнутом контуре**, выход в чанк | `F = min(1, Σ_лучей Π_{l<8} factor_l / walls)`; `target = ΣE × F` | Контур режет **только выход**; средневзвешенный по эмиссии фактор, бюджет 16 проб на чанк с ротацией каретки, замороженный factor для остальных | `RadiationContour.java:57-119`; `Containment.java:69-101`; `ChunkRadiationData.java:509-577` | `✓` `RadiationContourSelfTest` (160 checks), `inside_exposure_test.py` |
| A4 | Блок в контуре → прямая доза occupant | `0.05 × Σ E_блоков(interior)` | `F` **не применяется**: полная герметизация (`F=0`) не обнуляет внутреннюю дозу; стоящий снаружи за стеной не получает дозы | `Containment.java:103-134`; `RadiationContour.java:25` | `✓` `RadiationContourSelfTest.java:96-123` |
| A5 | Природный фон чанка | `V = baseline + C`; `baseline` ленивый: 1–10 nZt, `desolation` 1–5 mZt, бочка отходов 1–10 mZt; руда даёт разовый `10–80 nZt` в `C` | Руда **не** вечный baseline; чанк может отмыться за AFK; первый скан не форсит чтение мира | `ChunkRadiationData.java:104-107,252-268,579-640` | `~` `node_perf_rad_test.py` |
| A6 | Текучий кориум / форма блока | `molten_corium` излучает только при `fluidState.isSource()` | Растёкшаяся жидкость не фонит как источник | `RadSources.java:127-136` | `✓` `liquid_fire_radiation_test.py` (142 checks) |
| A7 | `BlockItem` на полу / в рамке | `target = E × count × F`; `r = 1 − 0.95^(Δt/400)`; `ΔC = (target − C)·r` | Та же модель 5 % за 20 с, что у поставленного блока; первый скан только заводит таймер | `RadiationSystem.java:517-548`; `ChunkRadiationData.java:189-201` | `~` `_radiation_contracts.py flows()` |

---

## 4. B. Геометрия контура, кэш, инвалидация

| ID | Взаимодействие | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| B1 | Замкнутость полости | BFS 14-связность (6 граней + 8 телесных диагоналей), `RANGE=32`, `VISIT_CAP=2048` | «Звезда» из 6 осевых стен **не** замыкает; одна диагональная щель → `OPEN`; ребро-щель 1-клеточной полости — принятый остаток | `RadiationContour.java:45-53,83-94` | `✓` `RadiationContourSelfTest.java:74-87` |
| B2 | Открытая дверь / люк / `contour_seal` | `aperture && OPEN ⇒ AIR` | Открытый проём разрывает контур; закрытая дверь учитывается **своим материалом** | `Containment.java:236-249` | `✓` `RadiationContourSelfTest.java:67` |
| B3 | Чистые seal-блоки на границе | `if seals·100 > (walls+seals)·50 ⇒ sum += seals` | Оболочка «почти из дверей» не получает защиту одного экранирующего блока | `RadiationContour.java:72-100` | `✓` `RadiationContourSelfTest.java:42-54` |
| B4 | Толщина стены | до `MAX_LAYERS=8` по **каждому** outward normal; `product < 1e-3 ⇒ 0` | Воздух/открытая дверь/UNLOADED прерывают луч; нет глобального флага «второй слой где-то есть» | `RadiationContour.java:106-119` | `✓` `RadiationContourSelfTest.java:42-54` |
| B5 | Радиоактивный блок как стена | `if emission>0` → узел проходится, добавляется в `interior` | Куча источников внутри не прячет внутренние блоки и не подменяет реальную свинцовую стену | `RadiationContour.java:69-82,95-103` | `✓` `RadiationContourSelfTest.java:120` |
| B6 | Свинцовый поршень в анимации | `moving_piston` → резолв в `THIRD_LEAD_PISTON_HEAD` | Два тика анимации не превращают защитную стену в технический блок | `Containment.java:198-234` | `~` `radiation_contour_test.py` static |
| B7 | Незагруженный чанк / вне высоты | `UNLOADED ⇒ OPEN`; `hasChunk` guard; `insideDose=0` | Неизвестная граница не доказывает герметичность и не даёт внутреннюю дозу; принудительной загрузки нет | `Containment.java:61-66,74-84,105-109`; `RadiationContour.java:68` | `✓` `RadiationContourSelfTest.java:123-126` |
| B8 | Изменение блока | `removeIf` по кубу `DEPENDENCY_RANGE = RANGE+MAX_LAYERS = 40` от `origin` | Инвалидация после **фактической** мутации мира; `cell(before)==cell(after)` — no-op; вызовы вне server thread откладываются | `Containment.java:47-49,251-267`; `LevelChunkCleanRoomMixin.java:16-24` | `~` `radiation_contour_test.py` |
| B9 | Chunk load / unload / level unload | `CACHE.remove(level)` — **весь уровень** | Любое, в т.ч. далёкое, chunk-событие сбрасывает все контуры мира; TTL 400 тиков, `CACHE_MAX=4096` → `cache.clear()` | `Containment.java:47-48,54,86,269-280` | `~` `radiation_contour_test.py`; **профиля нет** (`D-PERF12`) |
| B10 | Переиспользование снимка полости | alias `Entry` по всем `interior` позициям, если `enclosed && (!start.barrier() || start.emission()>0)` | Один просчитанный контур для движущегося игрока и всех источников внутри; твёрдый контейнер-origin не алиасит комнаты по разные стороны | `Containment.java:89-97` | `✓` `RadiationContourSelfTest.java:121` |

---

## 5. C. Игрок

| ID | Взаимодействие | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| C1 | Собственный инвентарь | `roots = items + armor + offhand`; `inspect → step` каждые 20 тиков | Меню, курсор и эндер-сундук **не** физические владельцы; тик не зависит от открытого GUI | `RadiationSystem.java:124-161,358-365` | `✓` `_radiation_contracts.py scheduler/external_storage` |
| C2 | Игрок **в контуре** (наблюдатель) | `insideDose = 0.05×(ΣE_блоков + ΣS_чужих физических владельцев)` | Свой инвентарь исключён и уже учтён отдельно полной эмиссией; удалённый ender не источник; `F` не режет; observer в стене ⇒ 0 | `Containment.java:103-192` | `✓` `inside_exposure_test.py` (57 assertions) |
| C3 | Складование дозы | `raw = S + 0.1·V + insideDose`; `raw ×= psyche`; `suit = hazmat × absorption`; `acc += raw·suit`; `gain = ⌊acc/1.35e8⌋ ‰` | Порядок строго `insideDose → psyche → hazmat → absorption`; accumulator хранит дробь | `RadiationSystem.java:226-252`; `PsycheChemical.java:74,242-245` | `~` `radiation_contour_test.py:54` (порядок) |
| C4 | Хазмат | `worn=4 ⇒ 0.60`, иначе `0.05·worn`; `shield −= 0.01·(mZt−20)`; `factor = 1 − min(0.60, shield)` | Горячий источник пробивает костюм: при `shield ≤ 0` фактор возвращается к 1.0 | `Hazmat.java:58-64,96-112` | `~` `radiation_behavior_test.py` |
| C5 | Абсорбция дозы | `cut = min(100, 30 + level²)`; `factor = (100−cut)/100` | I → −31 %, II → −34 % **новой** дозы; текущую шкалу не трогает | `RadiationSystem.java:240-248`; `RadDose.java:54-56,164-167` | `~` `radiation_behavior_test.py` |
| C6 | Спад шкалы | если `raw·suit < 100 nZt/с`: `shedAcc += scale×0.002`, целые ‰ | Спад **только** без облучения в этот тик; 20 % снятого уходит в чанк | `RadiationSystem.java:254-266,338-345` | `✓` `radiation_engine_test.py` (catchup/feedback) |
| C7 | Спад / очищение → чанк | `ΔC = permille × 1.35e8 × 0.2 × max(1 − C/100mZt, 0) × F_игрока` | Мягкий потолок 100 mZt не даёт петле «чанк→шкала→спад→чанк» разогнаться; мгновенные срезы ДТПА/Пентасина **не** передаются | `RadiationSystem.java:338-353`; `RadCleanse.java:49-80` | `~` `RAD_WORLD.md:43-70`; `radiation_behavior_test.py` |
| C8 | Игрок → чанк (выход инвентаря) | `ΔC = 0.01 × 0.95 × F × max(T − C, 0)` | `F` считается только если `T>C ∨ спад ∨ cleanse ∨ trace`; исходящий экран не уменьшает входящий фон | `RadiationSystem.java:317-327` | `✓` `_radiation_contracts.py flows()`, `radiation_engine_test.py` |
| C9 | Химия / некроз / эффекты | `chemical += dose/1.35e8`; `RadSickness.tick(scale)`; `Necrosis.tickAir` **каждый** тик | Пороги 50/200/500/800 ‰; некроз с 300 ‰; «Зуд III» даёт ×1.20 к дозе | `RadiationSystem.java:128-130,293-315`; `RadDose.java:41-52`; `PsycheChemical.java:74` | `~` `PSYCHE`-профиль, `radiation_behavior_test.py` |
| C10 | Смерть / выход | шкала → 0, все per-UUID карты очищаются | После смерти облучение сбрасывается; прочие шкалы `copyOnDeath` | `RadiationSystem.java:552-592` | `native` `RadiationGameTests.java` |
| C11 | Другой игрок в той же полости | его физический инвентарь в `roots` room scan, `entity != observer` | N наблюдателей ⇒ N полных сканов содержимого за тик; собственный вклад не переиспользуется как чужой | `Containment.java:139-192` | `✓` `inside_exposure_test.py`; **профиля N нет** |

---

## 6. D. Предметный кластер

| ID | Взаимодействие | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| D1 | Природный пресет | `ingot=base`, `nugget=base/9`, `dust=2·base`, `block=9·base`; точечные изотопы/топлива/шлаки/кориум | Стопка = `per_item × count`; ид предмета вне `gonzotech` ⇒ 0 | `RadSources.java:35-145` | `✓` `rad_materials_audit.py` (340 items / 295 blocks) |
| D2 | Выбор сильнейшего чужого источника | `prefix/suffix max` по `full=(preset+induced)·shielding`; `other = max(prefix[i], suffix[node.end]).transfer × policy.internalTransfer` | Исключается сам объект **и всё его дерево**; равные источники в других владельцах остаются; count не делает стопку «сильнейшим единичным» | `RadiationCluster.java:43-68,83-103`; `ItemSourceSnapshot.java:18-41` | `✓` `radiation_engine_test.py` sourceSelection, `RadiationGameTests.java:277,314` |
| D3 | Рост наведёнки | `target = min(max(0, source − intrinsic), 5 Zt)`; `seeded=max(cur,1mZt)`; `uptake=(1−0.0013)^(seeded/1mZt)`; `next = discharge(min(seeded + seeded·0.25·(1−seeded/target)·k·uptake, target))` | Равновесие **всегда ниже цели**; «предметы держат друг друга» невозможно; старт 1 mZt на предмет | `ItemRadioactivity.java:61-97` | `✓` `radiation_engine_test.py` (11 групп / 450609 assertions) |
| D4 | Саморазряд / спад в покое | `discharge(x) = x·(1 − 0.25·tanh(x/33mZt))`; `restDecay` ≤1024 шага, floor 1 mZt | Ряд 5→3.75→2.81; пропущенное время догоняется **пассивным** спадом, не историческим нагревом | `ItemRadioactivity.java:105-123,142-187` | `✓` `radiation_engine_test.py` catchup |
| D5 | Входящий фон чанка | `incoming = V × policy.internalTransfer`; `chunk = incoming > 30 mZt ? incoming : 0`; `source = max(other, chunk)` | **Строгий** `>30 mZt` после ×0.2; `F` контура к входу не применяется; холодный чанк не греет горячий предмет | `RadiationCluster.java:94-103` | `✓` `RadiationGameTests.java:431-453`; `radiation_engine_test.py` shieldAndChunk |
| D6 | Суммы и цель чанка | `S = P + Σ induced_i·shielding_i·count_i`; `T = P + Lmax` | Доза/панель суммируют все предметы; `chunkTarget` берёт **максимум наведёнки на предмет**, без count | `RadiationCluster.java:23-27,115-127` | `✓` `radiation_engine_test.py` chunkTarget |
| D7 | Переизлучение наведёнки | `transfer = (preset + induced·0.75)·shielding`; доза/чанк считаются полной | 0.75 действует только на перенос «наведёнка → предмет», не на дозу игрока | `ItemRadioactivity.java:263-295`; `RadiationCluster.java:89-90` | `✓` `radiation_engine_test.py` transfers |
| D8 | Щипцы / ковш (`CarrierItem`) | груз `×0.60`; `preset(carrier)=0`; cargo пишется в `CONTENTS` sidecar | Двойной фильтр: пресет ×0.6, наведёнка ×0.75×0.6; содержимое не облучает свою тару | `CarrierItem.java:21-38`; `RadiationCluster.java:55-61,72-74`; `ExternalRadiation.java:60-72` | `✓` `RadiationGameTests.java:99`; `radiation_engine_test.py` nested |
| D9 | Шалкер / связка / вложенный контейнер | `CONTAINER + BUNDLE_CONTENTS`, глубина `<4`, `IdentityHashMap` dedup | Вложенное хранилище **не прячет** содержимое от дозы; экран только у носителя; вложения не облучают друг друга | `RadiationCluster.java:43-68`; `RadSources.java:154-183`; `RadiationOwners.java:47-56` | `✓` `RadiationGameTests.java:331,390,533` |
| D10 | Материал предмета | `k = RadMaterials.itemFactor(stack)`; руды/готовое снаряжение/ваниль-«lead» ⇒ 1.0 | Свинец `0.02`, вольфрам `0.003`, барий `0.10`, борное стекло `0.008`; сырьё получает 30 % экранирующего процента слитка | `RadMaterials.java:38-124,175-258` | `✓` `rad_materials_audit.py` |
| D11 | Запрет мутации стека | `if (count != stack.getCount()) throw IllegalStateException(...)` | Ни `setCount/shrink/grow/set/remove` в ядре; динамическая радиация **не** в components/NBT | `RadiationCluster.java:107`; `_radiation_contracts.py external_storage()` | `✓` `radiation_behavior_test.py` |

---

## 7. E. Мировые владельцы и расписание

| ID | Взаимодействие | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| E1 | Container BE / PetBowl / экипировка / Container entity | `ΔC = 0.01 × F × max(T − C, 0)` | Один скан на чанк раз в 20 тиков, 20 фаз по `hash(chunkKey)`, только `shouldTickBlocksAt` + `getChunkNow` | `RadiationSystem.java:388-434,594-637` | `~` `_radiation_contracts.py scheduler()` |
| E2 | Двойной сундук | `chestOwners` → каноническая половина, `roots` из обеих | Один кластер/владелец; вторая половина не грузится принудительно | `RadiationSystem.java:399-404,436-453` | `native` `RadiationGameTests.java:506` |
| E3 | Обычный `ItemEntity` / `ItemFrame` | `ΔC = 0.01 × F × max(T − C, 0)` | Конечный предмет идёт обычным путём; BlockItem — отдельная модель A7 | `RadiationSystem.java:484-548` | `~` `node_perf_rad_test.py` |
| E4 | Живая сущность / Container entity | `RadiationOwners.slots(entity)` +EquipmentSlot | Для `LivingEntity` позиция источника — глаза; для пола/рамки — `blockPosition` | `RadiationSystem.java:493-513`; `Containment.java:171-186` | `✓` `inside_exposure_test.py` |
| E5 | Много кластеров в одном чанке | `ΣΔC ≤ max(0, maxTarget − C)` | Складываются скорости, но не могут перескочить максимальную цель и затем «перекормить» все предметы | `RadiationSystem.java:425-430` | `✓` `radiation_engine_test.py` max_target/clamp |
| E6 | Эндер-сундук | в `RadiationOwners.slots(Player)` **только для persistence** | Не входит в `playerSlots`, не в room scan, не «физический» источник полости | `RadiationOwners.java:22-25`; `Containment.java:174-179`; `RadiationSystem.java:358-365` | `~` `_radiation_contracts.py` |
| E7 | GUI / открытое меню | view, не владелец | GUI не объединяет физику инвентаря и сундука; радиация не ставится на паузу | `RadiationSystem.java:145-150,358-365`; `RadiationPanel.java:18` | `✓` `radiation_behavior_test.py` |
| E8 | Chunk unload | удаление ключей из `ACTIVE_CHUNKS`, `LAST_CONTENT_SCAN`, `LAST_FLOOR_BLOCK_SCAN` | Нет полной чистки карт на каждом скане; записи расписания удаляются точечно | `RadiationSystem.java:608-616` | `~` `_radiation_contracts.py scheduler()` |
| E9 | Чистая комната | общий только хук `setBlockState` | `CleanRoomSystem.blockChanged` и `Containment.blockChanged` — **независимые** системы; чистота воздуха не меняет радиационный контур | `LevelChunkCleanRoomMixin.java:16-24`; `CleanRoomSystem.java:43-62`; `Containment.java:32-34` | `✓` `cleanroom_test.py` (3850 checks) |

---

## 8. F. Свинцовый ящик с предметами в контуре (отдельный сценарий автора)

Тройное различие каналов — самое тонкое место матрицы.

| ID | Канал | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| F1 | Внутри кластера: чужой предмет | `other = strongestOther × 0.2` | `Policy.LEAD.internalTransfer=0.2` режет межпредметный перенос | `RadiationCluster.java:13-20,102`; `RadiationSystem.java:405-407` | `✓` `RadiationGameTests.java:431-439` |
| F2 | Внутри кластера: фон чанка | `incoming = V × 0.2`, затем **строго** `>30 mZt` | Внешние 100 → 20 и 150 → 30 mZt **не** дают наведёнки; >150 mZt проходит; `F` не применяется | `RadiationCluster.java:96-97` | `✓` `RadiationGameTests.java:440-453` |
| F3 | Выход в чанк | `ΔC = 0.01 × max(0.2 × T × F − C, 0)` | `CONTENT_CHUNK_FACTOR` применяется **к цели до вычитания C**; у обычного сундука `0.01×F×max(T−C,0)` — **асимметрия при C>0**, зафиксированная в `RAD_WORLD.md:31` | `RadiationSystem.java:412-417`; `ThirdLeadChestBlock.java:42` | `✓` `_radiation_contracts.py flows()`; `lead_chest_test.py` (54 пина) |
| F4 | Прямая доза полости | `0.05 × RadiationCluster.inspect(roots).total()` | `inspect` не принимает `Policy`; `CONTENT_CHUNK_FACTOR` к room-dose **не применяется**. Ящик экранирует выход в чанк и визуал, но не внутреннюю экспозицию occupants | `Containment.java:155-160,191`; `RadiationCluster.java:76` | `✗` отдельного теста «lead chest + insideDose» нет (`inside_exposure_test.py` не содержит `lead`/`0.2`) |
| F5 | Визуал прибором | `emission ×= 0.2` для содержимого ящика | Снимок `visualSources` ослаблен, но это картинка, не доза | `ChunkRadiationData.java:239-245` | `~` `lead_chest_test.py:96` |
| F6 | Вложения в свинце | коэффициент владельца применяется **один раз**, не на каждой глубине | Нет двойного ×0.2 для шалкера внутри ящика | `RadiationCluster.java:96-102`; `RAD_ITEMS.md:116` | `✓` `radiation_engine_test.py` shieldAndChunk |

> F4 — не объявляется дефектом: это разнесённые каналы, задокументированные как «обычное вложенное хранилище не становится защитой». Но пакет `D-PERF12` требует сохранить «экранирование и коэффициенты свинцового ящика», поэтому комбинированный сценарий должен быть явно принят автором и запинен детерминированным сравнением до любой оптимизации.

---

## 9. G. Переносы, копии, сохранение

| ID | Взаимодействие | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| G1 | `ItemStack.copy()` | `ExternalRadiation.copy(from,to)` + `RadiationCopies.detachNested` | Доза копируется в внешний слой; вложенные `CONTAINER`/`BUNDLE` получают **новые объекты** до глубины 4; исходник не меняется | `ItemRadiationCopyMixin.java:11-14`; `ExternalRadiation.java:34-48`; `RadiationCopies.java:16-40` | `✓` `RadiationGameTests.java:27,533`; `radiation_engine_test.py` copies |
| G2 | Слияние стопок | `merge = (a·n_a + b·n_b)/(n_a+n_b)`, `tick = max(t_a,t_b)` | Взвешенное по количеству, не сумма; адаптеры: Inventory, Slot, Hopper, SimpleContainer, ItemEntity, ItemHandler, InvWrapper, **SidedInvWrapper**, Bundle | `DoseMath.java:9-13`; `ExternalRadiation.java:49-59`; `mixin/*RadiationTransferMixin.java` | `✓` `radiation_fence_test.py`; `RAD_ITEMS.md#crit-2` (334) |
| G3 | Разделение / QUICK_CRAFT | `MenuDoseTransaction`: `Lot` на исходный слот, `Destination` на результат, `rate = Σ(dose·n)/count` | Provenance по identity; старый слот резервируется до назначения копии курсора; count не «чинится» | `MenuDoseTransaction.java:20-93`; `MenuRadiationTransferMixin.java:23-26` | `✓` `radiation_fence_test.py` (exact328 handler отведён), `RadiationGameTests.java:38,172` |
| G4 | Крафт / печь / новый материал | `ExternalRadiation.dilute(dest, added)` ⇒ разбавление нулём | Выход рецепта — **новый** материал; несоотнесённый выход не наследует дозу удалённого ингредиента | `FurnaceRadiationOutputMixin.java:9-11`; `ExternalRadiation.java:56-59`; `MenuDoseTransaction.java:89-92` | `~` `radiation_engine_test.py` actualFlows |
| G5 | Bundle / carrier (`custom`) | `MenuDoseTransaction.customTransfer()` → `close()` без reconcile | У этих адаптеров явная политика переноса; массовый reconcile не перетирает её | `MenuRadiationTransferMixin.java:19-21`; `MenuDoseTransaction.java:61` | `✓` `RadiationGameTests.java:99` |
| G6 | Creative | `CreativeRadiationBridge` + Fence `begin/end`, `ACK`, fingerprint | Сервер знает дозу; повторный/устаревший `end` не закрывает новый жест и не двигает ACK | `RadiationViewNetwork.java:23-76`; `RadiationPanel.java:30-53`; `CreativeRadiationTransferMixin.java` | `✓` `radiation_fence_test.py` + 7 мутаций |
| G7 | Сохранение | sidecar `GonzoItemRadiationV2` в NBT **владельца** (BE/Entity), вложенные ≤4 | При load сверяются `Item` и `Count`; пустые слоты сохраняют стабильные адреса; в ItemStack components доза не пишется | `RadiationOwners.java:18,36-90`; `BlockEntityRadiationSaveMixin.java`; `EntityRadiationSaveMixin.java` | `native` `RadiationGameTests.java:83,201` |
| G8 | Смена владельца / перенос | `ExternalRadiation` привязан к экземпляру `ItemStack` (WeakHashMap) | После переноса кэш контура обязан пересчитаться; «снимок» не может хранить живые изменяемые ссылки | `ExternalRadiation.java:15-16`; `Containment.java:137-138` | `~` `radiation_behavior_test.py`; **`D-PERF12` Этап 2** |

---

## 10. H. Сеть, панель, диагностика

| ID | Взаимодействие | Формула | Ожидаемое поведение | Кодовое подтверждение | Покрытие |
|---|---|---|---|---|---|
| H1 | Клиентская панель | `view.ack >= sequence` ∧ `menu` ∧ `stateId` ∧ `fingerprint` ⇒ `rate`, иначе `null` | `visible(null)=false` — панель **скрывается**, а не показывает `0`/`<?>`; мерцания неизвестных данных нет | `RadiationPanel.java:24-73`; `RadiationPanelLayout.java:15-17` | `native` `RadiationGameTests.java:348,480` |
| H2 | Приборный визуал | `visualSources(center,18)` ≤64, payload ≤128; `ratio = emission/675 mZt` | Read-only снимок; выгруженный чанк даёт last-known, не грузит мир; логарифмическая калибровка плотности тумана | `ChunkRadiationData.java:216-249`; `RadiationVisualPayload.java`; `RadiationVisualClient.java:52-85` | `~` `lead_chest_test.py` |
| H3 | `/gonzotech debug getdose` | `RadiationCluster.inspect(playerSlots)` без тика + `Containment.result` + `sourcesInChunk` | Шкала/чанк/кластер/сильнейший/`chunkTarget`/`F`/`insideDose`; trace 5 с; `sourcesInChunk` — полный 16×16×height скан (debug only) | `RadiationDebugCommand.java:65-129`; `RadiationTrace.java:23-24` | `~` `reactor_radiation_trace_test.py` |
| H4 | `/gonzotech debug purge` | `baseline=0`, `contamination/placed/placedPos` удалены в радиусе 8 чанков | Предметы и шкала игрока **не** очищаются; сообщение явно это указывает | `ChunkRadiationData.java:114-135`; `RadiationDebugCommand.java:50-63` | `~` `RAD_WORLD.md:81` |
| H5 | LEDGER | `server Σ = menu Σ + ground Σ` против `client Σ` | Арбитр «дюп vs призрак»: наблюдатель, **не** clamp; Q-дюп на земле ловится ростом мировой Σ | `RadiationSystem.java:100-108,163-210` | `✓` `radiation_behavior_test.py` |
| H6 | Debug-лог прохода | `System.nanoTime()` шлюз 1 строка/с на игрока | На tickRate 2000 лог не превращается в 100 строк/с; это **не** профилировщик | `RadiationSystem.java:44-47,212-223` | `~` `radiation_behavior_test.py` |
| H7 | `RadiationTrace` (команда → лог) | `DURATION_TICKS=100`, `CHUNK_RADIUS=1` | 5-секундная трасса по блокам, кластерам, чанкам, дозе; не влияет на расчёт, кроме принудительного `F` при `tracing` | `RadiationTrace.java:23-24`; `RadiationSystem.java:269-287,320` | `~` `reactor_radiation_trace_test.py` |

---

## 11. Итоги валидации (прогон на `e0e8cb7`)

Восстановлена инфраструктура: `/tmp/gtvenv` (Python 3.11.2, `Pillow==13.0.0`, `jdk4py==21.0.8.2`, tree-sitter), ECJ 3.39.0 `v20240820-0604` репакован из npm `@ctxo/lang-java-analyzer@0.13.0`; `java -jar … -version` OK. **Байтовое расхождение:** рабочий repack `14 658 971` байт, прежний артефакт `14 659 031`, frozen docs пинят `14 659 037` — это не один и тот же байтовый артефакт, и не Maven SHA `01f5a92…`.

| Проверка | Результат |
|---|---|
| `audit/radiation_engine_test.py` | PASS — 11 групп / 450 609 assertions; 12/12 мутантов отвергнуты |
| `audit/radiation_contour_test.py` | PASS — ядро 160 checks + 9 static integration |
| `audit/inside_exposure_test.py` | PASS — 57 assertions на реальных `insideDose`/`contentsEmission` |
| `audit/radiation_fence_test.py` | PASS — exact328 handler отклонён, Fence/provenance целы |
| `audit/radiation_behavior_test.py` | PASS — wiring/storage/flows/медикаменты |
| `audit/lead_chest_test.py` | PASS — 54 пина раунда 11 |
| `audit/node_perf_rad_test.py` | PASS |
| `audit/reactor_radiation_trace_test.py` | PASS |
| `audit/rad_materials_audit.py` | PASS — 340 items / 295 blocks, 55 семейств |
| `audit/cleanroom_test.py` | PASS — 3850 checks |
| `audit/liquid_fire_radiation_test.py` | PASS — 142 checks |
| `RadiationGameTests.java` | **23** `@GameTest`, нативно не запускались |

Стартовый рассинхрон индекса (`HEAD` снова указывал на `bf5e72e`) вылечен `git fetch` + `git reset --mixed origin/arena/a5aec272-gonzotech`; после этого база кода = `e0e8cb7`, рабочее дерево чистое. Код, ресурсы и `mod_version=0.3.337` не менялись: шаг документально-валидационный, доставлен в ветку `arena/a5aec272-gonzotech` тем же `one_patch` 0.3.337. Синхронные записи — [CURRENT](docs/CURRENT-STATE.md#rad-valid1), [HISTORY](docs/HISTORY.md#rad-valid1), [AUDIT](docs/AUDIT.md#rad-valid1), [RAD_WORLD](docs/RAD_WORLD.md#rad-valid1), [RAD_ITEMS](docs/RAD_ITEMS.md#rad-valid1), [CAPABILITIES](docs/CAPABILITIES.md#rad-valid1), [DEFFERED](docs/DEFFERED.md#d-perf12).

**Границы доказательств:** всё выше — Python/ECJ-стабы/headless-core/static-пины. Нет нативной Gradle/NeoForge/JAR-сборки, Minecraft-клиента/сервера как агентского прогона, применения Mixins в рантайме, GameTest-прогона, TPS/аллокационного профиля, GPU-сценария. `SKIP ≠ PASS`. Исторические `D-AUD1`, `selfTest3FAIL`, неиспользуемый `G` в `third_grinder` не скрывались; текущий полный gate 0.3.337 с новым пином матрицы — 101 PASS / 0 SKIP / 0 FAIL / 101 (до добавления `audit/radiation_matrix_test.py` — 100/0/0/100).

---

## 12. Пробелы, которые надо закрыть в радиационном блоке

1. **Нет измерений** (`D-PERF12`, Этап 1): ни contour rebuild, ни source scans, ни повторных наблюдений в тике, ни стоимости/объёма копий `MenuDoseTransaction`; нет baseline/среднего/хвоста времени и аллокаций, нет TPS-замера. Лечение выбирать нельзя.
2. **F4** — «свинцовый ящик + контур + внутренняя доза» не запинен отдельным детерминированным тестом.
3. **F3** — асимметрия `0.01×max(0.2·T·F−C,0)` против `0.01·F·max(T−C,0)` при `C>0` зафиксирована в docs, но не имеет отдельного численного comparing-теста старого/нового расчёта.
4. **B9/C11** — полный `CACHE.remove(level)` на любой chunk event и N полных сканов комнаты на N наблюдателей подтверждены кодом, но не профилированы.
5. **B8** — `Collections.synchronizedMap(WeakHashMap)` + составные `computeIfAbsent`/`put` без общей блокировки; конкурентность не проверена, не заявлена как доказанный баг.
6. **A3** — бюджет 16 проб `screenFactor` с замороженным factor: при >16 источниках экранирование может отставать; отдельного теста «много источников + мерцание» нет.
7. **G8** — «снимок» неизменяемости и индивидуальная проекция наблюдателя (Этап 2) не реализованы; общий physical snapshot с собственным inventory/remote ender запрещён пакетом.
8. **H1/H3** — клиентская панель и актуальность `getdose` покрыты только не запускавшимися GameTest'ами; нативная приёмка необходима.
9. Нет детерминированного сравнения «старый расчёт ↔ новый» на полном наборе: пустые/заражённые предметы, стопки и разделение, два одинаковых источника, несколько наблюдателей, свинец, контуры, фон чанка, быстрые инвентарные действия.

---

## 13. Что в этот блок не входит

`D-PIPE2` и сетевой баланс; физика/баланс и legacy-миграция; CRIT-2 (`SidedInvWrapper`, закрыта в 334); крафты/цепочки, трубы и серверная валидация «сверх всего» — по заданному автором порядку это **следующие** блоки. Запрещено: возвращать динамическую радиацию в ItemStack components/NBT, вводить паузы, косметически срезать количество, расширять шалкерную механику, задавать неподтверждённый «нормальный TPS», лечить одним TTL без полной invalidation.
