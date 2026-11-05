# Снимки документов перед микропатчем 0.3.337

**Это не действующая спецификация.** Побайтные копии 12 живых документов из commit
`b328877971e811fbac63f78b41a5041b44ae4cae` (D-PORTMODE1, 0.3.336), снятые перед устранением двух мерцаний HUD ключа,
удалением нижней строки AUTO и закрытием D-PORTMODE1 по приёмке автора.

| Исходный документ | Снимок |
| --- | --- |
| `docs/CURRENT-STATE.md` | [`OUTDATED_BEFORE_337_CURRENT-STATE.md`](OUTDATED_BEFORE_337_CURRENT-STATE.md) |
| `docs/HISTORY.md` | [`OUTDATED_BEFORE_337_HISTORY.md`](OUTDATED_BEFORE_337_HISTORY.md) |
| `docs/DEFFERED.md` | [`OUTDATED_BEFORE_337_DEFFERED.md`](OUTDATED_BEFORE_337_DEFFERED.md) |
| `docs/AUDIT.md` | [`OUTDATED_BEFORE_337_AUDIT.md`](OUTDATED_BEFORE_337_AUDIT.md) |
| `docs/RULES.md` | [`OUTDATED_BEFORE_337_RULES.md`](OUTDATED_BEFORE_337_RULES.md) |
| `docs/DOCS_RULES.md` | [`OUTDATED_BEFORE_337_DOCS_RULES.md`](OUTDATED_BEFORE_337_DOCS_RULES.md) |
| `docs/AUDIO_VISUAL.md` | [`OUTDATED_BEFORE_337_AUDIO_VISUAL.md`](OUTDATED_BEFORE_337_AUDIO_VISUAL.md) |
| `docs/CAPABILITIES.md` | [`OUTDATED_BEFORE_337_CAPABILITIES.md`](OUTDATED_BEFORE_337_CAPABILITIES.md) |
| `docs/REACTORS.md` | [`OUTDATED_BEFORE_337_REACTORS.md`](OUTDATED_BEFORE_337_REACTORS.md) |
| `docs/MULTIBLOCK.md` | [`OUTDATED_BEFORE_337_MULTIBLOCK.md`](OUTDATED_BEFORE_337_MULTIBLOCK.md) |
| `docs/MACHINES.md` | [`OUTDATED_BEFORE_337_MACHINES.md`](OUTDATED_BEFORE_337_MACHINES.md) |
| `docs/archive/README.md` | [`OUTDATED_BEFORE_337_archive-README.md`](OUTDATED_BEFORE_337_archive-README.md) |

`manifest.json` (schema 1) хранит SHA-256, размер и полный исходный commit для
каждого файла; побайтная сверка с Git — в `audit/docs_snapshots.py`. Относительные
ссылки внутри снимков намеренно не переписывались (правило 4 DOCS_RULES).

Действующие документы: [CURRENT-STATE](../../../CURRENT-STATE.md),
[HISTORY](../../../HISTORY.md#v-0-3-337), [AUDIT](../../../AUDIT.md#hud337),
[DEFFERED](../../../DEFFERED.md), [RULES](../../../RULES.md),
[индекс архива](../../README.md). Удалённые короткие фрагменты процитированы
дословно в HISTORY; снимки не переписывались.
