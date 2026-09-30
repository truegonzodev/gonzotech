package com.gonzotech.cleanroom;

import java.util.List;

/**
 * Физика открытых гермодверей (0.3.61, автор). Дверь остаётся герметиком
 * топологии (комната НЕ пересоздаётся при открытии — иначе качество стиралось
 * бы в ноль каждым открытием); вместо этого качество утекает ДИНАМИЧЕСКИ:
 * <ul>
 *   <li>дверь ведёт в открытый воздух — комната теряет
 *       {@link #OUTSIDE_LOSS_PER_SECOND} %/сек;</li>
 *   <li>дверь между двумя контурами — качества выравниваются: за секунду
 *       переходит {@link #EQUALIZE_PER_SECOND} доля разницы (сумма
 *       сохраняется);</li>
 *   <li>дверь внутрь одной комнаты или между двумя улицами — ничего.</li>
 * </ul>
 * Одна дверь может разделять два контура — каждая открытая дверь
 * обрабатывается ровно один раз. Чистый класс без Minecraft: компилируется
 * и проверяется вне игры (audit/door_leak_test.py). Числа — автора
 * (8 %/сек заданы явно; скорость выравнивания утверждать).
 */
public final class DoorLeaks {

    /** Потеря качества комнаты в секунду за дверь в открытый воздух (процентов). */
    public static final double OUTSIDE_LOSS_PER_SECOND = 8.0;
    /** Доля разницы качеств, переходящая за секунду между двумя контурами (0..1). */
    public static final double EQUALIZE_PER_SECOND = 0.08;

    private DoorLeaks() {
    }

    /** Открытая дверь: две стороны прохода; {@code null} — открытый воздух. */
    public record Door(RoomLedger.Room a, RoomLedger.Room b) {
    }

    /** Применить секундную утечку/выравнивание по всем открытым дверям. */
    public static void settle(RoomLedger ledger, List<Door> doors) {
        for (Door d : doors) {
            if (d.a() != null && d.b() != null) {
                double transfer = (d.a().quality() - d.b().quality()) * EQUALIZE_PER_SECOND;
                ledger.adjust(d.a(), -transfer);
                ledger.adjust(d.b(), transfer);
            } else {
                RoomLedger.Room room = d.a() != null ? d.a() : d.b();
                if (room != null) {
                    ledger.adjust(room, -OUTSIDE_LOSS_PER_SECOND);
                }
            }
        }
    }
}
