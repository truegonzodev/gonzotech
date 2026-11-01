# Редакции329 перед бюджетом330

Полные неизменённые предыдущие тексты exact9c2d9b3; SHA-256/байты/Git-источник — [manifest](manifest.json). Это **не текущие спецификации**.

- [CURRENT-STATE.md](OUTDATED_BEFORE_BUDGET330_CURRENT-STATE.md).
- [AUTOMINER.md](OUTDATED_BEFORE_BUDGET330_AUTOMINER.md).
- [CAPABILITIES.md](OUTDATED_BEFORE_BUDGET330_CAPABILITIES.md).
- [MACHINES.md](OUTDATED_BEFORE_BUDGET330_MACHINES.md).
- [RAD_ITEMS.md](OUTDATED_BEFORE_BUDGET330_RAD_ITEMS.md).
- [AUDIT.md](OUTDATED_BEFORE_BUDGET330_AUDIT.md).

Автор сообщил «Всё штатно», но прямо оставил6/10 открытыми. Дальнейшее решение: не дробить расчёты; два цельных расчёта кнопок/GUI на серверный тик, общая FIFO всех миров; автоматический40тиковый цикл буров не менять. Сетевой порог server TOML, default128/скользящаясекунда/ожидание, диапазон1–4096, всегда disconnect при превышении. Замена прежних slice-ожиданий — решение автора, не забытая задача. [Текущий статус](../../../CURRENT-STATE.md), [история330](../../../HISTORY.md#perf-001-budget330).
