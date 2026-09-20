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
 * «6 слитков по слотам = стопке из 6»). Торий осознанно НЕ фонит — в спеке его
 * нет, добавить при желании = одна строка в {@link #INGOT_BASE}.
 */
public final class RadSources {

    /** База эмиссии слитка, nZt/с. Ключ — короткое имя металла из id предмета. */
    private static final Map<String, Double> INGOT_BASE = Map.of(
            "uranium", 30.0 * RadUnits.MILLI,     // урановый слиток = «30mZt/t» из спеки
            "radium", 80.0 * RadUnits.MILLI,      // радий активнее урана
            "plutonium", 250.0 * RadUnits.MILLI   // плутоний — самая горячая база
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
