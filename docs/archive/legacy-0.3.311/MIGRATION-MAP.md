# Карта миграции docs — 0.3.311 / 27.10.2026

Это контроль переноса, не новый текущий backlog. **Все 75 исходных файлов сохранены побайтно** под legacy-0.3.311, включая прежнюю папку archive. Манифест хранит original/archived/SHA-256/bytes/destinations. sections.json сохраняет 4173 исходных заголовка/пункта с номерами строк; их наличие не делает каждый текст новым заданием.

Проверка байтов/полноты выполняется audit/docs_structure_test.py против Git-базы 42730c3 и manifest. Сохранность смысловых решений проверяется этой картой и живыми очередями, а не одним хешем.

## Исходные файлы и получатели

| Исходный путь относительно docs | Архивная копия | Живые получатели |
|---|---|---|
| `ALLOY_SYSTEM_DESIGN.md` | [ALLOY_SYSTEM_DESIGN.md](ALLOY_SYSTEM_DESIGN.md) | [MATERIALS.md](../../MATERIALS.md), [EQUIPMENT.md](../../EQUIPMENT.md), [HISTORY.md](../../HISTORY.md) |
| `ALLOY_TINT_PALETTE.md` | [ALLOY_TINT_PALETTE.md](ALLOY_TINT_PALETTE.md) | [MATERIALS.md](../../MATERIALS.md), [EQUIPMENT.md](../../EQUIPMENT.md), [HISTORY.md](../../HISTORY.md) |
| `AUDIT-2026-09-21.md` | [AUDIT-2026-09-21.md](AUDIT-2026-09-21.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-24.md` | [AUDIT-2026-09-24.md](AUDIT-2026-09-24.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-25-CLEANROOM.md` | [AUDIT-2026-09-25-CLEANROOM.md](AUDIT-2026-09-25-CLEANROOM.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-25.md` | [AUDIT-2026-09-25.md](AUDIT-2026-09-25.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-26-AIR-FILTER.md` | [AUDIT-2026-09-26-AIR-FILTER.md](AUDIT-2026-09-26-AIR-FILTER.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-26-ALCOHOL.md` | [AUDIT-2026-09-26-ALCOHOL.md](AUDIT-2026-09-26-ALCOHOL.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-26-CONTOURS.md` | [AUDIT-2026-09-26-CONTOURS.md](AUDIT-2026-09-26-CONTOURS.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-26-FLUID-FOAM.md` | [AUDIT-2026-09-26-FLUID-FOAM.md](AUDIT-2026-09-26-FLUID-FOAM.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-26-TOOLTIPS.md` | [AUDIT-2026-09-26-TOOLTIPS.md](AUDIT-2026-09-26-TOOLTIPS.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-27-FAINT-UI.md` | [AUDIT-2026-09-27-FAINT-UI.md](AUDIT-2026-09-27-FAINT-UI.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-27-FOLLOWUP.md` | [AUDIT-2026-09-27-FOLLOWUP.md](AUDIT-2026-09-27-FOLLOWUP.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-27-GUI.md` | [AUDIT-2026-09-27-GUI.md](AUDIT-2026-09-27-GUI.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-27-SECOND-PASS.md` | [AUDIT-2026-09-27-SECOND-PASS.md](AUDIT-2026-09-27-SECOND-PASS.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-09-27.md` | [AUDIT-2026-09-27.md](AUDIT-2026-09-27.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-2026-10-22-PLOTNICK-REREVIEW.md` | [AUDIT-2026-10-22-PLOTNICK-REREVIEW.md](AUDIT-2026-10-22-PLOTNICK-REREVIEW.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-42-REVALIDATION-2026-10-26.md` | [AUDIT-42-REVALIDATION-2026-10-26.md](AUDIT-42-REVALIDATION-2026-10-26.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUDIT-CURRENT.md` | [AUDIT-CURRENT.md](AUDIT-CURRENT.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `AUTOMINER-DESIGN-0.3.255.md` | [AUTOMINER-DESIGN-0.3.255.md](AUTOMINER-DESIGN-0.3.255.md) | [AUTOMINER.md](../../AUTOMINER.md), [DEFFERED.md](../../DEFFERED.md) |
| `CONTENT-INVENTORY-2026-09-27.csv` | [CONTENT-INVENTORY-2026-09-27.csv](CONTENT-INVENTORY-2026-09-27.csv) | [AUDIT.md](../../AUDIT.md), [HISTORY.md](../../HISTORY.md) |
| `DEFERRED-2026-09-22.md` | [DEFERRED-2026-09-22.md](DEFERRED-2026-09-22.md) | [DEFFERED.md](../../DEFFERED.md), [PROGRESSION.md](../../PROGRESSION.md), [AUTHORS-NOTES.md](../../AUTHORS-NOTES.md), [HISTORY.md](../../HISTORY.md) |
| `DRUG-DESCRIPTIONS-2026-09-24.md` | [DRUG-DESCRIPTIONS-2026-09-24.md](DRUG-DESCRIPTIONS-2026-09-24.md) | [PSYCHE.md](../../PSYCHE.md), [RAD_WORLD.md](../../RAD_WORLD.md) |
| `EPOCH3-BASE-2026-09-22.md` | [EPOCH3-BASE-2026-09-22.md](EPOCH3-BASE-2026-09-22.md) | [DEFFERED.md](../../DEFFERED.md), [PROGRESSION.md](../../PROGRESSION.md), [AUTHORS-NOTES.md](../../AUTHORS-NOTES.md), [HISTORY.md](../../HISTORY.md) |
| `EPOCH3-BRAINSTORM-2026-09-22.md` | [EPOCH3-BRAINSTORM-2026-09-22.md](EPOCH3-BRAINSTORM-2026-09-22.md) | [DEFFERED.md](../../DEFFERED.md), [PROGRESSION.md](../../PROGRESSION.md), [AUTHORS-NOTES.md](../../AUTHORS-NOTES.md), [HISTORY.md](../../HISTORY.md) |
| `EPOCH3-LITHOGRAPHY-PLAN-2026-09-26.md` | [EPOCH3-LITHOGRAPHY-PLAN-2026-09-26.md](EPOCH3-LITHOGRAPHY-PLAN-2026-09-26.md) | [DEFFERED.md](../../DEFFERED.md), [PROGRESSION.md](../../PROGRESSION.md), [AUTHORS-NOTES.md](../../AUTHORS-NOTES.md), [HISTORY.md](../../HISTORY.md) |
| `FLUID-PARTICLES-REVIEW-2026-09-26.md` | [FLUID-PARTICLES-REVIEW-2026-09-26.md](FLUID-PARTICLES-REVIEW-2026-09-26.md) | [AUDIT.md](../../AUDIT.md), [HISTORY.md](../../HISTORY.md) |
| `GREATSWORD-AUDIT-ROADMAP.md` | [GREATSWORD-AUDIT-ROADMAP.md](GREATSWORD-AUDIT-ROADMAP.md) | [MATERIALS.md](../../MATERIALS.md), [EQUIPMENT.md](../../EQUIPMENT.md), [HISTORY.md](../../HISTORY.md) |
| `GUI-TEXTURE-CONVENTION.md` | [GUI-TEXTURE-CONVENTION.md](GUI-TEXTURE-CONVENTION.md) | [RULES.md](../../RULES.md), [SUPER_CRUCIAL_FILE.md](../../SUPER_CRUCIAL_FILE.md) |
| `HEAVY-DOOR-2026-09-22.md` | [HEAVY-DOOR-2026-09-22.md](HEAVY-DOOR-2026-09-22.md) | [MACHINES.md](../../MACHINES.md), [RAD_WORLD.md](../../RAD_WORLD.md) |
| `ITEM-RADIATION-CURRENT.md` | [ITEM-RADIATION-CURRENT.md](ITEM-RADIATION-CURRENT.md) | [RAD_ITEMS.md](../../RAD_ITEMS.md), [RAD_WORLD.md](../../RAD_WORLD.md), [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md) |
| `ITEM-RADIATION-ENGINE-0.3.306.md` | [ITEM-RADIATION-ENGINE-0.3.306.md](ITEM-RADIATION-ENGINE-0.3.306.md) | [RAD_ITEMS.md](../../RAD_ITEMS.md), [RAD_WORLD.md](../../RAD_WORLD.md), [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md) |
| `ITEM-RADIATION-MERGE-0.3.311.md` | [ITEM-RADIATION-MERGE-0.3.311.md](ITEM-RADIATION-MERGE-0.3.311.md) | [RAD_ITEMS.md](../../RAD_ITEMS.md), [RAD_WORLD.md](../../RAD_WORLD.md), [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md) |
| `JEI-MACHINES.md` | [JEI-MACHINES.md](JEI-MACHINES.md) | [JEI.md](../../JEI.md), [AUDIT.md](../../AUDIT.md) |
| `JEI-RECIPE-INVENTORY.md` | [JEI-RECIPE-INVENTORY.md](JEI-RECIPE-INVENTORY.md) | [JEI.md](../../JEI.md), [AUDIT.md](../../AUDIT.md) |
| `METAL-CHAINS-2026-09-27.md` | [METAL-CHAINS-2026-09-27.md](METAL-CHAINS-2026-09-27.md) | [MATERIALS.md](../../MATERIALS.md), [EQUIPMENT.md](../../EQUIPMENT.md), [HISTORY.md](../../HISTORY.md) |
| `MUSIC-CONTEXTS-0.3.233.md` | [MUSIC-CONTEXTS-0.3.233.md](MUSIC-CONTEXTS-0.3.233.md) | [AUDIO_VISUAL.md](../../AUDIO_VISUAL.md) |
| `MUSIC-COOLDOWN-0.3.239.md` | [MUSIC-COOLDOWN-0.3.239.md](MUSIC-COOLDOWN-0.3.239.md) | [AUDIO_VISUAL.md](../../AUDIO_VISUAL.md) |
| `NOTES-EPOCH3-PLAN-2026-09-22.md` | [NOTES-EPOCH3-PLAN-2026-09-22.md](NOTES-EPOCH3-PLAN-2026-09-22.md) | [CHALKBOARD.md](../../CHALKBOARD.md), [DEFFERED.md](../../DEFFERED.md), [HISTORY.md](../../HISTORY.md) |
| `NOTES-FAVORITES-0.3.308.md` | [NOTES-FAVORITES-0.3.308.md](NOTES-FAVORITES-0.3.308.md) | [CHALKBOARD.md](../../CHALKBOARD.md), [DEFFERED.md](../../DEFFERED.md), [HISTORY.md](../../HISTORY.md) |
| `PIATILETOKA-2026-10-26.md` | [PIATILETOKA-2026-10-26.md](PIATILETOKA-2026-10-26.md) | [CHALKBOARD.md](../../CHALKBOARD.md), [DEFFERED.md](../../DEFFERED.md), [HISTORY.md](../../HISTORY.md) |
| `PIPE-HEAT-ROUTING-PERF-2026-10-04.md` | [PIPE-HEAT-ROUTING-PERF-2026-10-04.md](PIPE-HEAT-ROUTING-PERF-2026-10-04.md) | [MACHINES.md](../../MACHINES.md), [CAPABILITIES.md](../../CAPABILITIES.md) |
| `PIPES-REVIEW-2026-10-02.md` | [PIPES-REVIEW-2026-10-02.md](PIPES-REVIEW-2026-10-02.md) | [MACHINES.md](../../MACHINES.md), [CAPABILITIES.md](../../CAPABILITIES.md) |
| `RADIATION-BEHAVIOR-0.3.232.md` | [RADIATION-BEHAVIOR-0.3.232.md](RADIATION-BEHAVIOR-0.3.232.md) | [RAD_ITEMS.md](../../RAD_ITEMS.md), [RAD_WORLD.md](../../RAD_WORLD.md), [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md) |
| `RADIATION-VISUAL-PLAN-2026-09-25.md` | [RADIATION-VISUAL-PLAN-2026-09-25.md](RADIATION-VISUAL-PLAN-2026-09-25.md) | [RAD_WORLD.md](../../RAD_WORLD.md) |
| `REACTOR-FUEL-STORAGE-0.3.238.md` | [REACTOR-FUEL-STORAGE-0.3.238.md](REACTOR-FUEL-STORAGE-0.3.238.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `REACTOR-GUI-0.3.236.md` | [REACTOR-GUI-0.3.236.md](REACTOR-GUI-0.3.236.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `REACTOR-LAYER-REVIEW-0.3.248.md` | [REACTOR-LAYER-REVIEW-0.3.248.md](REACTOR-LAYER-REVIEW-0.3.248.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `REACTOR-MATRIX-0.3.237.md` | [REACTOR-MATRIX-0.3.237.md](REACTOR-MATRIX-0.3.237.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `REACTOR-MATRIX-0.3.238.md` | [REACTOR-MATRIX-0.3.238.md](REACTOR-MATRIX-0.3.238.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `REACTOR-RUNTIME-0.3.242.md` | [REACTOR-RUNTIME-0.3.242.md](REACTOR-RUNTIME-0.3.242.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `REACTOR-STRUCTURE-0.3.234.md` | [REACTOR-STRUCTURE-0.3.234.md](REACTOR-STRUCTURE-0.3.234.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `REACTOR-STRUCTURE-0.3.235.md` | [REACTOR-STRUCTURE-0.3.235.md](REACTOR-STRUCTURE-0.3.235.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `SPENT-FUEL-REPROCESSING-0.3.250.md` | [SPENT-FUEL-REPROCESSING-0.3.250.md](SPENT-FUEL-REPROCESSING-0.3.250.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `SUPER_CRUCIAL_FILE.md` | [SUPER_CRUCIAL_FILE.md](SUPER_CRUCIAL_FILE.md) | [SUPER_CRUCIAL_FILE.md](../../SUPER_CRUCIAL_FILE.md) |
| `UNITS-GOST-2026-09-22.md` | [UNITS-GOST-2026-09-22.md](UNITS-GOST-2026-09-22.md) | [RULES.md](../../RULES.md), [SUPER_CRUCIAL_FILE.md](../../SUPER_CRUCIAL_FILE.md) |
| `UPDATED_TEMP_NOTES.md` | [UPDATED_TEMP_NOTES.md](UPDATED_TEMP_NOTES.md) | [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md), [DEFFERED.md](../../DEFFERED.md), [RULES.md](../../RULES.md) |
| `WORLDGEN-SURFACE-RUINS.md` | [WORLDGEN-SURFACE-RUINS.md](WORLDGEN-SURFACE-RUINS.md) | [WORLDGEN.md](../../WORLDGEN.md) |
| `archive/CHALKBOARD-SERVER-AUTHORITY-PATCH.md` | [archive/CHALKBOARD-SERVER-AUTHORITY-PATCH.md](archive/CHALKBOARD-SERVER-AUTHORITY-PATCH.md) | [HISTORY.md](../../HISTORY.md) |
| `archive/CONTENT_AUDIT_2026-09-14.md` | [archive/CONTENT_AUDIT_2026-09-14.md](archive/CONTENT_AUDIT_2026-09-14.md) | [HISTORY.md](../../HISTORY.md) |
| `archive/LOGISTICS-TEMP-NOTES.md` | [archive/LOGISTICS-TEMP-NOTES.md](archive/LOGISTICS-TEMP-NOTES.md) | [HISTORY.md](../../HISTORY.md) |
| `archive/MATERIAL_PROCESSING_CONCEPT.md` | [archive/MATERIAL_PROCESSING_CONCEPT.md](archive/MATERIAL_PROCESSING_CONCEPT.md) | [HISTORY.md](../../HISTORY.md) |
| `archive/README.md` | [archive/README.md](archive/README.md) | [HISTORY.md](../../HISTORY.md) |
| `archive/TEMP-notes.md` | [archive/TEMP-notes.md](archive/TEMP-notes.md) | [HISTORY.md](../../HISTORY.md) |
| `debug.log` | [debug.log](debug.log) | [AUDIT.md](../../AUDIT.md), [HISTORY.md](../../HISTORY.md) |
| `debug2.log` | [debug2.log](debug2.log) | [AUDIT.md](../../AUDIT.md), [HISTORY.md](../../HISTORY.md) |
| `dupe_curing.md` | [dupe_curing.md](dupe_curing.md) | [RAD_ITEMS.md](../../RAD_ITEMS.md), [RAD_WORLD.md](../../RAD_WORLD.md), [HISTORY.md](../../HISTORY.md), [AUDIT.md](../../AUDIT.md) |
| `latest.log` | [latest.log](latest.log) | [AUDIT.md](../../AUDIT.md), [HISTORY.md](../../HISTORY.md) |
| `lore.md` | [lore.md](lore.md) | [LORE.md](../../LORE.md), [AUTHORS-NOTES.md](../../AUTHORS-NOTES.md) |
| `machines_balance.xlsx` | [machines_balance.xlsx](machines_balance.xlsx) | [AUDIT.md](../../AUDIT.md), [HISTORY.md](../../HISTORY.md) |
| `meditation-concept.md` | [meditation-concept.md](meditation-concept.md) | [meditation_concept.md](../../meditation_concept.md), [DEFFERED.md](../../DEFFERED.md) |
| `multiblock_rules.md` | [multiblock_rules.md](multiblock_rules.md) | [MACHINES.md](../../MACHINES.md), [REACTORS.md](../../REACTORS.md) |
| `nuclear_firebox_fuel_yield_0.3.143.md` | [nuclear_firebox_fuel_yield_0.3.143.md](nuclear_firebox_fuel_yield_0.3.143.md) | [MACHINES.md](../../MACHINES.md), [CAPABILITIES.md](../../CAPABILITIES.md) |
| `reactors_concept.md` | [reactors_concept.md](reactors_concept.md) | [REACTORS.md](../../REACTORS.md), [PROGRESSION.md](../../PROGRESSION.md), [HISTORY.md](../../HISTORY.md) |
| `steamgen_rebalance_0.3.142.md` | [steamgen_rebalance_0.3.142.md](steamgen_rebalance_0.3.142.md) | [MACHINES.md](../../MACHINES.md), [CAPABILITIES.md](../../CAPABILITIES.md) |

## Семантические решения, не потерявшиеся в переносе

| Прежняя запись/семейство | Исход при миграции |
|---|---|
| Закатной станок | Заменён прессом по явному уточнению автора; ранняя версия перехода неизвестна. MACHINES/PROGRESSION/AUTHORS-NOTES; не DEFFERED |
| Химзавод/наполнитель/брожение/литограф/чистая комната | Реализованы; прежнее «не начато» исторично; современные роли в профильных файлах |
| Химреактор, катализаторы с износом, универсальные аварийные баки, smart controllers | Неутверждённые варианты; AUTHORS-NOTES/концепт, не автоматические обязательства |
| 42 пункта и N-раунды | Сохранён весь исходный журнал; игровые вердикты привязаны к версиям; продолжение приёмки D-VAL2, без blanket-закрытия/повторного внесения фиксов |
| Предметная радиация NBT/held/quiet/clamp/RadDoseIndex/anti-bob | История; современный внешний движок в RAD_ITEMS; не восстанавливать старую архитектуру |
| Bundle не разряжается / plateau 10 mZt / floor 100 nZt | Устаревшие описания; действующий deep-спад CONTAINER+BUNDLE, floor 1 mZt, anchor 33 mZt. Отдельный player recovery floor не отменяется |
| Пар/GTU-продукт первого реактора / старые централизованные слоты | Старые этапы; нынешний GTH/sheath/runtime в REACTORS |
| Семь семейств, бассейн, расширенные топлива | reactor_extensions_concept; конкретные открытые R6/R7/R8 в DEFFERED |
| Предмет discovery III vs активация, 0-based vs 1-based | Разделены в PROGRESSION/CHALKBOARD; таблицы сверены с исходниками |
| JEI 23 категории / 292/293 записей | Старый census; в коде 24 категории, точные runtime display counts остаются D-E5 |
| GUI/NEO, цвета/координаты/текстуры | RULES и SUPER_CRUCIAL_FILE; исходные таблицы сохранены, ассеты не тронуты |
| Препараты, двойной смысл cleanse/absorption | Текущая таблица RAD_WORLD + PSYCHE; не повторный открытый химический завод |
| Ванны/благовония/медитация/револьвер/антистресс/ломка/кризис | D-PSY1…7; визуальный концепт медитации сохранён отдельно |
| Шесть старых событийных достижений | D-ACH1…6, не закрыты энергетическим деревом |
| Пластинки/права | D-SND1; контекстная музыка не закрывает предметные пластинки |
| Подземные жители/масляный робот/моб-телифон | AUTHORS-NOTES с датой свидетельства, не притворяются утверждёнными текущими задачами |
| Магнитный привод меча | D-MAG1; не смешан с уже реализованным magnet |
| Очистные/будущие УФ/мировая токсичность/старые неясные вопросы | D-RAD1/D-UV1/D-CHEM1/D-DESIGN1; не объявляются подтверждёнными багами |
| Hardcore death patch / дополнительная цена clue / legacy migration | Отклонено/исключено по автору; сохранено в истории, не очередь |
| Старый Гонзо/дракон/горизонт/сны/эмоциональный вектор | Полный LORE и meditation_concept; неоднозначность не заменена каноном |
| CSV/XLSX/debug/latest logs | Полные исходники в архиве; новые generated reports вне корня docs |

## Исходная пятилетка — каждый пункт

| ID источника | Исход | Получатель / постоянный ID |
|---|---|---|
| Р1 | Открытая отложенная запись; не разрешение реализации/цен | D-R1 |
| Р2 | Открытая отложенная запись; не разрешение реализации/цен | D-R2 |
| Р3 | Открытая отложенная запись; не разрешение реализации/цен | D-R3 |
| Р4 | Открытая отложенная запись; не разрешение реализации/цен | D-R4 |
| Р5 | Открытая отложенная запись; не разрешение реализации/цен | D-R5 |
| Р6 | Открытая отложенная запись; не разрешение реализации/цен | D-R6 |
| Р7 | Открытая отложенная запись; не разрешение реализации/цен | D-R7 |
| Р8 | Открытая отложенная запись; не разрешение реализации/цен | D-R8 |
| Р9 | Открытая отложенная запись; не разрешение реализации/цен | D-R9 |
| Е1 | Открытая отложенная запись; не разрешение реализации/цен | D-E1 |
| Е2 | Открытая отложенная запись; не разрешение реализации/цен | D-E2 |
| Е3 | Открытая отложенная запись; не разрешение реализации/цен | D-E3 |
| Е4 | Открытая отложенная запись; не разрешение реализации/цен | D-E4 |
| Е5 | Открытая отложенная запись; не разрешение реализации/цен | D-E5 |
| З1 | Открытая отложенная запись; не разрешение реализации/цен | D-З1 |
| З2 | Открытая отложенная запись; не разрешение реализации/цен | D-З2 |
| З3 | Реализовано кодом 0.3.308–310; не повторный TODO; ручная приёмка D-VAL1 | CHALKBOARD.md / HISTORY.md |
| З4 | Открытая отложенная запись; не разрешение реализации/цен | D-З4 |
| З5 | Открытая отложенная запись; не разрешение реализации/цен | D-З5 |
| З6 | Открытая отложенная запись; не разрешение реализации/цен | D-З6 |
| З7 | Открытая отложенная запись; не разрешение реализации/цен | D-З7 |
| З8 | Частично: базовые страницы есть; в очереди только остаток | D-З8 |
| З9 | Открытая отложенная запись; не разрешение реализации/цен | D-З9 |
| З10 | Открытая отложенная запись; не разрешение реализации/цен | D-З10 |
| З11 | Открытая отложенная запись; не разрешение реализации/цен | D-З11 |
| З12 | Открытая отложенная запись; не разрешение реализации/цен | D-З12 |
| З13 | Открытая отложенная запись; не разрешение реализации/цен | D-З13 |
| З14 | Открытая отложенная запись; не разрешение реализации/цен | D-З14 |
| З15 | Открытая отложенная запись; не разрешение реализации/цен | D-З15 |
| З16 | Открытая отложенная запись; не разрешение реализации/цен | D-З16 |
| З17 | Открытая отложенная запись; не разрешение реализации/цен | D-З17 |
| З18 | Открытая отложенная запись; не разрешение реализации/цен | D-З18 |
| З19 | Открытая отложенная запись; не разрешение реализации/цен | D-З19 |
| З20 | Открытая отложенная запись; не разрешение реализации/цен | D-З20 |
| З21 | Открытая отложенная запись; не разрешение реализации/цен | D-З21 |
| З22 | Открытая отложенная запись; не разрешение реализации/цен | D-З22 |
| З23 | Открытая отложенная запись; не разрешение реализации/цен | D-З23 |
| З24 | Открытая отложенная запись; не разрешение реализации/цен | D-З24 |
| З25 | Открытая отложенная запись; не разрешение реализации/цен | D-З25 |
| З26 | Открытая отложенная запись; не разрешение реализации/цен | D-З26 |
| З27 | Открытая отложенная запись; не разрешение реализации/цен | D-З27 |
| З28 | Открытая отложенная запись; не разрешение реализации/цен | D-З28 |
| З29 | Открытая отложенная запись; не разрешение реализации/цен | D-З29 |
| З30 | Реализовано кодом 0.3.308–310; не повторный TODO; ручная приёмка D-VAL1 | CHALKBOARD.md / HISTORY.md |
| З31 | Открытая отложенная запись; не разрешение реализации/цен | D-З31 |
| З32 | Открытая отложенная запись; не разрешение реализации/цен | D-З32 |
| З33 | Открытая отложенная запись; не разрешение реализации/цен | D-З33 |
| З34 | Открытая отложенная запись; не разрешение реализации/цен | D-З34 |
| З35 | Открытая отложенная запись; не разрешение реализации/цен | D-З35 |
| З36 | Открытая отложенная запись; не разрешение реализации/цен | D-З36 |
| З37 | Открытая отложенная запись; не разрешение реализации/цен | D-З37 |
| З38 | Открытая отложенная запись; не разрешение реализации/цен | D-З38 |
| З39 | Открытая отложенная запись; не разрешение реализации/цен | D-З39 |
| З40 | Открытая отложенная запись; не разрешение реализации/цен | D-З40 |
| З41 | Открытая отложенная запись; не разрешение реализации/цен | D-З41 |
| З42 | Открытая отложенная запись; не разрешение реализации/цен | D-З42 |
| З43 | Частично: базовые страницы есть; в очереди только остаток | D-З43 |
| З44 | Открытая отложенная запись; не разрешение реализации/цен | D-З44 |
| З45 | Открытая отложенная запись; не разрешение реализации/цен | D-З45 |
| З46 | Открытая отложенная запись; не разрешение реализации/цен | D-З46 |
| З47 | Открытая отложенная запись; не разрешение реализации/цен | D-З47 |
| З48 | Открытая отложенная запись; не разрешение реализации/цен | D-З48 |
| З49 | Реализовано кодом 0.3.308–310; не повторный TODO; ручная приёмка D-VAL1 | CHALKBOARD.md / HISTORY.md |
| З50 | Открытая отложенная запись; не разрешение реализации/цен | D-З50 |
| З51 | Реализовано кодом 0.3.308–310; не повторный TODO; ручная приёмка D-VAL1 | CHALKBOARD.md / HISTORY.md |
| З52 | Открытая отложенная запись; не разрешение реализации/цен | D-З52 |
| П1 | Открытая отложенная запись; не разрешение реализации/цен | D-P1 |
| П2 | Открытая отложенная запись; не разрешение реализации/цен | D-P2 |
| П3 | Выполнено 0.3.305: комментарий шлаков → ПЦФСОЗ | HISTORY.md / MACHINES.md |
## Границы полноты

Хеши доказывают отсутствие потерянных/изменённых файлов; реестр заголовков/пунктов даёт адрес каждого исходного свидетельства. Это не математическое доказательство, что ни одна авторская мысль не требует будущего уточнения. В сомнительных местах сохранены источник и неопределённость, а не выдуманы даты/вердикты/новые задачи. Полный старый текст остаётся доступен даже там, где новая текущая спецификация короче.
