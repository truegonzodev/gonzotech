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
     * ничего не храня в трубах. Значение {@code maxThroughput} здесь — предел
     * ПРЕДМЕТОВ за тик через одну точку забора: 5 шт/т СУММАРНО, но не более
     * 1 шт/т на КАЖДЫЙ конкретный вид (см. {@code ItemRouting.PER_ITEM_TICK_CAP}),
     * поэтому одновременно едет максимум 5 разных видов.
     */
    ITEM("first_item_pipe", false, GtUnits.ITEM, 5, GtUnits.U_ITEMS);

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

    /**
     * Цвет ресурса (RGB) для подсветки в HUD ключа. Константы живут в
     * {@link GtUnits} — это ЕДИНЫЙ источник цвета единицы: тот же цвет обязан
     * стоять на числе и обозначении в GUI станков, тултипах и отчётах приборов
     * (ГОСТ единиц, автор 22.09.2026).
     */
    public int color() {
        return color;
    }

    /**
     * Lang-ключ обозначения единицы этого ресурса («GTU», «GTH», «mB», «items»).
     * Хвост времени («/t») сюда НЕ входит: его подставляет {@code GtUnits} и он
     * остаётся цветом основного текста строки.
     */
    public String unitKey() {
        return unitKey;
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
