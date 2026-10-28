# Интеграции и совместимость модпака

Срез **0.3.311**, база `26e2a31`, 27.10.2026. Различаем: встроенный адаптер в коде, подтверждение автора о совместной работе, возможный конфликт и будущую интеграцию. Здесь не заявляется, что Arena запустила модпак/все версии модов.

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
| WorldGuard / региональные флаги | **Текущий reflective path не соответствует стандартному WG7; PERF-001/9 активна** | Нужны реальный bridge, адаптация WorldEdit World, cell-based ownerless query всех3разрешений и health check. Без серверного прогона защита не заявляется; fallback/отсутствие bridge требуют решения автора. Границы — [CAPABILITIES](CAPABILITIES.md) |

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

## PERF-001/9 — начальный API research

Независимо сверены официальные EngineHub исходники: WG version/7.0.x RegionContainer.get(com.sk89q.worldedit.world.World), RegionManager.getApplicableRegions(BlockVector3), WE BukkitAdapter.adapt(org.bukkit.World). Стандартный тип/методы не совпадают с текущим reflective path. API-source сверка не доказывает наличие классов/адаптации ServerLevel в конкретном hybrid-сервере. Требуются имя/версия реального bridge, mapping измерений и точная политика custom/standard flags; piston-protection нельзя молча считать эквивалентом положительного piston_allowed. Источники — HISTORY; зависимости/optional loading пока не меняются.

### Ответ автора по PERF-001/9

Bridge выбирают создатели серверов. Целевой набор автора: WG7.0.13 и WE7.4.2/подходящая7.4.x под Bukkit/Paper1.21.4. Это target, не тест совместимости всех билдов; адаптеру нужен корректный world provider/optional loading/health-check, а не жёсткое предположение о названии hybrid. БезWG/вне регионов разрешено; установленныйWG с неисправнымbridge — отказ с диагностикой; внутри региона все3разрешения обязательны, unset/unknown не ALLOW, без actor fallback. WG7.0.13 tag доступен upstream (82fdc65d0eec1a55b3346824e4e586983e85d62c). Исправление9 ещё не применено/не прогнано на сервере.
