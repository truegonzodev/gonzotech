# Машины и производственные цепочки

Актуализировано на runtime **0.3.326**,31.10.2026. Документальная база миграции:0.3.311/42730c3; действующие ресурсные/I/O-контракты ниже.


## Где искать истину

Рецепты верстака — `src/main/resources/data/gonzotech/recipe`. Машинные рецепты — `machines/processing/*Recipes.java`; особые циклы — соответствующий BE. Энергетические параметры — `MachineDefs`, `SecondTierDefs`, `PlotnickDefs` и локальные runtime-константы. Перенос в документацию не является перебалансировкой. Изменение displayRecipes/JEI не должно менять find/consume производственного цикла.

## Системы и маршруты

| Система | Действующая роль | Источник |
|---|---|---|
| Электропечи I/II | Загруженный SMELTING с параметрами своего тира, побочные эффекты плавки отдельно | ElectricFurnaceBlockEntity / SecondElectricFurnaceBlockEntity / SmeltHelper |
| Дробилка | Host/raw/ванильные входы → продукты обработки | CrusherRecipes |
| ЦФ1УР | Рудные семейства и специальные разделения, не изотопный реактор | CentrifugeRecipes |
| Измельчитель | Пыли/древесина/простые материалы; ОЯТ без корпуса → опасные шлаки | GrinderRecipes |
| Пресс | Пластины/провода/ядра/руды и сборка уранового ТВЭЛа с оснасткой | PressRecipes / SecondPressBlockEntity |
| Завод сплавов | Полный состав 5×5 → именной либо custom сплав | AlloyFoundryRecipes / AlloyFoundryBlockEntity |
| Химзавод III | Девять craft-ячеек, катализаторы/GTU, синтезы и графит | ChemicalPlantRecipes / ChemicalPlantBlockEntity |
| Наполнитель | Реакции/наполнение/жёлтый кек/ампулы; не универсальная утверждённая модель взрывов смешивания | FillerBlockEntity |
| Брожение/котёл | Динамическая брага/сусло, спирт/гниль | FluidBlends и соответствующие BE |
| Дистиллятор/ректификатор/кран | Дистиллят → ректификат; фасовка напитков | соответствующие BE |
| ПЦФСОЗ | Кек и с 0.3.253 шлаки → изотопные/вторичные продукты | PCFSOZRecipes / PcfsozBlockEntity |
| Измельчитель II (`third_grinder`) | ОЯТ + расходуемый пустой корпус → умеренные шлаки + бочка | ReprocessingRecipes / ReprocessingGrinderBlockEntity |
| Литографическая фабрика | Три стадии конвейера/три варианта чипа, брак от чистоты | SiliconFactoryBlockEntity / Structure |

**Закатной станок — не отдельная открытая машина.** Роль герметизации/сборки ТВЭЛа перешла прессу; `PressRecipes` обслуживает реальный маршрут. Не возвращать старый список 22.09 в backlog. Отдельный химреактор и опасные универсальные баки остаются неутверждёнными идеями, а не недописанными BE.

## Генерация и многоблоки

Топка/ядерная топка генерируют GTH; ядерная топка не первый реактор. Котёл, Стирлинг, парогенератор, турбина, теплообменники и конденсаторы имеют разные балансы и topology. Номинал одной операции не равен устойчивому потоку через ограниченные I/O-порты. У литографа три материала D взаимозаменяемы во всех вариантах; номер чипа выбирает матрица формы, не материал D (подтверждено автором в DOC-002).

Разрешения узлов задаются правилами конкретного многоблока: экранированные heat/universal узлы не следует отклонять просто из-за тира. Для реактора только разрешённые III-узлы; это не универсальное правило «любой узел подходит везде». Топология/кэш должны уважать загруженность чанка и идентичность BE. Геометрия/формирование/порты — [MULTIBLOCK](MULTIBLOCK.md); транспорт — [CAPABILITIES](CAPABILITIES.md), физика реактора — [REACTORS](REACTORS.md).

## Слоты, перенос и сохранение

`Container.setItem` заменяет значение, не дополняет. Вставка/симуляция/rollback обязаны иметь одного владельца предмета; не «чинить» лишнее количество обрезкой. С 0.3.311 явные ExternalRadiation.merge/dilute сопровождают перенос и чистый выход. Новая обработка не означает наследование всей наведёнки сырья продуктом.

GUI — [SUPER_CRUCIAL_FILE](SUPER_CRUCIAL_FILE.md); прогрессия — [PROGRESSION](PROGRESSION.md); описание рецептов JEI — [JEI](JEI.md). Старый XLSX сохранён как исторический баланс-снимок, не как текущий источник констант.

## BE: владение инвентарём, ресурсами и тиком

`BaseMachineBlockEntity` — `BlockEntity implements Container, MenuProvider`, `NonNullList<ItemStack>` фиксированного размера. База реализует доступ/remove/setItem, stillValid и NBT предметов через ContainerHelper с HolderLookup. Она **не** хранит общую энергию всех машин и **не** задаёт универсальный процесс: буферы, slots/sided I/O, receive*, serverTick и NBT ресурсов принадлежат конкретному BE. Не каждый BE машины обязательно наследует эту базу.

Обычный `setItem(slot, stack)` **заменяет** значение. В базе отдельно существует специальный путь наполненного `CarrierItem` (щипцы/ковш): проверка вместимости, извлечение содержимого в слот, ExternalRadiation.merge при сложении, выброс носителя рядом. Этот путь нельзя обобщать до «setItem всегда добавляет»; существующий clamp не является лечением дюпа. Стороны `WorldlyContainer`, canPlace/canTake и слоты меню проверяются у наследника, а не угадываются по наличию Container.

Серверный tick проверяет доступность входа/выхода, затраты, прогресс, commit/побочные выходы; нельзя списывать ресурсы при одном лишь displayRecipes/JEI-показе. При изменении ресурсов нужен корректный dirty/save путь. Пример: ElectricFurnace.receiveGtu вызывает setChanged только при реальном положительном приёме. База сериализует items; это не автоматическая сериализация произвольных полей наследника.

## Буферы: тип, единицы и границы

| Тип | Представление и ёмкость | Приём/выдача и NBT |
|---|---|---|
| `energy/GtBuffer` | BigInteger в milli: 1 GTU/GTH = 1000 mGTU/mGTH; capacity null или отрицательная = unlimited | receive/extract(long, simulate) возвращают реально принятую/выданную порцию; simulate не меняет amount. Десятичная строка milli в NBT; неверная/пустая строка → 0. amountAsLong и GUI int getters насыщают представление, не меняют запас |
| `energy/ResourceBuffer` | int amount/capacity, clamp в 0…capacity; вода/пар и жидкие продукты обычно mB | long-запрос насыщается до int, затем ограничивается свободным местом/остатком; simulate без записи. NBT int. Единицы задаёт владелец, а не одно только имя класса |
| Локальные поля BE | Не все машины используют buffer-классы | Химзавод: currentGtuMilli (long); наполнитель: currentGthMilli/currentGtuMilli + левые/правые typed tanks, состав/соль; Distiller/Rectifier/WortKettle/FermentationVat имеют свои поля состава и тепла. Читать их receive/save/load, не добавлять второй GtBuffer «по аналогии» |
| Сформированные структуры | Ёмкости зависят от формы | SteamGenCore/TurbineRotor создаются с нулевыми буферами до формирования и пересоздают их по math класса при form; это не реальные всегда нулевые capacities. Реакторная расчётная матрица и буфер воды — разные сущности |

**Capacity ≠ intake ≠ output ≠ расход операции.** Большой BigInteger-запас не даёт бесконечный throughput; long используется для порции. Константы — MachineDefs/SecondTierDefs/NuclearDefs и локальные поля BE; менять их без автора нельзя. Server storage точный, а ContainerData/GtFormat даёт клиентское представление (включая packed mantissa/tier и split-поля для отдельных жидкостей); GUI не источник истины о количестве.

## Синки: реальные интерфейсы приёма

`energy/Sinks` — собственные Java-интерфейсы, **не** автоматически зарегистрированные NeoForge capabilities и не Forge Energy. Sink означает приёмник ресурса; «синк GUI» означает сетевую синхронизацию и не является тем же API. Перечень актуальных методов:

| Интерфейс | Сигнатура | Единицы / смысл |
|---|---|---|
| `GthSink` | `receiveGth(long amount, boolean simulate)` | mGTH |
| `SteamSink` | `receiveSteam(long amount, boolean simulate)` | mB |
| `GtuSink` | `receiveGtu(long amount, boolean simulate)` | mGTU |
| `WaterSink` | `receiveWater(long amount, boolean simulate)` | mB |
| `MashSink` | `receiveMash(long amount, double alcoholPercent, double rotPercent, boolean simulate)` | mB; alcoholPercent и rotPercent |
| `WortSink` | `receiveWort(long amount, double alcoholPercent, boolean simulate)` | mB; alcoholPercent |
| `DistillateSink` | `receiveDistillate(long amount, boolean simulate)` | mB |
| `RectificateSink` | `receiveRectificate(long amount, boolean simulate)` | mB |
| `HotWaterSink` | `receiveHotWater(long amount, boolean simulate)` | mB |
| `PoisonPotionSink` | `receivePoisonPotion(long amount, boolean simulate)` | mB |
| `SulfuricAcidSink` | `receiveSulfuricAcid(long amount, boolean simulate)` | mB |
| `EthyleneSink` | `receiveEthylene(long amount, boolean simulate)` | mB |
| `AminoblazeethanolSink` | `receiveAminoblazeethanol(long amount, boolean simulate)` | mB |
| `FormaldehydeSink` | `receiveFormaldehyde(long amount, boolean simulate)` | mB |


`simulate=true` оценивает текущий приём, `false` делает реальную запись; возвращаемое значение — принятая порция, не свободная ёмкость и не обещание на будущее. Метод без Direction сам по себе не задаёт разрешённую сторону: её проверяет вызывающий routing/порт либо конкретный BE.

Примеры важных исключений:

- Boiler.receiveGth/receiveWater ограничивают **вызов** соответствующим MachineDefs intake и свободным местом; Boiler реализует SteamSink, но receiveSteam всегда возвращает 0. Наличие интерфейса не означает работающий вход пара.
- ElectricFurnace.receiveGtu ограничивает вызов ELECTRIC_GTU_INTAKE и свободный GtBuffer; после commit отмечает BE dirty. Такой Math.min без ledger нельзя называть общим лимитом всех источников за тик.
- SteamGenCore.receiveGthFromPort/receiveWaterFromPort используют formed/root/порт и tick ledger. Это другой контракт, чем простой clamp одного вызова; GTH дополнительно учитывается по портам.
- Mash/Wort переносят состав, а не только объём. Distiller не должен одновременно принимать несовместимые raw-fluid состояния; Filler выбирает совместимый typed tank. simulate не меняет состав/соль/тип и не запускает реакцию.

`Transfer.Receiver.receive(long, boolean)` унифицирует endpoint; `Transfer.distributeAmong` делит бюджет B на N стабильных receivers поровну, остаток раздаёт с rotation. Прямой helper делает commit долей и возвращает фактически moved; источник списывает именно moved. Трубы (`PipeRouting`, `PipeFlowNetwork`) добавляют поиск, лимиты и виртуальные endpoints. Не приписывать всем путям обязательную двухфазную транзакцию: там, где есть simulate, commit всё равно проверяет живое состояние заново. Ограничения кэша/чанков/идентичности — [CAPABILITIES](CAPABILITIES.md), портов — [MULTIBLOCK](MULTIBLOCK.md).

## Реестр BE с ресурсными буферами и точками приёма

Пути относительно `src/main/java/com/gonzotech/machines/`. Ниже **инициализаторы полей**, не развёрнутая смета и не итоговые ёмкости formed-структур. «Локальные поля» означает отсутствие GtBuffer/ResourceBuffer-инициализатора, а не отсутствие ресурса. Перечислены BE, объявляющие эти буферы или public receive*-методы; безресурсные/унаследованные классы не выдаются за отсутствующие в моде.

| BE / исходник | Объявленные буферы: тип, поле, исходное выражение capacity | Public receive*-точки |
|---|---|---|
| [AccumulatorBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/AccumulatorBlockEntity.java) | `GtBuffer gtu: (long) MachineDefs.ACCUMULATOR_GTU_CAPACITY` | `receiveGtu` |
| [AlloyFoundryBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/AlloyFoundryBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.ALLOY_FOUNDRY_GTU_CAPACITY` | `receiveGtu` |
| [BoilerBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/BoilerBlockEntity.java) | `GtBuffer gth: (long) MachineDefs.BOILER_GTH_CAPACITY`; `ResourceBuffer water: MachineDefs.BOILER_WATER_CAPACITY`; `ResourceBuffer steam: MachineDefs.BOILER_STEAM_CAPACITY` | `receiveGth`, `receiveSteam`, `receiveWater` |
| [CentrifugeBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/CentrifugeBlockEntity.java) | `GtBuffer gtu: (long) MachineDefs.CENTRIFUGE_GTU_CAPACITY`; `ResourceBuffer water: MachineDefs.CENTRIFUGE_WATER_CAPACITY`; `ResourceBuffer hotWater: MachineDefs.CENTRIFUGE_HOT_WATER_CAPACITY` | `receiveGtu`, `receiveWater`, `receiveSteam` |
| [ChemicalPlantBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/ChemicalPlantBlockEntity.java) | Локальные поля / иной layout; см. исходник | `receiveGtu` |
| [CobbleGeneratorBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/CobbleGeneratorBlockEntity.java) | `GtBuffer gtu: (long) MachineDefs.COBBLE_GTU_CAPACITY`; `ResourceBuffer water: MachineDefs.COBBLE_WATER_CAPACITY` | `receiveGtu`, `receiveWater` |
| [CrusherBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/CrusherBlockEntity.java) | `GtBuffer gtu: (long) MachineDefs.CRUSHER_GTU_CAPACITY` | `receiveGtu` |
| [DispensingTapBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/DispensingTapBlockEntity.java) | `ResourceBuffer distillate: DISTILLATE_CAPACITY`; `ResourceBuffer wort: WORT_CAPACITY` | `receiveDistillate`, `receiveWort` |
| [DistillerBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/DistillerBlockEntity.java) | `ResourceBuffer water: WATER_CAPACITY`; `ResourceBuffer boilingWater: HOT_WATER_CAPACITY`; `ResourceBuffer distillate: OUTPUT_CAPACITY`; `ResourceBuffer poison: OUTPUT_CAPACITY` | `receiveGth`, `receiveWater`, `receiveMash`, `receiveWort` |
| [ElectricFurnaceBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/ElectricFurnaceBlockEntity.java) | `GtBuffer gtu: (long) MachineDefs.ELECTRIC_GTU_CAPACITY` | `receiveGtu` |
| [FermentationVatBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/FermentationVatBlockEntity.java) | Локальные поля / иной layout; см. исходник | `receiveGth` |
| [FillerBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/FillerBlockEntity.java) | Локальные поля / иной layout; см. исходник | `receiveGth`, `receiveGtu`, `receiveWater`, `receiveRectificate`, `receiveSulfuricAcid`, `receiveEthylene`, `receiveAminoblazeethanol`, `receiveFormaldehyde`, `receiveDistillate`, `receiveMash`, `receiveWort`, `receiveHotWater`, `receivePoisonPotion` |
| [FireboxBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/FireboxBlockEntity.java) | `GtBuffer gth: (long) MachineDefs.BLAST_FURNACE_GTH_CAPACITY` | Нет объявленных public receive* |
| [NuclearFireboxBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/NuclearFireboxBlockEntity.java) | `GtBuffer gth: (long) NuclearDefs.NUCLEAR_FIREBOX_GTH_CAPACITY` | Нет объявленных public receive* |
| [PcfsozBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/PcfsozBlockEntity.java) | `GtBuffer gtu: (long) MachineDefs.PCFSOZ_GTU_CAPACITY`; `ResourceBuffer water: MachineDefs.PCFSOZ_WATER_CAPACITY`; `ResourceBuffer hotWater: MachineDefs.PCFSOZ_HOT_WATER_CAPACITY` | `receiveGtu`, `receiveWater`, `receiveSteam` |
| [PlotnickBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/PlotnickBlockEntity.java) | `GtBuffer gtu: GTU_CAPACITY_MILLI` | `receiveGtu` |
| [PumpBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/PumpBlockEntity.java) | `GtBuffer gtu: (long) MachineDefs.PUMP_GTU_CAPACITY`; `ResourceBuffer water: MachineDefs.PUMP_WATER_CAPACITY` | `receiveGtu`, `receiveWater` |
| [ReactorControlPanelBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/ReactorControlPanelBlockEntity.java) | Локальные поля / иной layout; см. исходник | `receiveWaterFromPort` |
| [RectifierBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/RectifierBlockEntity.java) | `ResourceBuffer distillate: DISTILLATE_CAPACITY`; `ResourceBuffer rectificate: RECTIFICATE_CAPACITY` | `receiveGth`, `receiveGtu`, `receiveDistillate` |
| [ReprocessingGrinderBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/ReprocessingGrinderBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.GRINDER2_GTU_CAPACITY` | `receiveGtu` |
| [SecondAccumulatorBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SecondAccumulatorBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.ACCUMULATOR_GTU_CAPACITY` | `receiveGtu` |
| [SecondCobbleGeneratorBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SecondCobbleGeneratorBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.COBBLE_GTU_CAPACITY`; `ResourceBuffer water: SecondTierDefs.COBBLE_WATER_CAPACITY` | `receiveGtu`, `receiveWater` |
| [SecondElectricFurnaceBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SecondElectricFurnaceBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.ELECTRIC_GTU_CAPACITY` | `receiveGtu` |
| [SecondGrinderBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SecondGrinderBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.GRINDER_GTU_CAPACITY` | `receiveGtu` |
| [SecondLumberBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SecondLumberBlockEntity.java) | `GtBuffer gtu: SecondTierDefs.SECOND_LUMBER_GTU_CAPACITY` | `receiveGtu` |
| [SecondPressBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SecondPressBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.PRESS_GTU_CAPACITY` | `receiveGtu` |
| [SecondPumpBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SecondPumpBlockEntity.java) | `GtBuffer gtu: (long) SecondTierDefs.PUMP_GTU_CAPACITY`; `ResourceBuffer water: SecondTierDefs.PUMP_WATER_CAPACITY` | `receiveGtu`, `receiveWater` |
| [SnaketypeCondenserBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SnaketypeCondenserBlockEntity.java) | Локальные поля / иной layout; см. исходник | `receiveHotWater` |
| [SolarPanelBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SolarPanelBlockEntity.java) | `GtBuffer gtu: MachineDefs.SOLAR_PANEL_GTU_CAPACITY` | Нет объявленных public receive* |
| [SteamGenCoreBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/SteamGenCoreBlockEntity.java) | `ResourceBuffer water: 0`; `ResourceBuffer steam: 0`; `GtBuffer gth: 0` | `receiveWaterFromPort`, `receiveGthFromPort` |
| [StirlingBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/StirlingBlockEntity.java) | `ResourceBuffer steam: MachineDefs.STIRLING_STEAM_CAPACITY`; `GtBuffer gtu: (long) MachineDefs.STIRLING_GTU_CAPACITY`; `ResourceBuffer water: MachineDefs.STIRLING_WATER_CAPACITY` | `receiveSteam`, `receiveGtu` |
| [TurbineRotorBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/TurbineRotorBlockEntity.java) | `ResourceBuffer steam: 0`; `GtBuffer gtu: 0` | `receiveSteamFromPort` |
| [WortKettleBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/WortKettleBlockEntity.java) | Локальные поля / иной layout; см. исходник | `receiveGth`, `receiveMash` |
| [SiliconFactoryBlockEntity](../src/main/java/com/gonzotech/machines/litho/SiliconFactoryBlockEntity.java) | `GtBuffer gtu: CAPACITY_MILLI` | `receiveGtu` |

При изменении BE обновлять этот реестр и соответствующий маршрут выше, отдельно CAPABILITIES/MULTIBLOCK/JEI/GUI при затрагивании их контрактов. Ресурсные сырые значения, тики и слоты не заменяются ссылкой «всё в HISTORY».

## История

0.3.13–16: химия/препараты/линия жидкостей. 0.3.79–89: носители/ПЦФСОЗ. 0.3.109–110: идентификаторы и гейт III. 0.3.142: замена старого парового баланса. 0.3.145–150: графит/урановый топливный маршрут/пресс. 0.3.250–253: отработка/перенос разделения в ПЦФСОЗ. 0.3.311: адаптеры внешнего переноса радиации. Полный лог — [HISTORY](HISTORY.md).

0.3.311, DOC-002: добавлены контракты BE/буферов, все Sinks и конкретные receive-исключения/реестр BE; runtime и баланс не менялись.

## Проверки 0.3.315 — D-AUD1

Проверен не новый баланс, а актуальность audit: NodeKinds домны соответствует утверждённому heat/universal любого поколения (33 layout checks); real PressRecipes/ProcessingRecipeDisplay/SteamGenScreen остаются в compile-гейте. Inherited menu и новые границы зависимостей восстановлены в compile-only stubs. Вырезания реальных классов из typecheck нет; recipes/resources/runtime не менялись. Это не самостоятельное Mojmap/игровое доказательство — AUDIT.

## III-хранилища — действующий контракт 0.3.316 / ENG-001

Баланс в `ThirdStorageDefs`, точный конечный long milli-GTU и независимые input/output clocks в `TickEnergyStorage`. Это локальное хранилище новых устройств, не замена GtBuffer/баланса старых машин.

| Устройство | Ёмкость GTU | Максимум приёма / выдачи GTU/t | Собственная потеря GTU/t | Предметные слоты |
|---|---:|---:|---:|---|
| `third_accumulator` | 230882.0 | 294 / 294 | 0.032 | 3 обычных, любые предметы; стандартные лимиты предмета/Container; save/extract/drop |
| `third_battery` | 68088.0 | 244 / 188 | 0 | 0; переносимый nonstackable item |

Оба лимита **суммарные за game tick**, не на вызов/грань/ветку; simulation не трогает ни запас, ни clocks. III-потеря раз за загруженный тик, без offline catchup; предмет батарейки пассивен и сам не разряжается. Зарядки предметов в трёх слотах **нет** — только будущее назначение. ThirdAccumulator.setItem хранит CarrierItem буквально, без базового unpack, и заменяет значение; обычные stack limits не объявляются исправлением дюпа.

| BE | Владение / API |
|---|---|
| [ThirdEnergyStorageBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/ThirdEnergyStorageBlockEntity.java) | Общий `receiveGtu`, `TickEnergyStorage`, read-only серверный ContainerData, PipeRouting WIRE, компаратор 0…15, NBT long `EnergyMilli` |
| [ThirdAccumulatorBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/ThirdAccumulatorBlockEntity.java) | 3 сохраняемых slots; обычные ContainerHelper и MachineBlock.onRemove; свой menu |
| [ThirdBatteryBlockEntity](../src/main/java/com/gonzotech/machines/block/entity/ThirdBatteryBlockEntity.java) | 0 slots; loadFromItem/saveToItem; placed network, собственное menu |

Батарейка сохраняет `GonzoBatteryGtuMilli` (long milli) в CUSTOM_DATA, не стирая чужие поля; clamped 0…68088000. `getDrops` берёт настоящий BE из LootContextParams и штампует **уже сформированный** собственный item drop. Нет второго ручного playerWillDestroy/drop-пути; действуют обычные правила loot/инструмента/взрыва, creative не создаёт отдельную гарантированную копию. При setPlacedBy charge восстанавливается точно. Форма/горизонтальная ориентация как у канистры, world liquid не принимает. I/II получили только узкое распознавание нового storage при equalization; старые capacities, loss, формат NBT и per-call intake не менялись.

Sync: три независимых поля (milli remainder, units%1000, thousands), каждое 0…999; decode в меню, без signed-short overflow и без округления server storage. GUI III — X.Y через GtUnits.x1. Физический gate III и Creative-группы — PROGRESSION; точные координаты — SUPER_CRUCIAL_FILE. Рецептов нет до авторской сметы D-ENG1; новая графика временная с разрешения автора.

Уточнение III-хранилищ / 0.3.319: устранён compileJava blocker TierThreeCrafting — пропущенный импорт machines.registry.ModMachines. Регистрация обоих устройств и их баланс/BE/I/O не менялись; полный gate III сохранён, рецепты всё ещё ждут автора. Production-класс gate включён в56-class boundary typecheck, отсутствие импорта проверяется негативной компиляцией. Это не доказательство полного Minecraft build/игрового I/O; BUILD-001 закрыта по подтверждению автора28.10.2026; нового native build агентом не выполнялось.

## Блочные модели и новые пути текстур — 0.3.320

После авторского731ff19 модели bore_pipe, geophone, miner_drill, plotnick, seismic_source, work_block и third_grinder используют gonzotech:block/third/ вместо удалённых block/second/ (7JSON/18slots). AccumulatorIII/battery уже использовали third/; их6PNG обновлены автором. Геометрия/грани/facing, blockstate/item IDs, GUI, BE/баланс/I/O и рецепты не менялись. Каталог текстур не устанавливает gameplay tier. Проверены существование всех model texture PNG и обе ссылки world/inventory; клиентская визуальная приёмка отдельно. Авторские PNG неизменны агентом; детали графики — AUDIO_VISUAL.

## PERF-001 — исправления аккумуляторов I/II в0.3.321

В321 по PERF-001/7/8 исправлены I→II half-positive-difference и intake I/II суммарно за gameTime. TickIntakeBudget оборачивает прежний GtBuffer.receive: симуляция read-only, счётчик списывается на реально принятый объём; отдача/освобождение места в том же тике не восстанавливает intake, следующий тик сбрасывает его логически. Capacity/intake/output/loss и decimal Gtu NBT/BigInteger прежние, transient budget не миграция и не новый форматIII. Headless:80605assertions/8mutants на настоящих methods и boundary dispatcher. **28.10.2026 автор подтвердил работу аккумуляторов и суммарного BE max input/t в игре;7/8 закрыты и по его распоряжению [архивированы](archive/editions/0.3.322/README.md).** Полный native build/инструментированные сценарии агентом не запускались; авторская приёмка не приписывается headless harness.

**Разные источники ограничений (напоминание автора28.10.2026):** пропускная способность каждого провода/пути и max input/t приёмного BE — разные ограничения. Два независимых провода с разных сторон по38GTU/t могут дать аккумуляторуIII суммарно76GTU/t, если его собственный input/t и свободная ёмкость позволяют. Если оставшийся суммарный лимит приёмника ниже, он принимает только допустимое; второй источник не создаёт второй BE allowance. Это не требование ограничить весь BE скоростью одного провода, не новый баланс и не повод менять исправный runtime. Учитываются также прежние ограничения источника и потери пути.

## Единичный input-слот ≠ лимит контейнера —0.3.323

Плотник реализует SlotCapacity: слоты9/10 (картыA/B) имеют capacity1, прочие Container-индексы сохраняют прежний runtime max. SeismicSource реализует тот же контракт для TNT-slot0=1. GUI уже ограничивал карты/TNT единицей; теперь ItemRouting, vanilla Hopper admission и NeoForge InvWrapper/SidedInvWrapper знают этот предел до переноса. Из подачи64в пустой единичный слот принимается1, возвращается63; занятый принимает0. Simulation handler не меняет ни inventory, ни dirty, ни поданный stack. Полный склад не превращается в maxStack1, GTU input/output/t и источник/проводные лимиты не менялись.

TNT.setItem больше не реализует «выбросить излишек и скопировать1»: штатная вставка приходит уже допустимым количеством. Вместе с A/B стоит проверка raw replacement: недопустимая новая стопка>1 вызывает IllegalArgumentException, не усечение/дроп. Восстановление старого TNT loadSurplus при загрузке NBT не менялось; новая legacy migration не вводилась. Уже лежащие старые переполнения не срезаются, обновление существующей ссылки не считается новой вставкой.

Другие GUI-return1 сверены: физическое топливо реактора имеет containermax1, ghost-фильтр — образец, не материальная стопка. Условия доступа/формирования, топливо/радиация/реакторный runtime не переделывались. Автор30.10.2026 явно подтвердил: карты внеA/B можно хранить как товар, функционал только вA/B; комментарий не повод ограничивать обычные складские слоты.

Пункт2принят автором в игре30.10.2026 и [архивирован](archive/editions/0.3.323/README.md). Локальные354assertions/10mutationkills/84PASS сохраняются как прежние тестовые результаты; native прогон агентом не заявляется. Код admission323и баланс в следующем шаге не меняются.

## Плотник: virtual setItem только заменяет —0.3.324

Виртуальная ветка setItem теперь не распоряжается вытесненным stack: нет скрытого insert/dump прежнего типа, splitoverflow или самопополнения других ячеек. Явное извлечение/возврат/rollback — у вызывающего кода, существующие removeItem/removeItemNoUpdate передают реальную собственность caller. Допустимое новое содержимое сохраняется какпередано; invalidnewreplacement отвергается до любых изменений, старые состояния не мигрируются.

Автоматизация по-прежнему проверяет типы/депо/добытое/грани/лимиты до вставки; обычныйstock, картыA/B, TNT, GTU buffers/caps/NBT/save/load и баланс не меняются. Контракт setter исправлен и **принят автором31.10.2026**, [пункт11архивирован](archive/editions/0.3.324/README.md). Существование доступного игрового дюпа этим не утверждается. Прежние9363assertions/8assertionmutantkills/85PASS — локальные результаты до авторской приёмки, не новый native прогон агентом.
## Предметные трубы,filter и отсеиватель:loaded-only —0.3.325

Ни один из этих путей не загружает чанки источника/приёмника/линии. Если root/source/path/receiver выгружен, недоставленные предметы остаются у источника либо в буфереfilter; нет новыхtickets/probes/known-endpoint loaders или собственного радиуса игрока. Общий lookup двойного сундука ждёт загруженности обеих половин. Filter pass/reject и запитанныйIIиспользуют loadedграницы; выгруженный отсеиватель не может удалить предмет, штатное удаление через загруженныйIIсохранено. Ordinaryserver lifecycle/другие законные chunk loaders не отключены. Вернувшийся игрок/внешняя загрузка восстанавливает перенос через прежниеTTL40иBFSbudget, не новыйSLA. Порядок54слотов/прогревПлотника5×5/throughput/радиация/баланс не менялись.5принят автором по выгрузке/стопу/возобновлению; блокировка doublechestисправляется отдельно в326и ждёт игры1. Подробности/чек-листы — [CAPABILITIES](CAPABILITIES.md), [CURRENT](CURRENT-STATE.md).

Дополнительнаяграница: уIIсвыгруженнойredstone-окрестностью (консервативные±3блокаX/Zдляvanillaconductor→wire) всёrejectendpointнедоступно, неOFF; ниудаления, нипереносавегоnet. Авторвыбралэтотвариантявно. Iнеqueryитredstoneиостаётсяобычнойпассивнойветкой. Analogуведомлениеуниверсальногоузлаоткладываетсяприunloadedreadокрестности, беззаписи«ужепослано», поэтомупослевнешнейзагрузкиповторяется;самsignal/движениепредметовнеотключены.

## Двойной сундук: живые физические владельцы —0.3.326

FreshCompoundContainerнеравенпоreferenceпредыдущемуview, но может представлять те же27+27слотов. ItemRoutingпроверяет обе физическиеContainerBEвстарой и новойобёртке; обычныйContainerпо-прежнему требует точнуюidentity. Replacementлюбойполовины/split/re-pairне разрешаются по одним координатам. Захваченный доBFSисточник проверяется заново; обаадреса получателя участвуют впрежнемsame-tickshield. Двеобёртки/две половинысвоегоsourceисключаютсяизtargetsкаквтрубах, таквfilter. Нетперестановки54слотов/новогоstorage/NBTмиграции/измененияsidecarпереноса, SlotCapacity/баланс/режимы/лимиты прежние. Acceptedloadedграницы5сохранены.1ждёттестовordinary→double/double→ordinary/double→doubleна326, анеобъявляетсязакрытымпо7498headlessassertions/9mutants. Подробности — [CAPABILITIES](CAPABILITIES.md), [CURRENT](CURRENT-STATE.md).
