package com.gonzotech.machines.network;

/**
 * Тип трубы = какой ресурс она переносит. Трубы одного типа соединяются между
 * собой в общий контур ({@link EnergyNetwork}); трубы разных типов друг друга
 * игнорируют (провод не коннектится к теплотрубе).
 * <p>
 * Пока реализованы только энергетические типы Фазы 2 ({@code WIRE} → GTU,
 * {@code HEAT} → GTH). Жидкостные и предметные трубы добавятся позже — тогда
 * сюда просто дописываются новые значения, а логика забора/отдачи для них — в
 * {@link EnergyNetwork}.
 */
public enum PipeType {

    /** Провод: переносит GTU («электричество»). Тянет из {@code GtuSource}, отдаёт в {@code GtuSink}. */
    WIRE("wire"),

    /** Теплотруба: переносит GTH (тепло). Тянет из {@code GthSource}, отдаёт в {@code GthSink}. */
    HEAT("heat_pipe");

    private final String id;

    PipeType(String id) {
        this.id = id;
    }

    /** Строковый id (совпадает с id блока/предмета трубы). */
    public String id() {
        return id;
    }
}
