package com.gonzotech.radiation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Пресетная («природная») радиоактивность предметов мода (автор 20.09, п.2):
 * уран, радий, плутоний фонят САМИ ПО СЕБЕ, без всякого NBT:
 * <ul>
 *   <li>слиток — база пресета;</li>
 *   <li>самородок = слиток / 9;</li>
 *   <li>металл-блок = слиток × 9;</li>
 *   <li>пыль = слиток × 2.</li>
 * </ul>
 * Значения — эмиссия в nZt/с за ОДИН предмет (стак считается умножением, п.3:
 * «6 слитков по слотам = стопке из 6»).
 *
 * <p>Ядерная перепрошивка автора 21.09: природные актиниды сильно ослаблены,
 * уран стал «Природным ураном», плутоний — «Реакторным плутонием-239», а
 * горячая таблица переехала на изотопы/топлива ({@link #DIRECT_EMISSION}):</p>
 * <pre>
 *   природный уран 0.02mZt · торий 0.007mZt · реакторный плутоний-239 3.6mZt · радий 75mZt
 *   изотопы: U-238 0.01 · U-235 0.10 · U-233 1.50 · оружейный Pu 1.20 ·
 *            Pu-238 12.00 · Pu-242 0.30 · Th-229 0.80   (mZt)
 *   топлива: урановое 0.03 · СО (MOX) 0.45 · ТСО 0.35 · СНУП 0.85   (mZt)
 * </pre>
 */
public final class RadSources {

    /** База эмиссии слитка, nZt/с. Ключ — короткое имя металла из id предмета. */
    private static final Map<String, Double> INGOT_BASE = Map.of(
            "uranium", 0.02 * RadUnits.MILLI,     // Природный уран (автор 21.09)
            "thorium", 0.007 * RadUnits.MILLI,    // торий ослаблен (автор 21.09)
            "plutonium", 3.6 * RadUnits.MILLI,    // Реакторный плутоний-239 (автор 21.09)
            "radium", 75.0 * RadUnits.MILLI       // радий — самая горячая природная база
    );

    /**
     * Точечная эмиссия «компонентов» по ПОЛНОМУ id-пути (изотопы и топливные
     * смеси — не подходят под схему {@code <металл>_<форма>}). Эмиссия за ОДИН
     * предмет; стак — умножением (п.3). Проверяется ДО форм-парсинга.
     */
    private static final Map<String, Double> DIRECT_EMISSION = Map.ofEntries(
            Map.entry("uranium_238", 0.01 * RadUnits.MILLI),
            Map.entry("uranium_235", 0.10 * RadUnits.MILLI),
            Map.entry("uranium_233", 1.50 * RadUnits.MILLI),
            Map.entry("weapons_plutonium", 1.20 * RadUnits.MILLI),
            Map.entry("plutonium_238", 12.0 * RadUnits.MILLI),
            Map.entry("plutonium_242", 0.30 * RadUnits.MILLI),
            Map.entry("thorium_229", 0.80 * RadUnits.MILLI),
            Map.entry("uranium_fuel", 0.03 * RadUnits.MILLI),
            Map.entry("mox_fuel", 0.45 * RadUnits.MILLI),   // СО — смесь оксидов
            Map.entry("tmox_fuel", 0.35 * RadUnits.MILLI),  // ТСО — ториевая смесь оксидов
            Map.entry("snup_fuel", 0.85 * RadUnits.MILLI)
    );

    private RadSources() {
    }

    /** Эмиссия ОДНОГО предмета (nZt/с) по пресетам; 0 — предмет не радиоактивен сам по себе. */
    public static double emissionPerItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!id.getNamespace().equals("gonzotech")) {
            return 0.0;
        }
        return emissionByPath(id.getPath());
    }

    /** То же по имени id (используется и для БЛОКОВ — формы те же: {@code <metal>_block}). */
    private static double emissionByPath(String path) {
        Double direct = DIRECT_EMISSION.get(path); // изотопы/топлива — точечно (автор 21.09)
        if (direct != null) {
            return direct;
        }
        int sep = path.indexOf('_');
        if (sep <= 0) {
            return 0.0;
        }
        Double base = INGOT_BASE.get(path.substring(0, sep));
        if (base == null) {
            return 0.0;
        }
        String form = path.substring(sep + 1);
        return switch (form) {
            case "ingot" -> base;
            case "nugget" -> base / 9.0;
            case "dust" -> base * 2.0;
            case "block" -> base * 9.0;
            default -> 0.0;
        };
    }

    /** Эмиссия целого блока (посаженный в мир {@code uranium_block} и т.п.). */
    public static double blockEmission(String blockPath) {
        return emissionByPath(blockPath);
    }

    /** Суммарная пресетная эмиссия стака: per-item × count (автор п.3). */
    public static double emissionOfStack(ItemStack stack) {
        double per = emissionPerItem(stack);
        return per <= 0.0 ? 0.0 : per * stack.getCount();
    }
}
