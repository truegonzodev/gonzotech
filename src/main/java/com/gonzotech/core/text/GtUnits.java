package com.gonzotech.core.text;

import com.gonzotech.radiation.ItemToxicity;
import com.gonzotech.radiation.RadUnits;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * «ГОСТ единиц» (автор 22.09.2026) — единый вид величины в любом тексте мода:
 * <b>обозначение единицы всегда своего цвета</b>, число — тем же цветом, суффикс
 * времени ({@code /t}, {@code /s}) — цветом ОСНОВНОГО текста строки (в тултипах
 * это {@code &amp;7}, на HUD — {@code &amp;f}), поэтому он не красится, а наследует
 * стиль родителя.
 *
 * <pre>
 *   &lt;цвет&gt;[число] [единица]&lt;&amp;7 или &amp;f&gt;/t
 *
 *   HUD ключа ......... §6[12.3 GTU]§f/t          («течёт 12.3 GTU за тик»)
 *   Пара в станке ..... §6[GTU]§7: §6[12]§7 / §6[100]
 *   Пара с хвостом .... §b[Вода]§7: §b[12]§7 / §b[100 mB]
 *   Радиация .......... #ceeb2d[30 mZt]§7/t       (всегда #ceeb2d)
 *   Токсичность ....... #d12176[2 mTx]§7/t        (всегда #d12176)
 * </pre>
 *
 * <p><b>Цвета — из подсказки гаечного ключа</b> ({@link com.gonzotech.machines.network.PipeType#color()}):
 * GTU {@code #FFD84A}, GTH {@code #FF6A4A}, вода {@code #4AA3FF}, пар {@code #D8D8D8},
 * предметы {@code #C08A4A}. Там же, где красится число, красится и обозначение —
 * иначе строка «рассыпается». Шкалы (заливка и подпись после 10 %) не затрагиваются:
 * у них своя палитра, это решено автором отдельно.</p>
 *
 * <p>Хвост времени живёт в lang ({@code unit.gonzotech.per_tick} = «/t» / «/т»),
 * потому что в русской локализации это «/т», а в английской — «/t».</p>
 */
public final class GtUnits {

    private GtUnits() {
    }

    // ───────────────────────── Цвета (единый источник) ─────────────────────────

    /** Провод: GTU. */
    public static final int GTU = 0xFFD84A;
    /** Теплотруба: GTH. */
    public static final int GTH = 0xFF6A4A;
    /** Водная труба: вода (mB). */
    public static final int WATER = 0x4AA3FF;
    /** Паровая труба: пар (mB). */
    public static final int STEAM = 0xD8D8D8;
    /** Предметная труба: предметы. */
    public static final int ITEM = 0xC08A4A;
    /** Радиация: всегда #ceeb2d (автор 22.09.2026). */
    public static final int RADIATION = 0xCEEB2D;
    /** Токсичность: всегда #d12176 (автор 22.09.2026). */
    public static final int TOXICITY = 0xD12176;

    // ─────────────────── Lang-ключи: обозначения и имена ───────────────────

    /** Обозначение единицы: «GTU». */
    public static final String U_GTU = "resource.gonzotech.first_wire.unit";
    /** Обозначение единицы: «GTH». */
    public static final String U_GTH = "resource.gonzotech.first_heat_pipe.unit";
    /** Обозначение единицы: «mB» (и для воды, и для пара). */
    public static final String U_MB = "resource.gonzotech.first_water_pipe.unit";
    /** Обозначение единицы: «items» (в обеих локализациях — дословно, как раньше). */
    public static final String U_ITEMS = "resource.gonzotech.first_item_pipe.unit";

    /** Имя ресурса: «Вода». */
    public static final String N_WATER = "resource.gonzotech.first_water_pipe";
    /** Имя ресурса: «Пар». */
    public static final String N_STEAM = "resource.gonzotech.first_steam_pipe";
    /** Имя ресурса: «Кипяток» (горячая вода центрифуги). */
    public static final String N_HOT_WATER = "resource.gonzotech.hot_water";

    /** Хвост времени: «/t» («/т»). */
    public static final String K_PER_TICK = "unit.gonzotech.per_tick";
    /** Хвост времени: «/s» («/с»). */
    public static final String K_PER_SECOND = "unit.gonzotech.per_second";

    // ─────────────────────────── Кирпичики строки ───────────────────────────

    /** Число цветом единицы: {@code §6[12]}. */
    public static MutableComponent num(Object value, int color) {
        return Component.literal(String.valueOf(value)).withColor(color);
    }

    /** Обозначение по lang-ключу, цветом единицы: {@code §6[GTU]}. */
    public static MutableComponent key(String unitKey, int color) {
        return Component.translatable(unitKey).withColor(color);
    }

    /** Обозначение дословно (напр. «mZt»), цветом единицы. */
    public static MutableComponent text(String unitText, int color) {
        return Component.literal(unitText).withColor(color);
    }

    /** «&lt;цвет&gt;[число] [обозначение]» — без времени. */
    public static MutableComponent amount(Object value, Component unit, int color) {
        return Component.empty().append(num(value, color)).append(" ").append(unit);
    }

    /** ГОСТ-поток: «&lt;цвет&gt;[число] [обозначение]&lt;база&gt;/t». */
    public static MutableComponent rate(Object value, Component unit, int color) {
        return Component.empty().append(amount(value, unit, color)).append(perTick());
    }

    /** ГОСТ-поток в секунду: «&lt;цвет&gt;[число] [обозначение]&lt;база&gt;/s». */
    public static MutableComponent ratePerSecond(Object value, Component unit, int color) {
        return Component.empty().append(amount(value, unit, color)).append(perSecond());
    }

    /** «&lt;цвет&gt;[число]&lt;база&gt;/t» — когда обозначение стоит в подписи строки. */
    public static MutableComponent ticked(Object value, int color) {
        return Component.empty().append(num(value, color)).append(perTick());
    }

    /** Хвост «/t» — ЦВЕТ НЕ ЗАДАН, наследует основной текст строки. */
    public static MutableComponent perTick() {
        return Component.translatable(K_PER_TICK);
    }

    /** Хвост «/s» — ЦВЕТ НЕ ЗАДАН, наследует основной текст строки. */
    public static MutableComponent perSecond() {
        return Component.translatable(K_PER_SECOND);
    }

    // ─────────────────── Пары «значение / ёмкость» (станки) ───────────────────

    /** «§6GTU§7: §612§7 / §6100». */
    public static MutableComponent gtuPair(Object value, Object capacity) {
        return Component.translatable("gui.gonzotech.gtu", gtu(), num(value, GTU), num(capacity, GTU));
    }

    /** «§6GTH§7: §612§7 / §6100». */
    public static MutableComponent gthPair(Object value, Object capacity) {
        return Component.translatable("gui.gonzotech.gth", gth(), num(value, GTH), num(capacity, GTH));
    }

    /** «§bВода§7: §b12§7 / §b100 mB». */
    public static MutableComponent waterPair(Object value, Object capacity) {
        return Component.translatable("gui.gonzotech.water",
                key(N_WATER, WATER), num(value, WATER), num(capacity, WATER), mb(WATER));
    }

    /** «§7Пар§7: §7 12§7 / §7100 mB» — цвет пара #D8D8D8. */
    public static MutableComponent steamPair(Object value, Object capacity) {
        return Component.translatable("gui.gonzotech.steam",
                key(N_STEAM, STEAM), num(value, STEAM), num(capacity, STEAM), mb(STEAM));
    }

    /** «§bКипяток§7: §b12§7 / §b100 mB» (горячая вода центрифуги). */
    public static MutableComponent hotWaterPair(Object value, Object capacity) {
        return Component.translatable("gui.gonzotech.hot_water",
                key(N_HOT_WATER, WATER), num(value, WATER), num(capacity, WATER), mb(WATER));
    }

    /** «§7Пар турбины§7: …» — паровая шкала турбины. */
    public static MutableComponent turbineSteamPair(Object value, Object capacity) {
        return Component.translatable("gui.gonzotech.turbine.steam",
                key(N_STEAM, STEAM), num(value, STEAM), num(capacity, STEAM), mb(STEAM));
    }

    /** «§bКонденсат§7: §b50§7% (§b12§7 / §b100 mB)» — шкала конденсата Стирлинга. */
    public static MutableComponent condensatePair(Object percent, Object value, Object capacity) {
        return Component.translatable("gui.gonzotech.condensate",
                num(percent, WATER), num(value, WATER), num(capacity, WATER), mb(WATER));
    }

    /** «§bБрага§7: §b12§7 / §b100 mB (§c12.3%§7 спирта, §a0.0%§7 гнили)». */
    public static MutableComponent mashPair(Object value, Object capacity, Object alcPercent, Object rotPercent) {
        return Component.translatable("gui.gonzotech.mash",
                num(value, WATER), num(capacity, WATER), mb(WATER),
                num(alcPercent, GTH), num(rotPercent, 0x8A9A5B));
    }

    /** «§bСусло§7: §b12§7 / §b100 mB (§c12.3%§7 спирта)». */
    public static MutableComponent wortPair(Object value, Object capacity, Object alcPercent) {
        return Component.translatable("gui.gonzotech.wort",
                num(value, WATER), num(capacity, WATER), mb(WATER),
                num(alcPercent, GTH));
    }

    /** Обозначение единицы объёма (mB) цветом ресурса. */
    private static MutableComponent mb(int color) {
        return key(U_MB, color);
    }

    /** Обозначение GTU (для строк, где подпись строит вызывающий). */
    public static MutableComponent gtu() {
        return key(U_GTU, GTU);
    }

    /** Обозначение GTH (для строк, где подпись строит вызывающий). */
    public static MutableComponent gth() {
        return key(U_GTH, GTH);
    }

    // ──────────── Готовые строки: подпись живёт в lang, ГОСТ — здесь ────────────

    /**
     * «Подпись: §6[12 GTU]§7/t» — подпись из lang, значение собирает ГОСТ.
     * Для строк вида «Номинальная мощность: %s».
     */
    public static MutableComponent rateLine(String langKey, Object value, String unitKey, int color) {
        return Component.translatable(langKey, rate(value, key(unitKey, color), color));
    }

    /**
     * «§6GTH§7 на входе: §6[12]§7/t» — обозначение стоит в ПОДПИСИ, поэтому
     * хвост времени идёт сразу за числом («ticked»).
     */
    public static MutableComponent tickLine(String langKey, Object value, String unitKey, int color) {
        return Component.translatable(langKey, key(unitKey, color), ticked(value, color));
    }

    /** «§bВода§7 на входе: §b[12 mB]§7/t» — имя ресурса + ГОСТ-поток. */
    public static MutableComponent nameRateLine(String langKey, Object value, String nameKey,
                                                String unitKey, int color) {
        return Component.translatable(langKey,
                key(nameKey, color), rate(value, key(unitKey, color), color));
    }

    /** «Тёплый — §6[12 GTH]§7» — величина без времени (склад тепла абсорбера). */
    public static MutableComponent amountLine(String langKey, Object value, String unitKey, int color) {
        return Component.translatable(langKey, amount(value, key(unitKey, color), color));
    }

    /** «Расход пара: §7[12 mB]§7/t» — турбина. */
    public static MutableComponent turbineSteamRate(Object value) {
        return rateLine("gui.gonzotech.turbine.steam_rate", value, U_MB, STEAM);
    }

    /** «Номинальная мощность: §6[12.3 GTU]§7/t» — турбина. */
    public static MutableComponent turbineGtuRate(Object value) {
        return rateLine("gui.gonzotech.turbine.gtu_rate", value, U_GTU, GTU);
    }

    /** «§6GTH§7 на входе: §6[12]§7/t» — паровой генератор. */
    public static MutableComponent steamGenGthIn(Object value) {
        return tickLine("gui.gonzotech.steamgen.gth_in", value, U_GTH, GTH);
    }

    /** «§bВода§7 на входе: §b[12 mB]§7/t» — паровой генератор. */
    public static MutableComponent steamGenWaterIn(Object value) {
        return nameRateLine("gui.gonzotech.steamgen.water_in", value, N_WATER, U_MB, WATER);
    }

    /** «Произведено пара: §7[12 mB]§7/t» — паровой генератор. */
    public static MutableComponent steamGenSteamMade(Object value) {
        return rateLine("gui.gonzotech.steamgen.steam_made", value, U_MB, STEAM);
    }

    /** «Отдано пара: §7[12 mB]§7/t» — паровой генератор. */
    public static MutableComponent steamGenSteamOut(Object value) {
        return rateLine("gui.gonzotech.steamgen.steam_out", value, U_MB, STEAM);
    }

    /** «Номинальная выработка: §7[12 mB]§7/t» — паровой генератор. */
    public static MutableComponent steamGenRated(Object value) {
        return rateLine("gui.gonzotech.steamgen.rated", value, U_MB, STEAM);
    }

    // ───────────────────── Радиация и токсичность (Zt / Tx) ─────────────────────

    /**
     * «#ceeb2d 30 mZt&lt;#7&gt;/t» — единица радиации ВСЕГДА этим цветом, приставка
     * (n/µ/m/k/M/G) приходит из {@link RadUnits} и стоит слитно с «Zt».
     */
    public static MutableComponent zt(double nZt) {
        return rate(RadUnits.value(nZt), text(RadUnits.unit(nZt), RADIATION), RADIATION);
    }

    /**
     * «#ceeb2d 20 mZt&lt;#7&gt;/s» — доза ЗА СЕКУНДУ (не за тик): так меряется мягкая
     * доза хазмата ({@link com.gonzotech.radiation.Hazmat#SOFT_DOSE_MILLI}), автор 22.09.2026
     * в лоре сета написал именно «mZt/s».
     */
    public static MutableComponent ztPerSecond(double nZt) {
        return ratePerSecond(RadUnits.value(nZt), text(RadUnits.unit(nZt), RADIATION), RADIATION);
    }

    /** «#d12176 2 mTx&lt;#7&gt;/t» — единица токсичности ВСЕГДА этим цветом. */
    public static MutableComponent tx(double nTx) {
        return rate(ItemToxicity.value(nTx), text(ItemToxicity.unit(nTx), TOXICITY), TOXICITY);
    }
}
