package com.gonzotech.radiation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Наведённая радиоактивность предмета.
 *
 * <p>ВАЖНО: {@code gonzo_rad} хранит дозу ОДНОГО предмета в стаке, а не дозу
 * стака. Это намеренно: ваниль при разделении стака копирует компоненты, и
 * per-item модель не создаёт радиацию из воздуха. Полная эмиссия стака всегда
 * считается как {@code perItem * count}.</p>
 */
public final class ItemRadioactivity {
    public static final String TAG_RAD = "gonzo_rad";
    /** Версия формата: 1 = значение на один предмет; без версии — старый total-stack формат. */
    private static final String TAG_RAD_MODEL = "gonzo_rad_model";
    private static final int PER_ITEM_MODEL = 1;
    public static final double GROWTH = 1.25;
    public static final double INDUCED_CAP = 5.0 * RadUnits.UNIT;
    public static final double CLEANUP_FLOOR = 1.0;

    private ItemRadioactivity() {
    }

    public static boolean isLeadImmune(ItemStack stack) {
        if (stack.isEmpty()) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath().contains("lead");
    }

    /** Наведённая эмиссия одного предмета внутри стака (nZt/s). */
    public static double getInduced(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0.0;
        CompoundTag tag = data.copyTag();
        double value = Math.max(0.0, tag.getDouble(TAG_RAD));
        // Сейвы до 0.3.17 содержали total-stack. Мигрируем чтением лениво;
        // ближайшая запись setInduced зафиксирует новый формат явно.
        return tag.getInt(TAG_RAD_MODEL) == PER_ITEM_MODEL
                ? value : value / Math.max(1, stack.getCount());
    }

    public static void setInduced(ItemStack stack, double value) {
        if (value < CLEANUP_FLOOR) {
            clear(stack);
            return;
        }
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> {
                    tag.putDouble(TAG_RAD, value);
                    tag.putInt(TAG_RAD_MODEL, PER_ITEM_MODEL);
                }));
    }

    private static void clear(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                d -> d.update(tag -> {
                    tag.remove(TAG_RAD);
                    tag.remove(TAG_RAD_MODEL);
                }));
        CustomData after = stack.get(DataComponents.CUSTOM_DATA);
        if (after != null && after.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
    }

    /** Рассчитать следующий per-item уровень без изменения ItemStack. */
    public static double nextInduced(double current, ItemStack stack,
                                     boolean hasSource, double sourceNzt, double factor) {
        int count = Math.max(1, stack.getCount());
        double target = Math.min(sourceNzt / count, INDUCED_CAP);
        if (hasSource && target >= 1.0 && factor > 0.0) {
            if (current > target) return current / GROWTH;
            double seeded = Math.max(current, 1.0 / count);
            double next = seeded + seeded * (GROWTH - 1.0) * (1.0 - seeded / target) * factor;
            return Math.min(next, target);
        }
        return current > 0.0 ? current / GROWTH : 0.0;
    }

    /**
     * Шаг роста одного предмета. {@code sourceNzt} — суммарный уровень
     * локального источника, поэтому для большого стака наведённая часть
     * делится на его количество. Суммарная полученная доза не зависит от
     * размера стака и не дюпается при split.
     */
    public static void tickInduced(ItemStack stack, boolean hasSource, double sourceNzt, double factor) {
        double next = nextInduced(getInduced(stack), stack, hasSource, sourceNzt, factor);
        if (next > 0.0) setInduced(stack, next);
        else clear(stack);
    }

    /** Совместимость со старыми callers: split больше не требует ручного деления. */
    public static void split(ItemStack stack, double fraction) {
        // Ничего не делаем: ванильный split копирует per-item значение правильно.
    }

    /** Однократная миграция старого total-stack тега до любых split/merge операций. */
    public static void migrateLegacy(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return;
        CompoundTag tag = data.copyTag();
        if (!tag.contains(TAG_RAD) || tag.getInt(TAG_RAD_MODEL) == PER_ITEM_MODEL) return;
        setInduced(stack, tag.getDouble(TAG_RAD) / Math.max(1, stack.getCount()));
    }

    /** Сравнение для нашего серверного объединителя: радиационный компонент игнорируется. */
    public static boolean sameExceptRadiation(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return false;
        ItemStack a = first.copy();
        ItemStack b = second.copy();
        clear(a);
        clear(b);
        // matches() may include count in this mappings version; count is not
        // an identity component for stackability, so normalize it first.
        a.setCount(1);
        b.setCount(1);
        return ItemStack.matches(a, b);
    }

    /** Объединяет два совместимых стака и усредняет per-item дозу без потери суммы. */
    public static void mergeInto(ItemStack target, ItemStack source, int amount) {
        if (amount <= 0) return;
        int oldCount = target.getCount();
        int moved = Math.min(amount, source.getCount());
        double weighted = (getInduced(target) * oldCount + getInduced(source) * moved)
                / Math.max(1, oldCount + moved);
        target.grow(moved);
        source.shrink(moved);
        setInduced(target, weighted);
    }

    /** Полная эмиссия стака: preset per-item × count + induced per-item × count. */
    public static double totalEmission(ItemStack stack) {
        return RadSources.emissionOfStack(stack) + getInduced(stack) * Math.max(1, stack.getCount());
    }

    public static double rawTagValue(CompoundTag tag) {
        return tag == null ? 0.0 : Math.max(0.0, tag.getDouble(TAG_RAD));
    }
}
