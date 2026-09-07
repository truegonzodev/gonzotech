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

    // ═══════════════════════════ ЕДИНИЦЫ ИЗМЕРЕНИЯ ═══════════════════════════
    //
    // GTU и GTH хранятся/передаются ВНУТРИ в МИЛЛИ-единицах (mGTU / mGTH):
    //   1 GTU = 1000 mGTU,  1 GTH = 1000 mGTH.
    // Это убирает «проскальзывание сквозь тики» и поломку равномерности: когда
    // бюджет отдачи делится между десятками машин, дискретная 1 GTU не делилась
    // на 120 приёмников; в милли деление точное (ошибка в 1000× меньше), а дробные
    // темпы вроде 1.6 GTU/t = 1600 mGTU/t выражаются РОВНО.
    //
    // Игроку показываем ЦЕЛЫЕ единицы (amount/1000) в шкалах/тултипах и десятые в
    // «поток: 10.2 GTU/t». ВАЖНО: ContainerData синкается как short (макс 32767),
    // поэтому в GUI уходит НЕ милли, а целые единицы (см. get/set в BlockEntity).
    //
    // Вода и пар остаются в mB (ISO/ГОСТ индустриальных модов) — НЕ трогаем.

    /** Сколько милли-единиц в одной отображаемой единице GTU/GTH. */
    public static final int MILLI = 1000;

    /** Милли → целые единицы (для GUI/тултипов), с округлением вниз. */
    public static int toUnits(int milli) {
        return milli / MILLI;
    }

    /** Целые единицы → милли (обратно, для клиентского set в ContainerData). */
    public static int toMilli(int units) {
        return units * MILLI;
    }

    // ═══════════════════════════ ТОПКА (Firebox) ═══════════════════════════
    // Уголь → GTH. Плавит предметы всегда, пока горит топливо (даже при полной
    // шкале GTH). Скорость плавки зависит от запаса GTH.

    /** Максимум GTH в топке (mGTH: 24000 GTH). */
    public static final int FIREBOX_GTH_CAPACITY = 24_000 * MILLI;

    /** GTH, вырабатываемое топкой за тик горения (mGTH: 9 GTH/t, не зависит от топлива). */
    public static final int FIREBOX_GTH_PER_TICK = 9 * MILLI;

    /** Паразитная потеря GTH топкой за тик (mGTH: 1 GTH/t, всегда). */
    public static final int FIREBOX_GTH_LOSS = 1 * MILLI;

    /** Макс. отдача GTH соседям за тик (mGTH: 80 GTH/t). */
    public static final int FIREBOX_GTH_OUTPUT = 80 * MILLI;

    /** Базовое (ванильное) время переплавки, тиков — fallback, если у рецепта нет своего. */
    public static final int FIREBOX_BASE_COOK_TIME = 200;

    // Скорость плавки в промилле (1000 = 100% ванильной скорости) в зависимости
    // от запаса GTH: 0 GTH → 80%, MID GTH → 100%, полная шкала → 120%.
    public static final int FIREBOX_SPEED_MIN_PERMILLE = 800;   // при 0 GTH
    public static final int FIREBOX_SPEED_MID_PERMILLE = 1000;  // при FIREBOX_GTH_MID
    public static final int FIREBOX_SPEED_MAX_PERMILLE = 1200;  // при полной шкале
    /** Середина кривой скорости (mGTH: 10000 GTH). */
    public static final int FIREBOX_GTH_MID = 10_000 * MILLI;

    /**
     * Множитель скорости плавки топки (в промилле) для запаса GTH (в mGTH).
     * Кусочно-линейная интерполяция 80% → 100% → 120%.
     * <p>
     * Счёт в {@code long}: при милли-ёмкости произведение
     * {@code 200 × 14_000_000} переполнило бы {@code int}.
     */
    public static int fireboxSpeedPermille(long gth) {
        long g = Math.max(0L, Math.min((long) FIREBOX_GTH_CAPACITY, gth));
        if (g <= FIREBOX_GTH_MID) {
            // 800 → 1000 на отрезке [0, MID]
            return (int) (FIREBOX_SPEED_MIN_PERMILLE
                + (long) (FIREBOX_SPEED_MID_PERMILLE - FIREBOX_SPEED_MIN_PERMILLE) * g / FIREBOX_GTH_MID);
        }
        // 1000 → 1200 на отрезке [MID, CAP]
        long span = FIREBOX_GTH_CAPACITY - FIREBOX_GTH_MID;
        return (int) (FIREBOX_SPEED_MID_PERMILLE
            + (long) (FIREBOX_SPEED_MAX_PERMILLE - FIREBOX_SPEED_MID_PERMILLE) * (g - FIREBOX_GTH_MID) / span);
    }

    // ═══════════════════════════ ПАРОВОЙ КОТЁЛ (Boiler) ═══════════════════════════
    // Water → Steam, тратя GTH. Работает ТОЛЬКО при примыкающей топке.

    /** Максимум GTH в котле (mGTH: 24000 GTH). */
    public static final int BOILER_GTH_CAPACITY = 24_000 * MILLI;
    /** Максимум воды в котле, mB. */
    public static final int BOILER_WATER_CAPACITY = 12_000;
    /** Максимум пара в котле, mB. */
    public static final int BOILER_STEAM_CAPACITY = 12_000;

    /** Пар (mB), вырабатываемый котлом за тик работы. */
    public static final int BOILER_STEAM_PER_TICK = 20;
    /** Вода (mB), потребляемая котлом за тик работы. */
    public static final int BOILER_WATER_PER_TICK = 20;
    /** GTH, потребляемое котлом за тик работы (mGTH: 22 GTH/t). */
    public static final int BOILER_GTH_PER_TICK = 22 * MILLI;

    /** Паразитная конденсация пара котлом за тик: пар→вода 1:1, ТОЛЬКО если пар есть. */
    public static final int BOILER_STEAM_LOSS = 1;
    /**
     * @deprecated вода теперь конденсируется из своего же пара (1:1 к
     *     {@link #BOILER_STEAM_LOSS}), а не создаётся из воздуха. Оставлено для
     *     справки; в тик-логике не используется.
     */
    @Deprecated
    public static final int BOILER_WATER_GAIN = 1;
    /** Паразитная потеря GTH котлом за тик (mGTH: 1 GTH/t, всегда). */
    public static final int BOILER_GTH_LOSS = 1 * MILLI;

    /** Макс. отдача пара соседям за тик. */
    public static final int BOILER_STEAM_OUTPUT = 80;
    /** Макс. приём GTH за тик (mGTH: 64 GTH/t). */
    public static final int BOILER_GTH_INTAKE = 64 * MILLI;
    /** Макс. приём воды за тик (от генератора). */
    public static final int BOILER_WATER_INTAKE = 80;

    // ═══════════════════════════ ГЕНЕРАТОР СТИРЛИНГА (Stirling) ═══════════════════════════
    // 40 пара → 2 GTU + возврат воды в котёл (база 6 + 5 за конденсатор). Работает при примыкающем котле.

    /** Максимум пара в стирлинге, mB. */
    public static final int STIRLING_STEAM_CAPACITY = 12_000;
    /** Максимум GTU в стирлинге (mGTU: 120 GTU). */
    public static final int STIRLING_GTU_CAPACITY = 120 * MILLI;
    /**
     * Буфер ВОЗВРАТНОЙ воды (конденсата) на слив в котёл, mB. ВИДИМ в GUI как
     * шкала «давление конденсата». Небольшой (2000): полный буфер не стопорит
     * генератор (лишняя вода теряется), но роняет эффективность — стимул провести
     * обратный водный контур.
     */
    public static final int STIRLING_WATER_CAPACITY = 2_000;

    // Эффективность генератора в зависимости от уровня конденсата (mB):
    //   < STIRLING_WATER_EFF_FLOOR         → 100% (буфер почти пуст, слив успевает);
    //   [FLOOR .. STIRLING_WATER_CAPACITY] → линейно 100% → 90% (давление растёт);
    // Генератор НИКОГДА не встаёт из-за воды — только теряет до 10% выработки GTU.
    /** До этого уровня конденсата (mB) выработка на 100%. */
    public static final int STIRLING_WATER_EFF_FLOOR = 100;
    /** Эффективность при почти пустом буфере, промилле (100%). */
    public static final int STIRLING_EFF_MAX_PERMILLE = 1000;
    /** Эффективность при полном буфере, промилле (90%). */
    public static final int STIRLING_EFF_MIN_PERMILLE = 900;

    /**
     * Множитель выработки GTU (в промилле) для текущего уровня конденсата (mB).
     * {@code < FLOOR → 1000}; далее линейно {@code 1000 → 900} к полному буферу.
     * Генератор из-за воды не встаёт — этот множитель лишь снижает выработку.
     */
    public static int stirlingEfficiencyPermille(int waterMb) {
        if (waterMb <= STIRLING_WATER_EFF_FLOOR) return STIRLING_EFF_MAX_PERMILLE;
        int w = Math.min(waterMb, STIRLING_WATER_CAPACITY);
        int span = STIRLING_WATER_CAPACITY - STIRLING_WATER_EFF_FLOOR; // 1900
        int drop = (STIRLING_EFF_MAX_PERMILLE - STIRLING_EFF_MIN_PERMILLE) * (w - STIRLING_WATER_EFF_FLOOR) / span;
        return STIRLING_EFF_MAX_PERMILLE - drop;
    }

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
    /** GTU, вырабатываемое стирлингом за тик работы (mGTU: 2 GTU/t). */
    public static final int STIRLING_GTU_PER_TICK = 2 * MILLI;

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
    /** Макс. отдача GTU соседям за тик (mGTU: 40 GTU/t). */
    public static final int STIRLING_GTU_OUTPUT = 40 * MILLI;

    // ═══════════════════════════ ЭЛЕКТРОПЕЧЬ (Electric Furnace) ═══════════════════════════
    // GTU → переплавка (160% ванили). Работает при примыкающем стирлинге.

    /** Максимум GTU в электропечи (mGTU: 4800 GTU). */
    public static final int ELECTRIC_GTU_CAPACITY = 4_800 * MILLI;

    /** Время переплавки одного предмета, тиков (160% скорости → 200/1.6 = 125). */
    public static final int ELECTRIC_COOK_TIME = 125;

    /** Суммарный расход GTU на один переплавленный предмет (mGTU: 200 GTU → 1.6 GTU/t). */
    public static final int ELECTRIC_GTU_PER_ITEM = 200 * MILLI;

    /**
     * Расход GTU за тик, mGTU (200 GTU / 125 тиков = 1.6 GTU/t = 1600 mGTU/t).
     * Делится нацело → каждый тик списывается РОВНО 1600 mGTU, без аккумулятора.
     */
    public static final int ELECTRIC_GTU_MILLI_PER_TICK = ELECTRIC_GTU_PER_ITEM / ELECTRIC_COOK_TIME;

    /** Макс. приём GTU за тик (mGTU: 64 GTU/t). */
    public static final int ELECTRIC_GTU_INTAKE = 64 * MILLI;

    // ═══════════════════════════ ЭНЕРГОХРАНИЛИЩЕ (Accumulator) ═══════════════════════════
    // Пассивный буфер GTU: принимает излишек от генераторов (реализует GtuSink) и
    // отдаёт запас потребителям, когда те просят. Сглаживает пики перенасыщенного
    // контура — генераторы не встают «в потолок», а потребители не голодают.

    /** Максимум GTU в энергохранилище (mGTU: 10840 GTU — влезает в short как единицы). */
    public static final int ACCUMULATOR_GTU_CAPACITY = 10_840 * MILLI;

    /** Макс. приём GTU за тик (mGTU: 64 GTU/t) — TODO: подтвердить баланс у автора. */
    public static final int ACCUMULATOR_GTU_INTAKE = 64 * MILLI;

    /** Макс. отдача GTU соседям за тик (mGTU: 64 GTU/t) — TODO: подтвердить баланс у автора. */
    public static final int ACCUMULATOR_GTU_OUTPUT = 64 * MILLI;

    /**
     * Паразитная потеря GTU аккумулятором за тик (mGTU: 16 mGTU/t = 0.016 GTU/t).
     * Списывается всегда, ТОЛЬКО если в буфере что-то есть (не создаёт «долг»).
     * Очень мягкая: полный буфер (10840 GTU) саморазряжается за ~9.4 часа АФК.
     */
    public static final int ACCUMULATOR_GTU_LOSS = 16;

    // ═══════════════════════════ ЛОГИСТИКА: ТРУБЫ ═══════════════════════════
    // Провода пассивны (не тикают, не хранят, не буферят) — это «вынос слива за
    // пределы блока». Пропускная способность = существующий *_OUTPUT самой машины
    // (сколько она готова слить за тик), провод его лишь дотягивает до приёмников.
    // Отдельных throughput/buffer-констант у труб НЕТ — кроме универсальной
    // жидкостной трубы ниже (общий бюджет воды+пара).

    /**
     * Суммарная пропускная способность универсальной жидкостной трубы/узла за тик,
     * mB (вода + пар вместе). Обычные первого уровня водная и паровая тянут по
     * 1000 mB/t каждая; универсальная несёт оба ресурса, но их СУММА за тик через
     * одну трубу не превышает этой величины.
     */
    public static final int UNIVERSAL_FLUID_OUTPUT = 800;

    // ═══════════════════════════ ПОМПА (Pump) ═══════════════════════════
    // «Тупая» водокачка паровой эры: есть GTU — работает, нет — стоит (независимо
    // от того, есть ли рядом вода). Сканирует куб 3×3×3 вокруг себя СВЕРХУ ВНИЗ,
    // уничтожает первый найденный источник воды и разово получает 1000 mB. Умеет
    // наполнять вёдра/пузырьки из своей шкалы и сливать воду напрямую соседям
    // (котлу), пока нет жидкостных труб.

    /** Максимум GTU в помпе (mGTU: 1200 GTU). */
    public static final int PUMP_GTU_CAPACITY = 1_200 * MILLI;
    /** Максимум воды в помпе, mB. */
    public static final int PUMP_WATER_CAPACITY = 24_000;

    /** Макс. приём GTU за тик (mGTU: 64 GTU/t, из провода). */
    public static final int PUMP_GTU_INTAKE = 64 * MILLI;

    /**
     * Пассивный расход помпы, mGTU за тик (2800 mGTU = 2.8 GTU/t). Пока есть чем
     * платить — помпа «запитана» и работает; иначе стоит. Теперь, когда весь буфер
     * GTU в милли, списывается РОВНО эта величина каждый тик — без аккумулятора.
     */
    public static final int PUMP_GTU_MILLI_PER_TICK = 2_800;

    /** Сколько mB воды помпа получает за одно всасывание (один уничтоженный источник). */
    public static final int PUMP_WATER_PER_SOURCE = 1_000;

    /** Таймаут между операциями всасывания воды, тиков (в диапазоне 3–10). */
    public static final int PUMP_SUCK_INTERVAL = 5;

    /** Макс. скорость слива воды помпой соседям за тик, mB. */
    public static final int PUMP_WATER_OUTPUT = 154;

    /** Стоимость воды (mB) на наполнение одного ведра из шкалы. */
    public static final int PUMP_BUCKET_WATER_COST = 1_000;

    // ═══════════════════════════ ГЕНЕРАТОР БУЛЫЖНИКА ═══════════════════════════
    // Механизм паровой эры: при наличии ведра лавы (требование-модификатор, не
    // тратится) единовременно тратит 1000 mB воды → «виртуальный булыжник», затем
    // шкала копания растёт, стабильно тратя GTU (0.8 GTU/t); по готовности кладёт
    // 1 булыжник в слот выдачи (только забор; воду подавать только трубами).

    /** Максимум воды в генераторе, mB. */
    public static final int COBBLE_WATER_CAPACITY = 6_000;
    /** Максимум GTU в генераторе (mGTU: 140 GTU). */
    public static final int COBBLE_GTU_CAPACITY = 140 * MILLI;
    /** Макс. приём GTU за тик (mGTU: 64 GTU/t, из провода). */
    public static final int COBBLE_GTU_INTAKE = 64 * MILLI;
    /** Макс. приём воды за тик (mB, из трубы). */
    public static final int COBBLE_WATER_INTAKE = 1_000;

    /** Вода на один «виртуальный булыжник» (единовременно при старте копания), mB. */
    public static final int COBBLE_WATER_PER_ROCK = 1_000;
    /** Стабильный расход энергии на копание, mGTU за тик (800 mGTU = 0.8 GTU/t). */
    public static final int COBBLE_GTU_MILLI_PER_TICK = 800;

    // Скорость вскопки (тиков на 1 булыжник) по кирке в слоте-модификаторе.
    public static final int COBBLE_TICKS_NONE = 220;
    public static final int COBBLE_TICKS_WOOD = 160;
    public static final int COBBLE_TICKS_STONE = 140;
    public static final int COBBLE_TICKS_IRON = 110;
    public static final int COBBLE_TICKS_GOLD = 100;
    public static final int COBBLE_TICKS_DIAMOND = 70;
    public static final int COBBLE_TICKS_NETHERITE = 50;

    // Шансы подмены РЕЗУЛЬТАТА при выдаче (в промилле-долях: доля 0..1 * 100000).
    // Проверяются по порядку; первый сработавший заменяет булыжник.
    public static final double COBBLE_CHANCE_COAL_ORE = 0.05;      // 5% угольная руда
    public static final double COBBLE_CHANCE_IRON_ORE = 0.01;      // 1% железная руда
    public static final double COBBLE_CHANCE_OBSIDIAN = 0.001;     // 0.1% обсидиан
    public static final double COBBLE_CHANCE_GONZO_STONE_ORE = 0.0003; // 0.03% каменная руда gonzotech

    // Глобальные шансы при СОЗДАНИИ виртуального булыжника (единожды, не на тик).
    public static final double COBBLE_CHANCE_LAVA_TO_OBSIDIAN = 0.0016; // 0.16% ведро лавы → ведро обсидиана
    public static final double COBBLE_CHANCE_PICKAXE_BREAK = 0.0009;    // 0.09% кирка ломается (пропадает)

    // ═══════════════════════════ ВОДА (провайдеры-вёдра) ═══════════════════════════

    /** Обычное ведро воды → mB. */
    public static final int WATER_PER_BUCKET = 1_000;
    /** Ведро с рыхлым снегом → mB. */
    public static final int WATER_PER_POWDER_SNOW = 500;
    /** Ведро с рыбой → mB (и спавнит рыбу-сущность). */
    public static final int WATER_PER_FISH_BUCKET = 1_000;
}
