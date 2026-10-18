# Правило путей и тем машинных GUI

**Действует с версии 0.3.241.** Этот документ — источник истины для путей машинных листов и правила их переиспользования.

## Каталоги

- Classic-листы: `src/main/resources/assets/gonzotech/textures/gui/classic/`.
- NEO-листы: `src/main/resources/assets/gonzotech/textures/gui/neo/`.
- Общие служебные текстуры (шкалы `bar_*.png`, анимации, onboarding и прочие экраны) остаются в корне `textures/gui/` или в своих подкаталогах, например `textures/gui/notes/`.

Экран машины должен выбирать оба файла явно через `MachineScreen.machineGui(classicFile, neoFile)`. Этот метод добавляет папку текущей темы. Одноаргументный `MachineScreen.gui(file)` предназначен только для служебных изображений в корне; машинные листы через него не загружаются.

## Переиспользование

- **Classic можно переиспользовать**, когда дизайн/раскладка одинаковы. Например, `AccumulatorScreen` и `SecondAccumulatorScreen` оба используют `classic/accumulator_gui.png` и `classic/accumulator_gui_bg.png`.
- **У каждого станка и уровня должен быть собственный NEO-путь**, даже если пока пиксели совпадают. Для аккумуляторов это `neo/first_accumulator_gui_neo.png` и `neo/first_accumulator_gui_bg_neo.png` против `neo/second_accumulator_gui_neo.png` и `neo/second_accumulator_gui_bg_neo.png`.
- Разливной кран может использовать классический лист помпы, но его NEO-пара отдельная: `neo/third_dispensing_tap_gui_neo.png` и `neo/third_dispensing_tap_gui_bg_neo.png`.
- Фильтры и оверлеи тоже следуют этому правилу: первый и второй фильтры имеют отдельные NEO-файлы; NEO-рамка дополнительных слотов реактора лежит в `neo/`.

JEI намеренно показывает Classic-листы независимо от клиентской настройки. Новая проверка `audit/machine_gui_theme_test.py` валидирует разрешение всех путей из экранов, наличие и совпадение размеров листов, отсутствие повторного использования NEO-файла между экранами и отсутствие неиспользуемых NEO-машинных листов.
