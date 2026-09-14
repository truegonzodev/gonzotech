# Gonzo Tech — остаточный статический аудит

Дата: 2026-09-14. Это обновлённый список только оставшихся, новых либо требующих решения вопросов после исправления прогрессии, model-parent ссылок и GUI-контрактов. Проверка статическая: JSON и ссылки на ресурсы проверены скриптом; Gradle, выделенный сервер, клиент и игра пока не запускались.

## Закрытая область проверки

Следующие прежние замечания больше не входят в список открытых:

- `crusher` теперь принадлежит Discovery 2 и одновременно скрыт из vanilla recipe book и физически заменяется на `botched_mechanism` до открытия;
- `turbine_casing` и `turbine_rotor` теперь принадлежат Discovery 1 с теми же двумя gate-механиками;
- dust-alloy смеси по-прежнему намеренно только скрыты из книги до Discovery 2 и всегда физически крафтятся;
- все девять legacy `models/item/<ore>_ore.json` теперь указывают на существующую `block/deepslate_<ore>_ore` model; это устраняет stale parents, но не является доказанным текущим visual-regression, поскольку базовые stone-host BlockItem этих руд не зарегистрированы;
- `SecondMachineStubBlock` удалён после поиска регистраций, импортов, инстанцирований и строковых ссылок: их не было;
- raw Java-надпись `OUTPUT` удалена из Alloy Foundry;
- кодовые пути текстур для Electric Furnace II, Foundry background, Item Filter II, Pump II и Cobble Generator II переведены на отдельные имена. Восемь целевых PNG намеренно **не создавались и не копировались**: художник создаёт их параллельно, и agent не должен конфликтовать с его WIP. До их появления эти экраны не следует запускать в клиенте.

Статическая проверка прошла: все 34 recipe ID из `TierTwoCrafting` имеют JSON, нужные craft/recipe-book gates присутствуют, а все прямые `gonzotech:block/...` model references разрешаются. Восемь новых GUI references являются ожидающими art-ресурсами, а не ошибками model/JSON разрешения.

## Главный результат: Tier 2 не везде является безусловно лучшим Tier 1

| Объект / семейство | Подтверждённое отличие | Статический вывод |
|---|---|---|
| Accumulator II | 48,900 против 10,840 GTU; 144 против 64 GTU/t I/O; loss 0.010 против 0.016 GTU/t | Однозначно лучше: 4.51× storage, 2.25× I/O, на 37.5% меньше passive loss. |
| Electric Furnace II | 2 линии по 90 ticks / 220 GTU вместо 1 линии 125 ticks / 200 GTU; storage 8,400 вместо 4,800 | Производительность 2.78× выше, но стоимость предмета на 10% выше. Это speed-up, **не** energy-efficiency upgrade. Входной cap остаётся 64 GTU/t как у I. |
| Pump II | 844 против 154 mB/t delivery cap, source interval 4 против 5 ticks, intake 96 против 64 GTU/t; storage не меняется | Delivery cap 5.48×, добыча source-water 1.25×, а source-water/GTU примерно 1.17× лучше. Но это не 5.48× steady-state source generation: её ограничивает один source pickup каждые 4 ticks. |
| Cobble Generator II | fixed 60 ticks, 1.4 GTU/t, no pickaxe, versus I: 220–50 ticks и 0.8 GTU/t по типу кирки | Без кирки I: 3.67× быстрее и 84 против 176 GTU/rock, то есть лучше. Но против I с gold/diamond/netherite pick II уже расходует больше энергии; против netherite I (50 ticks, 40 GTU/rock) II ещё и медленнее (60 ticks, 84 GTU/rock). |
| Item Filter II | 5 вместо 3 шаблонов; 10 вместо 5 предметов/t total; 2 вместо 1/t per exact item | Однозначное расширение возможностей и throughput. Buffer остаётся 5, что не отменяет маршрутного увеличения. |
| Item Scavenger II | тот же passive `ItemScavengerBlock`, без BE/menu/собственных constants | Сам блок II функционально идентичен I. Это может быть намеренным визуально-прогрессионным вариантом, но не самостоятельным апгрейдом. Скорость системы может вырасти только в сочетании с Filter II. |
| Tier-2 pipes/nodes | Wire 96/38, Heat 696/388, Water/Steam 1900/1000, Item 10/5 total и 2/1 exact-item, Universal Fluid 1500/800 | Все лимиты выше (примерно 1.79×–2.53×; fluid 1.875×; item 2×). |
| Grinder II, Press II, Alloy Foundry II, Crusher, Centrifuge | В первом tier нет одноимённой машины с эквивалентным recipe/BE contract | Сравнение «лучше Tier 1» неприменимо: это новые специализированные машины, а не II-версия существующего станка. |

## Открытые / спорные продуктовые решения

1. **Electric Furnace II: подтвердить цену скорости.** Сейчас её throughput выше, но energy per item хуже на 10%. Если правило II буквально должно означать «быстрее и эффективнее», следует снизить `ELECTRIC_GTU_PER_ITEM` до 200 или ниже. Если II — осознанный overclock, текущий баланс корректен, но описание не должно называть её энергоэффективнее.
2. **Cobble Generator II: определить baseline для сравнения.** Он хорошо апгрейдит базовый generator без кирки и исключает расход/поломку кирки, но не является апгрейдом fully-equipped I с diamond/netherite киркой. Возможны два корректных направления: оставить II как стабильный автомат без инструмента или уменьшить его time/cost так, чтобы он превосходил лучшую кирку I.
3. **Pump II: решить, является ли отказ от контейнеров намеренным.** I умеет наполнять bucket/bottle из inventory; II не имеет предметных слотов и работает только через water network. Это сознательная специализация на pipes либо functional regression — по исходникам нельзя выбрать продуктовую трактовку.
4. **Item Scavenger II: подтвердить намеренную идентичность.** Если нужен настоящий II-upgrade, ему требуется собственный параметр/механика; если он служит лишь tier-consistent visual block, код уже соответствует этому намерению.
5. **Фактическая проверка нужна после готовых PNG.** Статика подтверждает имена, JSON и кодовые ссылки, но не проверяет slot alignment, alpha masking, z-order, rendering атласов, behavior recipe book и то, что ingredients действительно уничтожаются в multiplayer/server context.

## GUI resource contracts, ожидающие финальной графики

| Экран | Кодовой ресурс |
|---|---|
| Electric Furnace II | `second_electric_furnace_gui.png`, `second_electric_furnace_gui_bg.png` |
| Alloy Foundry II | foreground `alloy_foundry_gui.png`, background `alloy_foundry_gui_bg.png` |
| Item Filter II | `second_item_filter_gui.png` (single sheet; Tier I динамически остаётся на `item_filter_gui.png`) |
| Pump II | `second_pump_gui.png`, `second_pump_gui_bg.png` |
| Cobble Generator II | `second_cobble_generator_gui.png`, `second_cobble_generator_gui_bg.png` |

Accumulator II продолжает использовать Tier-1 art по принятому решению. Grinder II, Press II, Centrifuge и Crusher в этой задаче не менялись.
