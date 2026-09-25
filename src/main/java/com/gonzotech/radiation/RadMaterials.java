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
 *   вольфрам 0.003 · ВР-20 0.01 · осмий/иридий 0.24
 *   свинец 0.02 · барий 0.10 · бор 0.13
 *   бариевый бетон: предмет/стена 0.10
 *   борное стекло:  предмет/стена 0.008
 *   железо 0.90 · золото 0.50
 *   лёгкие металлы 0.68 · средние металлы 0.80 · остальные 0.72 · неметаллы 1.0
 * </pre>
 * Матчинг по id-пути: «всё что связано с X» = префикс {@code "X"} или
 * {@code "X_..."} (lead_ingot, lead_block, lead_glass, boron_concrete и т.п.).
 * Готовые конструкционные экраны (бетон/стекло) задаются точечно: у блока и у
 * его предмета-блокитима разные значения (автор 21.09).
 */
public final class RadMaterials {

    /** Точные факторы по префиксу пути (проверка идёт от самого длинного префикса). */
    private static final Map<String, Double> FACTORS = Map.ofEntries(
            Map.entry("tungsten", 0.003),       // 99.7% shielding
            Map.entry("vr_20", 0.01),            // 99% shielding
            Map.entry("vr20", 0.01),             // 99% shielding
            Map.entry("lead", 0.02),             // 98% shielding
            Map.entry("barium", 0.10),           // 90% shielding
            Map.entry("osmium", 0.24),           // 76% shielding (20% reduction from 95%)
            Map.entry("iridium", 0.24),          // 76% shielding (20% reduction from 95%)
            Map.entry("boron", 0.13),            // 87% shielding; boron glass is exact below
            Map.entry("gold", 0.50),             // 50% shielding
            Map.entry("golden", 0.50),           // 50% shielding
            Map.entry("iron", 0.90),             // 10% shielding
            // Other former 50% metals: 50% × (1 - 60%) = 20% shielding.
            Map.entry("copper", 0.80),
            Map.entry("tin", 0.80),
            Map.entry("nickel", 0.80),
            Map.entry("cobalt", 0.80),
            Map.entry("chromium", 0.80),
            Map.entry("manganese", 0.80),
            // Other former 80% light metals: 80% × (1 - 60%) = 32% shielding.
            Map.entry("lithium", 0.68),
            Map.entry("magnesium", 0.68),
            Map.entry("zinc", 0.68)
    );
    /** Префиксы в порядке убывания длины, чтобы «vr_20» не съедался чужими префиксами. */
    private static final List<String> PREFIXES = FACTORS.keySet().stream()
            .sorted((a, b) -> b.length() - a.length()).toList();

    /**
     * Точечные факторы ПРЕДМЕТА (наведённый фон стака) для готовых
     * конструкционных экранов: id-путь → доля прохождения. Проверяются раньше
     * префиксной таблицы, иначе «barium_concrete» поймал бы общий префикс «barium».
     */
    private static final Map<String, Double> ITEM_EXACT = Map.ofEntries(
            Map.entry("barium_concrete", 0.10),
            Map.entry("bore_stained_glass", 0.008),
            Map.entry("copper_dust", 1.0),
            Map.entry("copper_nugget", 1.0),
            Map.entry("copper_plate", 1.0),
            Map.entry("copper_wire", 1.0),
            Map.entry("aluminum_plate", 0.68),
            Map.entry("aluminum_wire", 0.68),
            Map.entry("steel_plate", 0.72),
            Map.entry("stainless_steel_plate", 0.72),
            Map.entry("silver_wire", 0.72),
            Map.entry("titanium_plate", 0.72),
            Map.entry("zirconium_plate", 0.72),
            Map.entry("semiconductor_plate", 0.72),
            Map.entry("semiconductor_core", 0.72),
            // Двери (автор 22.09): отличаются ОДНИМ параметром — защитой от радиации.
            // Свинцовая — свинец 0.02, вольфрамовая — вольфрам 0.003,
            // гермодверь — 0.33 («защита от фона ×0.33», зато для чистого контура).
            Map.entry("third_heavy_door_lead", 0.02),
            Map.entry("third_heavy_door_tungsten", 0.003),
            Map.entry("third_hermetic_door", 0.33)
    );

    /**
     * Точечные факторы БЛОКА-стены (экранирование контура в чанке): те же
     * материалы как строительные блоки гасят дозу сильнее, чем в инвентаре.
     */
    private static final Map<String, Double> BLOCK_EXACT = Map.of(
            "barium_concrete", 0.10,
            "bore_stained_glass", 0.008,
            // Двери как стены контура: тот же параметр, что и у предмета (автор 22.09).
            "third_heavy_door_lead", 0.02,
            "third_heavy_door_tungsten", 0.003,
            "third_hermetic_door", 0.33
    );

    /** Узнаваемые «прочие металлы и сплавы» (×0.72) — fallback после 60% reduction. */
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
        String path = id.getPath();
        if (!id.getNamespace().equals("gonzotech")) {
            return vanillaItemFactor(id.getNamespace(), path);
        }
        // Ores and finished equipment are not shielding material. Do not let
        // players build a bunker from ore blocks or iron tools/armor.
        if (isOrePath(path) || isFinishedEquipment(path)) {
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
        if (!id.getNamespace().equals("gonzotech")) {
            return vanillaBlockFactor(id.getNamespace(), id.getPath());
        }
        if (isOrePath(id.getPath())) {
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
            // «остальные металлы и сплавы ×0.72»: любой *_ingot/*_nugget,
            // плюс *_dust с металлическим префиксом (чтобы glowstone_dust не стал «металлом»).
            if (path.endsWith("_ingot") || path.endsWith("_nugget")) {
                return 0.72;
            }
            if (path.endsWith("_dust") && hasMetalPrefix(path)) {
                return 0.72;
            }
            if (path.endsWith("_block") && hasMetalPrefix(path)) {
                return 0.72;
            }
            return 1.0;
        }
        // блоки: только явные металлы-формы, остальное — «воздух для гаммы»
        return (path.endsWith("_block") || path.endsWith("_ingot_form")) && hasMetalPrefix(path) ? 0.72 : 1.0;
    }

    private static boolean isOrePath(String path) {
        return path.equals("ore") || path.startsWith("ore_")
                || path.endsWith("_ore") || path.contains("_ore_");
    }

    private static double vanillaItemFactor(String namespace, String path) {
        if (!namespace.equals("minecraft")) return 1.0;
        return switch (path) {
            case "iron_ingot", "iron_nugget", "iron_door", "iron_trapdoor", "iron_block" -> 0.90;
            case "gold_ingot", "gold_nugget", "gold_block" -> 0.50;
            default -> 1.0;
        };
    }

    private static double vanillaBlockFactor(String namespace, String path) {
        if (!namespace.equals("minecraft")) return 1.0;
        return switch (path) {
            case "iron_door", "iron_trapdoor", "iron_block" -> 0.90;
            case "gold_block" -> 0.50;
            default -> 1.0;
        };
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
