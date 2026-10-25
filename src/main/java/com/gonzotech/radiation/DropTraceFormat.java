package com.gonzotech.radiation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * 0.3.291: однострочный слепок стака для дебаггера дюпа — id, количество и
 * хэш дата-компонентов. Хэш — быстрый маркер «компоненты те же / изменились»
 * (полный NBT в лог не тащим: наведёнка gonzo_rad тоже попадает в хэш).
 */
public final class DropTraceFormat {
    private DropTraceFormat() {}

    public static String fmt(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "∅";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "×" + stack.getCount()
            + "#" + Integer.toHexString(componentsHash(stack));
    }

    public static int componentsHash(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var components = stack.getComponents();
        return components == null ? 0 : components.hashCode();
    }

    /** Совместимость слияния — ровно та же проверка, что делает ванильный перенос. */
    public static boolean compat(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }
}
