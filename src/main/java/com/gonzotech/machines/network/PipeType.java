package com.gonzotech.machines.network;

import com.gonzotech.core.text.GtUnits;

/**
 * Тип трубы = какой ресурс она переносит. Трубы одного типа образуют связную цепь
 * для слива ({@link PipeRouting}); трубы разных типов друг друга игнорируют
 * (провод не коннектится к теплотрубе, водная — к паровой).
 * <p>
 * <b>Углы сечения и «жидкостное семейство».</b> В пучке ровно 4 угла сечения
 * (см. {@link PipeGeometry#corner}): WIRE — верх-лево, FLUID — верх-право, HEAT —
 * низ-лево, ITEM — низ-право. Углы WIRE/HEAT занимают энергетические типы. Угол
 * FLUID — общий для ВСЕГО жидкостного семейства ({@link #isFluid()}): вода, пар,
 * а также жидкости Эпохи III (брага, сусло, дистиллят, ретификат, кипяток, зелье).
 * Раз FLUID-угол один, в одном пучке может быть максимум ОДНА жидкостная труба —
 * они физически делят один угол.
 */
public enum PipeType {

    /**
     * Провод: переносит GTU («электричество»), отдаёт в {@code GtuSink}. Угол WIRE.
     * Макс. проводимость 38 GTU/t (в milli: 38000 mGTU/t).
     */
    WIRE("first_wire", false, GtUnits.GTU, 38 * 1000, GtUnits.U_GTU),

    /**
     * Теплотруба: переносит GTH (тепло), отдаёт в {@code GthSink}. Угол HEAT.
     * Макс. проводимость 388 GTH/t (в milli: 388000 mGTH/t).
     */
    HEAT("first_heat_pipe", false, GtUnits.GTH, 388 * 1000, GtUnits.U_GTH),

    /** Водная труба: переносит воду (mB), отдаёт в {@code WaterSink}. Угол FLUID. Макс. 1000 mB/t. */
    WATER("first_water_pipe", true, GtUnits.WATER, 1000, GtUnits.U_MB),

    /** Паровая труба: переносит пар (mB), отдаёт в {@code SteamSink}. Угол FLUID. Макс. 1000 mB/t. */
    STEAM("first_steam_pipe", true, GtUnits.STEAM, 1000, GtUnits.U_MB),

    /**
     * Предметная труба: переносит ПРЕДМЕТЫ (не mB/GTU), угол ITEM (низ-право).
     * В отличие от жидкостей/энергий не пассивна: узлы/трубы этого типа тикают и
     * мгновенно маршрутизируют предметы источник→приёмник (см. {@code ItemRouting}),
     * ничего не храня в трубах.
     */
    ITEM("first_item_pipe", false, GtUnits.ITEM, 5, GtUnits.U_ITEMS),

    // ──────────────────────── Жидкости Эпохи III (автор 22.09) ────────────────────────
    // Текут только через универсальные жидкостные трубы/узлы (Тир 1 и Тир 2) и
    // универсальные узлы. Одиночных труб под них нет by design.

    /**
     * Брага: динамическая жидкость брожения (% спирта 0..13, % гнили 0..98).
     * Скорость ×0.5 от воды (база 500 mB/t). Цвет: #b84a28.
     */
    MASH("mash", true, 0xB84A28, 500, GtUnits.U_MB),

    /**
     * Сусло: выпаренная брага (% спирта 0..30).
     * Скорость как у воды (база 1000 mB/t). Цвет: #ffd582.
     */
    WORT("wort", true, 0xFFD582, 1000, GtUnits.U_MB),

    /**
     * Дистиллят: продукт дистилляции (константный 48% спирт).
     * Скорость как у воды (база 1000 mB/t). Цвет: #8bd3fc.
     */
    DISTILLATE("distillate", true, 0x8BD3FC, 1000, GtUnits.U_MB),

    /**
     * Ретификат: чистый спирт (100%), сырьё для полимеров и химического завода.
     * Скорость как у воды (база 1000 mB/t). Цвет: #8affe9.
     */
    RECTIFICATE("rectificate", true, 0x8AFFE9, 1000, GtUnits.U_MB),

    /**
     * Кипяток / горячая вода: побочный продукт дистиллятора. Охлаждается в конденсаторе.
     * Скорость как у воды (база 1000 mB/t). Цвет: #4eb8f5.
     */
    BOILING_WATER("hot_water", true, 0x4EB8F5, 1000, GtUnits.U_MB),

    /**
     * Зелье отравления II: результат перегонки гнилой браги (>8% гнили).
     * Скорость как у воды (база 1000 mB/t). Цвет: #839c66.
     */
    POISON_POTION("poison_potion", true, 0x839C66, 1000, GtUnits.U_MB),

    /**
     * Серная кислота: кислотный реагент наполнителя. Скорость как у воды (1000 mB/t). Цвет: #c8ff9e.
     */
    SULFURIC_ACID("sulfuric_acid", true, 0xC8FF9E, 1000, GtUnits.U_MB),

    /**
     * Этилен: мономер для полиэтилена. Скорость как у воды (1000 mB/t). Цвет: #42ff94.
     */
    ETHYLENE("ethylene", true, 0x42FF94, 1000, GtUnits.U_MB),

    /**
     * Аминоблейзатанол: реактивный органический раствор. Скорость как у воды (1000 mB/t). Цвет: #ffda05.
     */
    AMINOBLAZEETHANOL("aminoblazeethanol", true, 0xFFDA05, 1000, GtUnits.U_MB),

    /**
     * Формальдегид: едкий альдегид. Скорость как у воды (1000 mB/t). Цвет: #8374a6.
     */
    FORMALDEHYDE("formaldehyde", true, 0x8374A6, 1000, GtUnits.U_MB);

    private final String id;
    private final boolean fluid;
    private final int color;
    private final long maxThroughput;
    private final String unitKey;

    PipeType(String id, boolean fluid, int color, long maxThroughput, String unitKey) {
        this.id = id;
        this.fluid = fluid;
        this.color = color;
        this.maxThroughput = maxThroughput;
        this.unitKey = unitKey;
    }

    /** Строковый id (совпадает с id блока/предмета трубы для базовых 5 типов). */
    public String id() {
        return id;
    }

    /**
     * Принадлежит ли тип «жидкостному семейству» (вода/пар/брага/сусло/дистиллят/…):
     * все такие типы делят один угол сечения FLUID.
     */
    public boolean isFluid() {
        return fluid;
    }

    /**
     * Цвет ресурса (RGB) для подсветки в HUD ключа.
     */
    public int color() {
        return color;
    }

    /**
     * Lang-ключ обозначения единицы этого ресурса («GTU», «GTH», «mB», «items»).
     */
    public String unitKey() {
        return unitKey;
    }

    /**
     * Макс. пропускная способность одной трубы/узла этого типа за тик.
     */
    public long maxThroughput() {
        return maxThroughput;
    }
}
