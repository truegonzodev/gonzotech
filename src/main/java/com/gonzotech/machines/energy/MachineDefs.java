package com.gonzotech.machines.energy;

/**
 * Глобальные определения энергосистемы Gonzo Tech (Фаза 2, паровая ветка).
 * <p>
 * Единственный источник правды для того, СКОЛЬКО каждого ресурса
 * ({@code GTH}, {@code GTU}, {@code Steam}, {@code Water}) МОГУТ хранить
 * функциональные блоки, для скоростей производства/потребления, для скоростей
 * передачи соседям (макс. отдача) и приёма (макс. приём), а также для
 * паразитных потерь.
 * <p>
 * Идея: не прописывать балансные числа в каждом BlockEntity, а брать их отсюда.
 * Меняешь баланс в одном месте — меняется везде.
 *
 * <h2>Ресурсы</h2>
 * <ul>
 *   <li>{@code GTH} — GonzoTechHeat, тепло. Производит топка, потребляет котёл.</li>
 *   <li>{@code Steam} — пар, в mB. Производит котёл, потребляет стирлинг.</li>
 *   <li>{@code GTU} — GonzoTechUnits, «электричество». Производит стирлинг,
 *       потребляет электропечь.</li>
 *   <li>{@code Water} — вода, в mB.</li>
 * </ul>
 *
 * <h2>Паровая цепочка (замкнутый цикл по воде)</h2>
 * <pre>
 *   [Топка] --GTH--> [Котёл] --Steam--> [Стирлинг] --GTU--> [Электропечь]
 *      уголь        Water→Steam        Steam→GTU+Water          (переплавка)
 *                        ^─────────── возврат воды ────────────┘
 * </pre>
 *
 * <h2>Железное правило передачи</h2>
 * Отдача ресурса нескольким соседям-приёмникам — РАВНОМЕРНАЯ (не приоритетная):
 * см. {@link Transfer#distribute}. Если у источника N приёмников — бюджет
 * делится между ними поровну.
 */
public final class MachineDefs {

    private MachineDefs() {
    }

    // ═══════════════════════════ ТОПКА (Firebox) ═══════════════════════════
    // Уголь → GTH. Плавит предметы всегда, пока горит топливо (даже при полной
    // шкале GTH). Скорость плавки зависит от запаса GTH.

    /** Максимум GTH в топке. */
    public static final int FIREBOX_GTH_CAPACITY = 24_000;

    /** GTH, вырабатываемое топкой за тик горения (не зависит от вида топлива). */
    public static final int FIREBOX_GTH_PER_TICK = 9;

    /** Паразитная потеря GTH топкой за тик (всегда). */
    public static final int FIREBOX_GTH_LOSS = 1;

    /** Макс. отдача GTH соседям за тик. */
    public static final int FIREBOX_GTH_OUTPUT = 80;

    /** Базовое (ванильное) время переплавки, тиков — fallback, если у рецепта нет своего. */
    public static final int FIREBOX_BASE_COOK_TIME = 200;

    // Скорость плавки в промилле (1000 = 100% ванильной скорости) в зависимости
    // от запаса GTH: 0 GTH → 80%, MID GTH → 100%, полная шкала → 120%.
    public static final int FIREBOX_SPEED_MIN_PERMILLE = 800;   // при 0 GTH
    public static final int FIREBOX_SPEED_MID_PERMILLE = 1000;  // при FIREBOX_GTH_MID
    public static final int FIREBOX_SPEED_MAX_PERMILLE = 1200;  // при полной шкале
    public static final int FIREBOX_GTH_MID = 10_000;

    /**
     * Множитель скорости плавки топки (в промилле) для запаса GTH.
     * Кусочно-линейная интерполяция 80% → 100% → 120%.
     */
    public static int fireboxSpeedPermille(int gth) {
        int g = Math.max(0, Math.min(FIREBOX_GTH_CAPACITY, gth));
        if (g <= FIREBOX_GTH_MID) {
            // 800 → 1000 на отрезке [0, MID]
            return FIREBOX_SPEED_MIN_PERMILLE
                + (FIREBOX_SPEED_MID_PERMILLE - FIREBOX_SPEED_MIN_PERMILLE) * g / FIREBOX_GTH_MID;
        }
        // 1000 → 1200 на отрезке [MID, CAP]
        int span = FIREBOX_GTH_CAPACITY - FIREBOX_GTH_MID;
        return FIREBOX_SPEED_MID_PERMILLE
            + (FIREBOX_SPEED_MAX_PERMILLE - FIREBOX_SPEED_MID_PERMILLE) * (g - FIREBOX_GTH_MID) / span;
    }

    // ═══════════════════════════ ПАРОВОЙ КОТЁЛ (Boiler) ═══════════════════════════
    // Water → Steam, тратя GTH. Работает ТОЛЬКО при примыкающей топке.

    /** Максимум GTH в котле. */
    public static final int BOILER_GTH_CAPACITY = 24_000;
    /** Максимум воды в котле, mB. */
    public static final int BOILER_WATER_CAPACITY = 12_000;
    /** Максимум пара в котле, mB. */
    public static final int BOILER_STEAM_CAPACITY = 12_000;

    /** Пар (mB), вырабатываемый котлом за тик работы. */
    public static final int BOILER_STEAM_PER_TICK = 20;
    /** Вода (mB), потребляемая котлом за тик работы. */
    public static final int BOILER_WATER_PER_TICK = 20;
    /** GTH, потребляемое котлом за тик работы. */
    public static final int BOILER_GTH_PER_TICK = 22;

    /** Паразитная конденсация пара котлом за тик: пар→вода 1:1, ТОЛЬКО если пар есть. */
    public static final int BOILER_STEAM_LOSS = 1;
    /**
     * @deprecated вода теперь конденсируется из своего же пара (1:1 к
     *     {@link #BOILER_STEAM_LOSS}), а не создаётся из воздуха. Оставлено для
     *     справки; в тик-логике не используется.
     */
    @Deprecated
    public static final int BOILER_WATER_GAIN = 1;
    /** Паразитная потеря GTH котлом за тик (всегда). */
    public static final int BOILER_GTH_LOSS = 1;

    /** Макс. отдача пара соседям за тик. */
    public static final int BOILER_STEAM_OUTPUT = 80;
    /** Макс. приём GTH за тик. */
    public static final int BOILER_GTH_INTAKE = 64;
    /** Макс. приём воды за тик (от генератора). */
    public static final int BOILER_WATER_INTAKE = 80;

    // ═══════════════════════════ ГЕНЕРАТОР СТИРЛИНГА (Stirling) ═══════════════════════════
    // 40 пара → 2 GTU + возврат воды в котёл (база 6 + 5 за конденсатор). Работает при примыкающем котле.

    /** Максимум пара в стирлинге, mB. */
    public static final int STIRLING_STEAM_CAPACITY = 12_000;
    /** Максимум GTU в стирлинге. */
    public static final int STIRLING_GTU_CAPACITY = 120;
    /** Внутренний буфер воды на возврат (не показывается в GUI), mB. */
    public static final int STIRLING_WATER_CAPACITY = 12_000;

    /** Пар (mB), потребляемый стирлингом за тик работы. */
    public static final int STIRLING_STEAM_PER_TICK = 40;
    /**
     * Базовая вода (mB), возвращаемая в котёл за тик работы БЕЗ конденсаторов.
     * Раньше было 1:1 (40), теперь стирлинг «сбрасывает» большую часть пара как
     * потери, отдавая обратно лишь малую воду — цикл нуждается в конденсаторах.
     */
    public static final int STIRLING_WATER_BASE_PER_TICK = 6;
    /** +вода (mB) за каждый примыкающий конденсатор ({@link #STIRLING_WATER_BASE_PER_TICK} + n·это). */
    public static final int STIRLING_WATER_PER_CONDENSER = 5;
    /** GTU, вырабатываемое стирлингом за тик работы. */
    public static final int STIRLING_GTU_PER_TICK = 2;

    /** Итоговая водоотдача стирлинга за тик при {@code n} примыкающих конденсаторах. */
    public static int stirlingWaterPerTick(int condensers) {
        return STIRLING_WATER_BASE_PER_TICK + Math.max(0, condensers) * STIRLING_WATER_PER_CONDENSER;
    }

    /** Паразитная конденсация пара стирлингом за тик: пар→вода 1:1, ТОЛЬКО если пар есть. */
    public static final int STIRLING_STEAM_LOSS = 1;
    /**
     * @deprecated вода теперь конденсируется из своего же пара (1:1 к
     *     {@link #STIRLING_STEAM_LOSS}), а не создаётся из воздуха — иначе при
     *     нескольких котлах-соседях был дюп воды. Оставлено для справки.
     */
    @Deprecated
    public static final int STIRLING_WATER_GAIN = 1;

    /** Макс. приём пара за тик. */
    public static final int STIRLING_STEAM_INTAKE = 80;
    /** Макс. отдача воды соседям (котлу) за тик. */
    public static final int STIRLING_WATER_OUTPUT = 80;
    /** Макс. отдача GTU соседям за тик. */
    public static final int STIRLING_GTU_OUTPUT = 40;

    // ═══════════════════════════ ЭЛЕКТРОПЕЧЬ (Electric Furnace) ═══════════════════════════
    // GTU → переплавка (160% ванили). Работает при примыкающем стирлинге.

    /** Максимум GTU в электропечи. */
    public static final int ELECTRIC_GTU_CAPACITY = 4_800;

    /** Время переплавки одного предмета, тиков (160% скорости → 200/1.6 = 125). */
    public static final int ELECTRIC_COOK_TIME = 125;

    /** Суммарный расход GTU на один переплавленный предмет (≈1.6 GTU/t). */
    public static final int ELECTRIC_GTU_PER_ITEM = 200;

    /** Макс. приём GTU за тик. */
    public static final int ELECTRIC_GTU_INTAKE = 64;

    // ═══════════════════════════ ВОДА (провайдеры-вёдра) ═══════════════════════════

    /** Обычное ведро воды → mB. */
    public static final int WATER_PER_BUCKET = 1_000;
    /** Ведро с рыхлым снегом → mB. */
    public static final int WATER_PER_POWDER_SNOW = 500;
    /** Ведро с рыбой → mB (и спавнит рыбу-сущность). */
    public static final int WATER_PER_FISH_BUCKET = 1_000;
}
