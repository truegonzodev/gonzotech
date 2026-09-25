package com.gonzotech.radiation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * «Правильные связи» материалов с радиацией (автор 21.09): сколько дозы
 * ПРОХОДИТ сквозь материал. Используется в двух местах:
 * <ul>
 *   <li><b>предметы</b> — с какой скоростью стак накапливает наведённый фон
 *       (свинцовый слиток фармит ×0.02 от обычного — он не «иммунен», автор
 *       специально поправил: «не 100% защищён, а 98%»);</li>
 *   <li><b>блоки стен</b> — экранируют источник в контуре (см. {@code Containment}).</li>
 * </ul>
 *
 * <p>Таблица автора (доля проходящей дозы):</p>
 * <pre>
 *   вольфрам 0.003 · ВР-20 0.01 · осмий/иридий 0.05
 *   свинец 0.02 · барий 0.02 · бор 0.13
 *   бариевый бетон: предмет 0.03 · стена контура 0.01
 *   борное стекло:  предмет 0.08 · стена контура 0.05
 *   лёгкие металлы (литий, магний, цинк) 0.2
 *   средние металлы 0.5 · остальные металлы и сплавы 0.3 · неметаллы 1.0
 * </pre>
 * Матчинг по id-пути: «всё что связано с X» = префикс {@code "X"} или
 * {@code "X_..."} (lead_ingot, lead_block, lead_glass, boron_concrete и т.п.).
 * Готовые конструкционные экраны (бетон/стекло) задаются точечно: у блока и у
 * его предмета-блокитима разные значения (автор 21.09).
 */
public final class RadMaterials {

    /** Точные факторы по префиксу пути (проверка идёт от самого длинного префикса). */
    private static final Map<String, Double> FACTORS = Map.ofEntries(
            Map.entry("tungsten", 0.003),
            Map.entry("vr_20", 0.01),
            Map.entry("vr20", 0.01),
            Map.entry("lead", 0.02),
            Map.entry("barium", 0.02),
            Map.entry("osmium", 0.05),
            Map.entry("iridium", 0.05),
            Map.entry("boron", 0.13),
            Map.entry("lithium", 0.2),
            Map.entry("magnesium", 0.2),
            Map.entry("zinc", 0.2),
            // средние по весу — 0.5
            Map.entry("iron", 0.5),
            Map.entry("copper", 0.5),
            Map.entry("tin", 0.5),
            Map.entry("nickel", 0.5),
            Map.entry("cobalt", 0.5),
            Map.entry("chromium", 0.5),
            Map.entry("manganese", 0.5)
    );
    /** Префиксы в порядке убывания длины, чтобы «vr_20» не съедался чужими префиксами. */
    private static final List<String> PREFIXES = FACTORS.keySet().stream()
            .sorted((a, b) -> b.length() - a.length()).toList();

    /**
     * Точечные факторы ПРЕДМЕТА (наведённый фон стака) для готовых
     * конструкционных экранов: id-путь → доля прохождения. Проверяются раньше
     * префиксной таблицы, иначе «barium_concrete» поймал бы общий префикс «barium».
     */
    private static final Map<String, Double> ITEM_EXACT = Map.of(
            "barium_concrete", 0.03,
            "bore_stained_glass", 0.08,
            // Двери (автор 22.09): отличаются ОДНИМ параметром — защитой от радиации.
            // Свинцовая — свинец 0.02, вольфрамовая — вольфрам 0.003,
            // гермодверь — 0.33 («защита от фона ×0.33», зато для чистого контура).
            "third_heavy_door_lead", 0.02,
            "third_heavy_door_tungsten", 0.003,
            "third_hermetic_door", 0.33
    );

    /**
     * Точечные факторы БЛОКА-стены (экранирование контура в чанке): те же
     * материалы как строительные блоки гасят дозу сильнее, чем в инвентаре.
     */
    private static final Map<String, Double> BLOCK_EXACT = Map.of(
            "barium_concrete", 0.01,
            "bore_stained_glass", 0.05,
            // Двери как стены контура: тот же параметр, что и у предмета (автор 22.09).
            "third_heavy_door_lead", 0.02,
            "third_heavy_door_tungsten", 0.003,
            "third_hermetic_door", 0.33
    );

    /** Узнаваемые «прочие металлы и сплавы» (×0.3) — fallback для форм без точной строки. */
    private static final List<String> GENERIC_METALS = List.of(
            "gold", "silver", "platinum", "titanium", "aluminum", "aluminium",
            "steel", "bronze", "brass", "invar", "electrum", "constantan",
            "vanadium", "mercury", "cadmium", "bismuth", "antimony",
            "uranium", "thorium", "radium", "plutonium");

    private RadMaterials() {
    }

    /**
     * Фактор прохождения для ПРЕДМЕТА (наведённый фон накапливается ×factor).
     * Неметаллы (дерево, факелы, камень) — 1.0 (без экрана).
     */
    public static double itemFactor(ItemStack stack) {
        if (stack.isEmpty()) {
            return 1.0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        // Material shielding belongs to Gonzo Tech materials. Vanilla items
        // such as minecraft:lead (the lead) and minecraft:iron_bars are not
        // radiation shields just because their ids contain a metal word.
        if (!id.getNamespace().equals("gonzotech")) {
            return 1.0;
        }
        String path = id.getPath();
        // Ores and finished equipment are not shielding material. Do not let
        // players build a bunker from ore blocks or iron tools/armor.
        if (path.contains("ore") || isFinishedEquipment(path)) {
            return 1.0;
        }
        return factorForPath(path, true);
    }

    /**
     * Фактор прохождения для БЛОКА-стены (экранирование контура).
     * Обычный камень/земля/дерево — 1.0: полный блок герметизирует полость
     * геометрически, но дозу не гасит (защита только экранирующими металлами).
     */
    public static double blockFactor(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("gonzotech") || id.getPath().contains("ore")) {
            return 1.0;
        }
        return factorForPath(id.getPath(), false);
    }

    private static double factorForPath(String path, boolean item) {
        Double exact = (item ? ITEM_EXACT : BLOCK_EXACT).get(path);
        if (exact != null) {
            return exact;
        }
        for (String p : PREFIXES) {
            if (path.equals(p) || path.startsWith(p + "_")) {
                return FACTORS.get(p);
            }
        }
        if (item) {
            // «остальные металлы и сплавы ×0.3»: любой *_ingot/*_nugget,
            // плюс *_dust с металлическим префиксом (чтобы glowstone_dust не стал «металлом»).
            if (path.endsWith("_ingot") || path.endsWith("_nugget")) {
                return 0.3;
            }
            if (path.endsWith("_dust") && hasMetalPrefix(path)) {
                return 0.3;
            }
            if (path.endsWith("_block") && hasMetalPrefix(path)) {
                return 0.3;
            }
            return 1.0;
        }
        // блоки: только явные металлы-формы, остальное — «воздух для гаммы»
        return (path.endsWith("_block") || path.endsWith("_ingot_form")) && hasMetalPrefix(path) ? 0.3 : 1.0;
    }

    private static boolean isFinishedEquipment(String path) {
        return path.endsWith("_pickaxe") || path.endsWith("_axe") || path.endsWith("_shovel")
                || path.endsWith("_hoe") || path.endsWith("_sword")
                || path.endsWith("_helmet") || path.endsWith("_chestplate")
                || path.endsWith("_leggings") || path.endsWith("_boots")
                || path.endsWith("_horse_armor") || path.endsWith("_armor")
                || path.endsWith("_tools") || path.endsWith("_bars");
    }

    private static boolean hasMetalPrefix(String path) {
        for (String m : GENERIC_METALS) {
            if (path.equals(m) || path.startsWith(m + "_")) {
                return true;
            }
        }
        return false;
    }
}
