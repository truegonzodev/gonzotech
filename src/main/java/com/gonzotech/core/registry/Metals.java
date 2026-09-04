package com.gonzotech.core.registry;

import java.util.List;

/**
 * Нейтральный (без зависимостей) реестр «металлов» мода — единый источник правды
 * для списка слитков и производных предметов/блоков (пыль, самородки, блоки-хранилища).
 * <p>
 * Вынесено отдельно, чтобы избежать циклической статической инициализации между
 * {@link ModItems} и {@link ModBlocks} (оба читают этот список, но он сам ничего
 * из них не читает).
 * <p>
 * Правила Фазы 3:
 * <ul>
 *   <li>Блок-хранилище ({@code <metal>_block}) — у КАЖДОГО слитка.</li>
 *   <li>Пыль ({@code <metal>_dust}) — у всех, КРОМЕ iodine/manganese/sulfur/mercury.</li>
 *   <li>Самородок ({@code <metal>_nugget}) — у всех, КРОМЕ iodine/manganese/sulfur.</li>
 * </ul>
 */
public final class Metals {

    /** Полный список id слитков (тот же порядок, что в креатив-вкладке). */
    public static final List<String> INGOT_IDS = List.of(
        // 26 Ore Metal Ingots
        "calcium_ingot", "aluminum_ingot", "magnesium_ingot", "sulfur_ingot",
        "manganese_ingot", "titanium_ingot", "barium_ingot", "zinc_ingot",
        "tin_ingot", "boron_ingot", "chromium_ingot", "nickel_ingot",
        "cobalt_ingot", "silver_ingot", "iodine_ingot", "tungsten_ingot",
        "mercury_ingot", "uranium_ingot", "zirconium_ingot", "thorium_ingot",
        "platinum_ingot", "tellurium_ingot", "palladium_ingot", "cesium_ingot",
        "iridium_ingot", "osmium_ingot",

        // 21 Alloy & Extra Ingots
        "steel_ingot", "stainless_steel_ingot", "corten_steel_ingot", "cast_iron_ingot",
        "plutonium_ingot", "nitinol_ingot", "invar_ingot", "lead_ingot",
        "neodymium_ingot", "ferromagnetic_ingot", "cantor_ingot", "vitreloy_ingot",
        "semiconductor_ingot", "vr20_ingot", "stellite_ingot", "alnico_ingot",
        "telluride_ingot", "bismuth_ingot", "rhenium_ingot", "radium_ingot",
        "lithium_ingot"
    );

    /**
     * Металлы БЕЗ пыли (у них есть слиток, но пыль не делаем). Йод, марганец,
     * сера, ртуть — по требованию заказчика.
     */
    private static final List<String> NO_DUST = List.of(
        "iodine", "manganese", "sulfur", "mercury"
    );

    /**
     * Металлы БЕЗ самородка. Йод, марганец, сера — без самородка; ртуть самородок
     * ИМЕЕТ (для неё только самородок, но не пыль).
     */
    private static final List<String> NO_NUGGET = List.of(
        "iodine", "manganese", "sulfur"
    );

    /** Базовое имя металла: {@code tellurium_ingot} -> {@code tellurium}. */
    public static String base(String ingotId) {
        return ingotId.endsWith("_ingot") ? ingotId.substring(0, ingotId.length() - "_ingot".length()) : ingotId;
    }

    public static boolean hasDust(String ingotId) {
        return !NO_DUST.contains(base(ingotId));
    }

    public static boolean hasNugget(String ingotId) {
        return !NO_NUGGET.contains(base(ingotId));
    }

    private Metals() {
    }
}
