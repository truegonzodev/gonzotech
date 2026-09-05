package com.gonzotech.machines.network;

/**
 * Тип трубы = какой ресурс она переносит. Трубы одного типа образуют связную цепь
 * для слива ({@link PipeRouting}); трубы разных типов друг друга игнорируют
 * (провод не коннектится к теплотрубе).
 * <p>
 * Пока реализованы только энергетические типы ({@code WIRE} → GTU,
 * {@code HEAT} → GTH). Жидкостные и предметные трубы добавятся позже — тогда
 * сюда просто дописываются новые значения.
 */
public enum PipeType {

    /** Провод: переносит GTU («электричество»), отдаёт в {@code GtuSink}. */
    WIRE("wire"),

    /** Теплотруба: переносит GTH (тепло), отдаёт в {@code GthSink}. */
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
