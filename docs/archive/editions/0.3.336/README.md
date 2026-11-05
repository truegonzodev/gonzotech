# Снимки документов перед микропатчем 0.3.336

**Это не действующая спецификация.** Побайтные копии 12 живых документов из commit
`f875c9b26969f2906569d96532dc981e68cdaf09` (приёмка 0.3.335), снятые перед реализацией D-PORTMODE1 — реальных
режимов «забор / отдача / авто» у вшитых корпусных портов.

| Исходный документ | Снимок |
| --- | --- |
| `docs/CURRENT-STATE.md` | [`OUTDATED_BEFORE_336_CURRENT-STATE.md`](OUTDATED_BEFORE_336_CURRENT-STATE.md) |
| `docs/HISTORY.md` | [`OUTDATED_BEFORE_336_HISTORY.md`](OUTDATED_BEFORE_336_HISTORY.md) |
| `docs/DEFFERED.md` | [`OUTDATED_BEFORE_336_DEFFERED.md`](OUTDATED_BEFORE_336_DEFFERED.md) |
| `docs/AUDIT.md` | [`OUTDATED_BEFORE_336_AUDIT.md`](OUTDATED_BEFORE_336_AUDIT.md) |
| `docs/RULES.md` | [`OUTDATED_BEFORE_336_RULES.md`](OUTDATED_BEFORE_336_RULES.md) |
| `docs/DOCS_RULES.md` | [`OUTDATED_BEFORE_336_DOCS_RULES.md`](OUTDATED_BEFORE_336_DOCS_RULES.md) |
| `docs/AUDIO_VISUAL.md` | [`OUTDATED_BEFORE_336_AUDIO_VISUAL.md`](OUTDATED_BEFORE_336_AUDIO_VISUAL.md) |
| `docs/CAPABILITIES.md` | [`OUTDATED_BEFORE_336_CAPABILITIES.md`](OUTDATED_BEFORE_336_CAPABILITIES.md) |
| `docs/REACTORS.md` | [`OUTDATED_BEFORE_336_REACTORS.md`](OUTDATED_BEFORE_336_REACTORS.md) |
| `docs/MULTIBLOCK.md` | [`OUTDATED_BEFORE_336_MULTIBLOCK.md`](OUTDATED_BEFORE_336_MULTIBLOCK.md) |
| `docs/MACHINES.md` | [`OUTDATED_BEFORE_336_MACHINES.md`](OUTDATED_BEFORE_336_MACHINES.md) |
| `docs/archive/README.md` | [`OUTDATED_BEFORE_336_archive-README.md`](OUTDATED_BEFORE_336_archive-README.md) |

`manifest.json` (schema 1) хранит SHA-256, размер и полный исходный commit для
каждого файла; побайтная сверка с Git — в `audit/docs_snapshots.py`. Относительные
ссылки внутри снимков намеренно не переписывались (правило 4 DOCS_RULES).

Действующие документы: [CURRENT-STATE](../../../CURRENT-STATE.md),
[HISTORY](../../../HISTORY.md#v-0-3-336), [AUDIT](../../../AUDIT.md#portmode336),
[DEFFERED](../../../DEFFERED.md), [RULES](../../../RULES.md),
[индекс архива](../../README.md). Удалённые короткие фрагменты процитированы
дословно в HISTORY; снимки не переписывались.
