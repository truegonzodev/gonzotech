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
 *   лёгкие металлы (литий, магний, цинк) 0.2
 *   средние металлы 0.5 · остальные металлы и сплавы 0.3 · неметаллы 1.0
 * </pre>
 * Матчинг по id-пути: «всё что связано с X» = префикс {@code "X"} или
 * {@code "X_..."} (lead_ingot, lead_block, lead_glass, boron_concrete и т.п.).
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
        return factorForPath(id.getPath(), true);
    }

    /**
     * Фактор прохождения для БЛОКА-стены (экранирование контура).
     * Обычный камень/земля/дерево — 1.0: полный блок герметизирует полость
     * геометрически, но дозу не гасит (защита только экранирующими металлами).
     */
    public static double blockFactor(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return factorForPath(id.getPath(), false);
    }

    private static double factorForPath(String path, boolean item) {
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

    private static boolean hasMetalPrefix(String path) {
        for (String m : GENERIC_METALS) {
            if (path.equals(m) || path.startsWith(m + "_")) {
                return true;
            }
        }
        return false;
    }
}
