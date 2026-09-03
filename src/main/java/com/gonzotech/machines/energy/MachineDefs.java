package com.gonzotech.machines.energy;

/**
 * Глобальные определения энергосистемы Gonzo Tech (Фаза 2, паровая ветка).
 * <p>
 * Единственный источник правды для того, СКОЛЬКО каждого ресурса
 * ({@code GTH}, {@code GTU}, {@code Steam}, {@code Water}) МОГУТ хранить
 * функциональные блоки, а также для скоростей передачи между соседями и
 * скоростей производства/потребления в цепочке.
 * <p>
 * Идея: не прописывать ёмкости и балансные числа в каждом BlockEntity,
 * а брать их отсюда. Меняешь баланс в одном месте — меняется везде.
 *
 * <h2>Ресурсы</h2>
 * <ul>
 *   <li>{@code GTH} — GonzoTechHeat, тепло. Производит топка, потребляет котёл.</li>
 *   <li>{@code Steam} — пар, в mB. Производит котёл, потребляет стирлинг.</li>
 *   <li>{@code GTU} — GonzoTechUnits, «электричество» (аналог RF/EU).
 *       Производит стирлинг, потребляет электропечь.</li>
 *   <li>{@code Water} — вода, в mB (1 ведро = {@link #WATER_PER_BUCKET} mB).</li>
 * </ul>
 *
 * <h2>Паровая цепочка</h2>
 * <pre>
 *   [Топка] --GTH--> [Паровой котёл] --Steam--> [Генератор Стирлинга] --GTU--> [Электропечь]
 *      уголь              + вода                       (пар)                    (переплавка)
 * </pre>
 */
public final class MachineDefs {

    private MachineDefs() {
    }

    // ─────────────────────────── ЁМКОСТИ (сколько блок МОЖЕТ хранить) ───────────────────────────

    /** Максимум GTH (тепла) в буфере блока. */
    public static final int GTH_CAPACITY = 24_000;

    /** Максимум GTU («электричества») в буфере блока. */
    public static final int GTU_CAPACITY = 100_000;

    /** Максимум пара, mB. */
    public static final int STEAM_CAPACITY = 12_000;

    /** Максимум воды, mB. */
    public static final int WATER_CAPACITY = 12_000;

    // ─────────────────────────── ПЕРЕДАЧА между соседними блоками (за тик) ───────────────────────────

    /** Сколько GTH топка отдаёт котлу за тик. */
    public static final int GTH_TRANSFER = 200;

    /** Сколько пара котёл отдаёт стирлингу за тик. */
    public static final int STEAM_TRANSFER = 200;

    /** Сколько GTU стирлинг отдаёт электропечи за тик. */
    public static final int GTU_TRANSFER = 500;

    // ─────────────────────────── ТОПКА (Firebox): уголь → GTH ───────────────────────────

    /** GTH, вырабатываемое топкой за тик, пока горит топливо. */
    public static final int FIREBOX_GTH_PER_TICK = 40;

    /** Базовое время переплавки «казуальной побочки» топки, тиков. */
    public static final int FIREBOX_COOK_TIME = 200;

    // ─────────────────────────── ПАРОВОЙ КОТЁЛ (Boiler): GTH + вода → пар ───────────────────────────

    /** GTH, потребляемое котлом за тик работы. */
    public static final int BOILER_GTH_PER_TICK = 20;

    /** Вода (mB), потребляемая котлом за тик работы. */
    public static final int BOILER_WATER_PER_TICK = 10;

    /** Пар (mB), вырабатываемый котлом за тик работы. */
    public static final int BOILER_STEAM_PER_TICK = 20;

    /** Сколько mB воды даёт одно ведро воды. */
    public static final int WATER_PER_BUCKET = 1_000;

    // ─────────────────────────── ГЕНЕРАТОР СТИРЛИНГА (Stirling): пар → GTU ───────────────────────────

    /** Пар (mB), потребляемый стирлингом за тик работы. */
    public static final int STIRLING_STEAM_PER_TICK = 20;

    /** GTU, вырабатываемое стирлингом за тик работы. */
    public static final int STIRLING_GTU_PER_TICK = 40;

    // ─────────────────────────── ЭЛЕКТРОПЕЧЬ (Electric Furnace): GTU → переплавка ───────────────────────────

    /** GTU, потребляемое электропечью за тик переплавки. */
    public static final int ELECTRIC_GTU_PER_TICK = 24;

    /** Базовое время переплавки электропечи, тиков (при наличии GTU). */
    public static final int ELECTRIC_COOK_TIME = 160;
}
