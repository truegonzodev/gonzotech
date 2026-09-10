package com.gonzotech.machines.network;

/**
 * Тип трубы = какой ресурс она переносит. Трубы одного типа образуют связную цепь
 * для слива ({@link PipeRouting}); трубы разных типов друг друга игнорируют
 * (провод не коннектится к теплотрубе, водная — к паровой).
 * <p>
 * <b>Углы сечения и «жидкостное семейство».</b> В пучке ровно 4 угла сечения
 * (см. {@link PipeGeometry#corner}): WIRE — верх-лево, FLUID — верх-право, HEAT —
 * низ-лево, ITEM — низ-право. Углы WIRE/HEAT занимают энергетические типы. Угол
 * FLUID — общий для ВСЕГО жидкостного семейства ({@link #isFluid()}): вода, пар
 * (в будущем гелий и т.п.). Раз FLUID-угол один, в одном пучке может быть максимум
 * ОДНА жидкостная труба (вода ЛИБО пар) — они физически делят один угол. Это и
 * снимает вопрос «труба то воду, то пар»: труба переносит строго один ресурс, а
 * разные жидкости — это разные трубы, конкурирующие за общий угол пучка.
 */
public enum PipeType {

    /**
     * Провод: переносит GTU («электричество»), отдаёт в {@code GtuSink}. Угол WIRE.
     * Макс. проводимость 38 GTU/t (в milli: 38000 mGTU/t).
     */
    WIRE("first_wire", false, 0xFFD84A, 38 * 1000),

    /**
     * Теплотруба: переносит GTH (тепло), отдаёт в {@code GthSink}. Угол HEAT.
     * Макс. проводимость 388 GTH/t (в milli: 388000 mGTH/t).
     */
    HEAT("first_heat_pipe", false, 0xFF6A4A, 388 * 1000),

    /** Водная труба: переносит воду (mB), отдаёт в {@code WaterSink}. Угол FLUID. Макс. 1000 mB/t. */
    WATER("first_water_pipe", true, 0x4AA3FF, 1000),

    /** Паровая труба: переносит пар (mB), отдаёт в {@code SteamSink}. Угол FLUID. Макс. 1000 mB/t. */
    STEAM("first_steam_pipe", true, 0xD8D8D8, 1000),

    /**
     * Предметная труба: переносит ПРЕДМЕТЫ (не mB/GTU), угол ITEM (низ-право).
     * В отличие от жидкостей/энергий не пассивна: узлы/трубы этого типа тикают и
     * мгновенно маршрутизируют предметы источник→приёмник (см. {@code ItemRouting}),
     * ничего не храня в трубах. Значение {@code maxThroughput} здесь — предел
     * ПРЕДМЕТОВ за тик через одну точку забора: 5 шт/т СУММАРНО, но не более
     * 1 шт/т на КАЖДЫЙ конкретный вид (см. {@code ItemRouting.PER_ITEM_TICK_CAP}),
     * поэтому одновременно едет максимум 5 разных видов.
     */
    ITEM("first_item_pipe", false, 0xC08A4A, 5);

    private final String id;
    private final boolean fluid;
    private final int color;
    private final long maxThroughput;

    PipeType(String id, boolean fluid, int color, long maxThroughput) {
        this.id = id;
        this.fluid = fluid;
        this.color = color;
        this.maxThroughput = maxThroughput;
    }

    /** Строковый id (совпадает с id блока/предмета трубы). */
    public String id() {
        return id;
    }

    /**
     * Принадлежит ли тип «жидкостному семейству» (вода/пар/…): все такие типы
     * делят один угол сечения FLUID и потому взаимоисключающи в одном пучке.
     */
    public boolean isFluid() {
        return fluid;
    }

    /** Цвет ресурса (RGB) для подсветки в HUD ключа. */
    public int color() {
        return color;
    }

    /**
     * Макс. пропускная способность одной трубы/узла этого типа за тик, в тех же
     * единицах, что и бюджет слива ({@code drain}): для энергий — milli (mGTU/mGTH),
     * для жидкостей — mB. Клипом этой величины ограничивается объём, который машина
     * может слить за тик через цепь труб данного типа
     * ({@link PipeRouting#drain}). Значения: WIRE 38 GTU/t, HEAT 388 GTH/t,
     * WATER/STEAM по 1000 mB/t.
     */
    public long maxThroughput() {
        return maxThroughput;
    }
}
