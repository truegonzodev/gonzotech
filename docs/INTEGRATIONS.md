# Интеграции и совместимость модпака

База реестра **0.3.311** / `26e2a31`; WorldGuard обновлён на **0.3.332**,01.11.2026. Различаем: встроенный адаптер в коде, подтверждение автора о совместной работе, возможный конфликт и будущую интеграцию. Здесь не заявляется, что Arena запустила модпак/все версии модов.

## Поддерживаемое окружение

| Мод / область | Текущий статус | Основание и ограничения |
|---|---|---|
| JEI | Поддерживается; необязательная клиентская интеграция | `integration/jei/GonzoTechJeiPlugin`, 24 категории. neoforge.mods.toml: optional, CLIENT, AFTER, `[20.0.0.4,)`; build.gradle: compileOnly APIs и runtimeOnly для dev runtime. JEI не упакован в GT; рецепты не progression-filtered. Подробности — [JEI](JEI.md) |
| Simple Fog Control | Хорошо работает с GT по подтверждению автора в этом шаге | Это подтверждение совместимости, не обязательная dependency и не отдельный API-адаптер. Собственные fog/depth/underwater эффекты GT остаются частью визуала; не отключать их ради заявления о совместимости |
| Sodium | Хорошо работает с GT по подтверждению автора | Не обязательная dependency GT. Это не обещание поддержки любого shader pipeline или любой версии Sodium |
| Lithium | Хорошо работает с GT по подтверждению автора | Не обязательная dependency GT; не путать название мода с материалом lithium в контенте GT |
| Шейдеры, заменяющие небо | **Вероятны конфликты** | GT меняет skybox/небесные эффекты (`space/client/SpaceSkyEffects`, солнечные эффекты и mixins). Shader pack, который тоже подменяет небо, может перекрыть или сломать визуал. Это риск, а не доказанный конфликт со всеми шейдерами; универсальная совместимость не обещана |
| Моды генерации мира/биомов | **Возможны конфликты** | GT сам вмешивается в генерацию и biome source. Особенно опасны несовместимые подмены/инъекции источника биомов и нарушение lifecycle генерации. Отсутствие общей библиотеки согласования не доказывает баг другого мода |
| TerraBlender | **Сейчас не поддерживается** | В текущем Java-коде нет адаптера/проверки его наличия; в build.gradle и neoforge.mods.toml нет dependency. Установка TerraBlender в сборку сама по себе не переводит GT на его API. Будущая согласованная задача — [D-INT1](DEFFERED.md#d-int1) |
| WorldGuard / региональные флаги | **Optional адаптер332 реализован; №9 закрыт решением автора без native-прогона** | Bukkit/CraftWorld identity bridge, plugin classloaders, реальные WE/WG API, target-cell ownerless policy. Нет выбранного hybrid-стенда/гарантии всех платформ; [подробности ниже](#perf-001-wg332) |

Для Simple Fog Control/Sodium/Lithium точные номера совместно проверенных версий и конфиг не предоставлены. Не подставлять придуманные номера и не превращать авторское подтверждение в собственный отчёт тестирования.

## Как GT генерирует мир сейчас

GT работает **standalone**, без TerraBlender. `DesolationPresetInject`/`DesolationPresetExt` и mixins инъецируют Пустошь в multinoise preset/source; features/biome modifiers добавляют собственный контент. Это собственный путь GT, а не согласованное подключение к TerraBlender. Две точки lifecycle покрывают готовые datapack-реестры и уже созданный biome source, с защитой от повторного добавления.

Поэтому генерация GT **может ломать или перекрывать генерацию других модов**, а их подмена biome source может мешать GT. Не заявлять одновременно «интеграция с библиотекой уже есть» и «только забыли dependency»: нет ни подключения API, ни объявления зависимости. Библиотека TerraBlender — не Terraformer; имя уточнено автором в этом шаге.

Будущая D-INT1: при установленном TerraBlender генератор биомов GT должен работать через него, при отсутствии — сохранить standalone. Это ещё не реализовано, зависимость сейчас не добавляется. Детали собственной генерации — [WORLDGEN](WORLDGEN.md); не смешивать biome integration с переносом старых миров.

## Где проверять совместимость

Сборка/загрузка без optional-модов и с ними; клиент и dedicated server отдельно. Для JEI — категории/дисплеи/отсутствие hard-load на сервере; для визуала — небо, солнечный ивент, космические измерения, fog жидкостей, дождь/снег; для генерации — новые миры/чанки, biome source, Пустошь и чужие биомы. Это чек-лист будущей приёмки, не запись выполненных тестов.

При сообщении о конфликте фиксировать Minecraft/NeoForge, версии обоих модов, shader pack и настройки, seed/измерение/способ создания мира, минимальное воспроизведение и лог. Непонятное уточнять у автора, не объявлять виновника предположением. Визуальные контракты — [AUDIO_VISUAL](AUDIO_VISUAL.md), внутренние I/O API — [CAPABILITIES](CAPABILITIES.md).

## История

0.3.311, DOC-002: добавлен явный реестр совместимости; автор подтвердил JEI/Simple Fog Control/Sodium/Lithium. Проверено отсутствие текущей TerraBlender-интеграции; автор согласовал D-INT1 для будущего условного подключения. Небо/генерация отмечены как зоны риска, runtime/dependencies не изменены. Полная история — [HISTORY](HISTORY.md).

## JEI vanilla factory для alloy previews (0.3.316)

Новые display-only CraftingRecipe получаются через IVanillaRecipeFactory/IJeiShapedRecipeBuilder **JEI20.0.0 / MC1.21.4**, SlotDisplay.ItemStackSlotDisplay; API сверено с upstream tagv20.0.0 (gradle.properties minecraftVersion1.21.4), не с веткой другого MC. Dependency20.0.0.4/optional client scope не изменены, JEI не импортируется серверными BE/recipes. Никаких реальных серверных recipe registration или ослабления состава. Показ/переключение мира/сервер без JEI требуют игрового smoke-test; headless checks не заменяют build compatibility.

## GUI layer вместо Post — 0.3.317

Использованы штатные NeoForge1.21.4 RegisterGuiLayersEvent.registerAbove(VanillaGuiLayers.CHAT, id, layer) и mod-client registration; сигнатура/порядок/pose pop подтверждены upstream RegisterGuiLayersEvent/GuiLayerManager. Это исправляет размещение приборов в собственном моде, не объявляет протестированными все сторонние chat/HUD/render mods. Зависимости не менялись; GPU visual acceptance отдельно от typecheck/registration harness.

<a id="perf-001-wg332"></a>
## PERF-001/9 — optional WE/WG bridge,0.3.332

Bridge выбирают создатели серверов, не автор GT. Target автора: WG7.0.13 и WE7.4.2/подходящая7.4.x для Bukkit/Paper1.21.4; не проверка бинарной совместимости любого артефакта/всех hybrid. Нет отдельной обязательной dependency, WE/WG не упаковываются в мод.

### Реальная цепочка API и границы bridge

1. Lazy discovery реального running Bukkit server и `PluginManager.getPlugin("WorldGuard")`. Только Bukkit API/пустой сервер не считается WG. Нет WG — кэш отсутствия до server-start/stop, никаких новых block/region/world scans на повторной попытке. После установки ранее отсутствовавшего плагина нужен **полный restart**, hot-install не обещан.
2. Известный installed WG требует enabled WorldGuard **и** WorldEdit. Классы берутся из classloader реальных plugin objects, не `Class.forName(WG)` из mod loader. Bukkit API должен быть виден mod loader либо server-thread context loader; полностью изолированный/неэкспортирующий Bukkit runtime не является поддержанным bridge. Старое «нет класса WG в mod loader — разрешить всё» удалено.
3. Bukkit World выбирается только по публичному `getHandle() == ServerLevel`, без угадывания имён/dimension keys. Это проверенный public CraftWorld API Paper1.21.4, но не обещание такого контракта каждого hybrid. Не найден точный handle — deny, не fallback. Слабая карта world mapping очищается при LevelEvent.Unload; server sessions — при ServerStarted/Stopped. Смена plugin object переинициализирует bindings, disabled/missing plugin не разрешает actor fallback.
4. `BukkitAdapter.adapt(org.bukkit.World)` → **WorldEdit World** → `WorldGuard.getInstance().getPlatform().getRegionContainer().get(world)` → **живой** `RegionManager.getApplicableRegions(BlockVector3)` для одной целевой клетки. Нет cache решений/RegionManager по строке измерения. Новый manager и правка флагов видны при следующей попытке, включая тот же тик.
5. `ApplicableRegionSet.queryValue` и native `FlagValueCalculator.getEffectiveFlagOf` разрешают priority/ties/parents/groups. BUILD требует ненулевой RegionAssociable: proxy всегда **NON_MEMBER**, не владельца/nearby player. Explicit-value probe отсекает standard default `pistons=ALLOW`. Применение идёт до debit/world mutation/ore award; BE gates до прежнего event cache. Нет проверки корпуса Плотника или whole-sector preflight.

Без WG — штатный путь NeoForge-событий (включая прежнюю actor/event семантику иных защит), без новых региональных сканов. Исправный WG вне любых регионов разрешает. Известный установленный, но неисправный WG/WE/classloader/API/world mapping/region data — deny + серверный ERROR один раз на failure kind за session, не молчаливый fail-open. Null RegionManager (включая disabled regions)/virtual set считается недоступными данными, не доказательством «вне регионов». API binding failure требует смены plugin object/restart; world/region-data failures могут восстановиться при следующей попытке.

### Выбранная автором машинная политика

Для **любой** правки нужны оба разрешения `block-place` и `block-break`: каждое — явный ALLOW либо, при UNSET, fallback от `build=ALLOW`. Любой явный DENY place/break запрещает всё; `build=DENY` — безусловный машинный veto. `pistons` обязан быть **явно ALLOW**; UNSET запрещает, хотя стандартный default WG равен ALLOW. Полное отсутствие этих флагов внутри региона не разрешает работу. Приоритеты, одинаковые priority, родители и region groups разрешаются движком WG; субъект всегда NON_MEMBER, без владельца/ближайшего игрока.

`__global__` считается регионом всего мира, не «вне регионов». По отдельному решению автора явный глобальный `build=ALLOW` **может дать fallback**, в отличие от legacy-правила самого WG. Исключение учитывает NON_MEMBER-группу и native priority/ignored-parent/implicit-build barriers: не перепрыгивает локальный build-барьер и не отменяет DENY/прочие флаги. Остальные значения разрешает WG.

Это **машинная политика**, не имитация обычного WG player build-check. Имена стандартные `build`, `block-place`, `block-break`, `pistons`; выдуманные `block_place`/`piston_allowed`/`piston-protection` не aliases. Область: work/drill строительство, bore-pipe установка/замена + добыча породы, боковой всос руды, демонтаж, TNT replacement. Проверяется именно изменяемая клетка, не соседняя или корпус машины.

### Проверенные источники и предел доказательства

Пины/полные upstream bytes/hash/лицензия: [fixture manifest](../audit/fixtures/worldguard_7/manifest.json), [описание harness](../audit/fixtures/worldguard_7/README.md). WG7.0.13 commit82fdc65d0eec1a55b3346824e4e586983e85d62c; WE7.4.2 ff8ccac9d4934b036a18640f54388ef8b543a075; Paper ver/1.21.4 73116ac9dd1d55c59db411a55de7e91e025a0c80. Это source/API evidence, не запуск этих plugin jars на NeoForge.

`worldguard_bridge_test.py`:1326assertions,9assertionmutants, exact331compilednegative,100000absentAPIdecisions/0block/chunkreads. Исполняются production adapter/policy/session, неизменённые BE gates/мутационные методы и13полных исходников WG flag engine; классы WE/WG физически убраны из mod classpath и доступны только plugin loader. Bukkit/MC/bootstrap/spatial lookup/Guava boundary doubles; не full protected-region index/плагинный bootstrap/native hybrid/Gradle/GameTests/TPS. Проверены81комбинация флагов в обоих gates, live edits/replaced manager, priority/ties/parents/groups/global override и72сравнения global fallback с native ordinary-region oracle, отказ/возобновление6mutation paths, diagnostics/recovery/lifecycle/absence.

### Закрытие по решению автора и граница native-доказательства

Автор ответил: **реального hybrid-стенда пока нет**. Автор отдельно закрыл PERF-001/9 как выполненную реализацию; native-прогон не заявлен и отдельной активной задачи приёмки не оставлено. Если в будущем проверяется конкретный сервер, справочный чек-лист (не текущая очередь): зафиксировать его точный build, Minecraft/NeoForge/WE/WG jars и startup log; проверить loaded plugin APIs и точное mapping overworld/custom dimension; отсутствие WG/чужой NeoForge protection; пустые регионы/глобальный регион; оба разрешения/BUILD veto/явный PISTONS, priority/parent/group; все6правок включая боковой всос руды, отсутствие дебита/выдачи при deny и штатный retry после allow; same-tick edit/reload/unload/restart/disabled/broken provider с диагностикой. Эти сценарии **не выполнены** агентом. 12/13/14 и D-PLOT1 этим шагом не реализуются.

## NeoForge item wrappers / Mixin admission —0.3.323

В getSlotLimit InvWrapper/SidedInvWrapper добавлены условные SlotCapacity hooks; остальные containers/стандартные special cases сохраняют прежний return. Vanilla Hopper.tryMoveInItem обёрнут WrapMethod с bounded offer, остатком и вызовом original, не переписан. Исходники классов/сигнатур wrappers сверены с NeoForge branch1.21.4 commitf4b4a9655362b103b95769258ed6586597d8b7f4, сохранены сLGPL2.1/source/hash в audit fixtures; это не утверждение компиляции точного артефакта21.4.157. WrapMethod API существует в MixinExtras0.4.1; current branchNeoForge использует0.5.3, не приписывать это автоматически установленной версии. Hopper boundary — отдельный явно неофициальный decompiled mirror KernelDotDLL/mcp-1.21.4@6e96771cc9652af23d86d558e30f9f39a7378225.

Тест исполняет production hook bodies с ручным wiring и компилирует новые mixin classes против annotation/API doubles; агент не запускал native Mixinbootstrap. Автор30.10.2026 подтвердил в игре весь пункт2, он принят/архивирован; это не утверждение проверенной матрицы всех внешних модов. Existing radiation redirects/copy hooks/формулы не менялись. Исправление не новая FE/ItemHandler capabilityregistration и не гарантия против произвольного изменения ItemStack/NBT в обход insertionprotocol. В324эти mixins/зависимости не менялись, проверены их caller paths с production virtual setter.
**Смена статуса01.11.2026:** №9 закрыт решением автора без нового native-доказательства;13/14 → [D-PIPE2](DEFFERED.md#d-pipe2),12 → [D-PERF12](DEFFERED.md#d-perf12). Реализация deferred не разрешена. [Хроника](HISTORY.md#status-reset332).

## Авторский пакет 03 — отложенная серверная валидация

[D-SEC1](DEFFERED.md#d-sec1) содержит только SEC-1/A6 (bounded ingress других C2 S, не замена существующего Plotnick/Fence) и SEC-2/A7 (server-confirmed crisis action/частота/повторы). SEC-3 из старого исходника не является открытой задачей: автор повторно подтвердил закрытие№9 без native-прогона. Справочный WG-чек-лист выше сохраняет ограничения совместимости, но не создаёт очередь приёмки. Источники — [OUTDATED_AUTHOR332_03](archive/editions/0.3.332/OUTDATED_AUTHOR332_03.md).

Предыдущая редакция профиля: [до синхронизации пакетов332](archive/editions/0.3.332/OUTDATED_BEFORE_AUTHOR_SYNC332_INTEGRATIONS.md); причина и границы — [HISTORY](HISTORY.md#author-sync332).
