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

    /** Провод: переносит GTU («электричество»), отдаёт в {@code GtuSink}. Угол WIRE. */
    WIRE("wire", false, 0xFFD84A),

    /** Теплотруба: переносит GTH (тепло), отдаёт в {@code GthSink}. Угол HEAT. */
    HEAT("heat_pipe", false, 0xFF6A4A),

    /** Водная труба: переносит воду (mB), отдаёт в {@code WaterSink}. Угол FLUID. */
    WATER("water_pipe", true, 0x4AA3FF),

    /** Паровая труба: переносит пар (mB), отдаёт в {@code SteamSink}. Угол FLUID. */
    STEAM("steam_pipe", true, 0xD8D8D8);

    private final String id;
    private final boolean fluid;
    private final int color;

    PipeType(String id, boolean fluid, int color) {
        this.id = id;
        this.fluid = fluid;
        this.color = color;
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
}
