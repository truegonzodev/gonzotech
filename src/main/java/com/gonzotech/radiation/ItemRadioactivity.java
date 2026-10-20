package com.gonzotech.radiation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;

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
    /** Последний ленивый радиационный шаг, общий для инвентарей, контейнеров и ItemEntity. */
    private static final String TAG_RAD_LAST_TICK = "gonzo_rad_last_tick";
    public static final int DECAY_STEP_TICKS = 20;
    private static final int PER_ITEM_MODEL = 1;
    public static final double GROWTH = 1.25;
    public static final double INDUCED_CAP = 5.0 * RadUnits.UNIT;
    /** Плато самоподдерживающейся наведёнки (0.3.249): эффективный уровень ~10 mZt. */
    public static final double INDUCED_PLATEAU = 10.0 * RadUnits.MILLI;
    /** Отдача сверх плато: половина избытка гасится каждую секунду (0.3.249). */
    public static final double PLATEAU_DISCHARGE = 0.5;
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
                    tag.remove(TAG_RAD_LAST_TICK);
                }));
        CustomData after = stack.get(DataComponents.CUSTOM_DATA);
        if (after != null && after.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
    }

    /** Рассчитать следующий per-item уровень без изменения ItemStack. */
    public static double nextInduced(double current, ItemStack stack,
                                     boolean hasSource, double sourceNzt, double factor) {
        int count = Math.max(1, stack.getCount());
        // Preset emission is a permanent floor, not an inducible part. A
        // source may only raise a target above its own intrinsic baseline.
        // Носитель (щипцы/ковш, 0.3.83): собственного «фундамента» не имеет —
        // наведёнка растёт от ЧУЖИХ источников инвентаря/чанка; содержимое
        // свою тару не облучает (sourceLevel уже исключает сам стак).
        double intrinsic = stack.getItem() instanceof CarrierItem
                ? 0.0 : RadSources.emissionOfStack(stack);
        double target = Math.min(Math.max(0.0, sourceNzt - intrinsic) / count, INDUCED_CAP);
        double next;
        if (hasSource && target >= 1.0 && factor > 0.0) {
            if (current > target) {
                next = current / GROWTH;
            } else {
                double seeded = Math.max(current, 1.0 / count);
                next = Math.min(
                    seeded + seeded * (GROWTH - 1.0) * (1.0 - seeded / target) * factor,
                    target);
            }
        } else {
            next = current > 0.0 ? current / GROWTH : 0.0;
        }
        return plateauDischarge(next);
    }

    /**
     * Саморазряд наведёнки (0.3.249, идея автора): чем больше доза, тем выше
     * отдача. Всё сверх {@link #INDUCED_PLATEAU} гасится на
     * {@link #PLATEAU_DISCHARGE} за секунду — даже предмет с 5 Zt наведёнки за
     * единицы секунд остывает до плато ~10 mZt, и вечная нейтральная петля
     * «предметы держат друг друга на любом уровне» перестаёт существовать.
     * Ниже плато действуют прежние правила (рост к цели, затухание без
     * источника): слабый источник держит цель ниже плато, плато не подмешивает.
     */
    private static double plateauDischarge(double value) {
        return value > INDUCED_PLATEAU
                ? INDUCED_PLATEAU + (value - INDUCED_PLATEAU) * (1.0 - PLATEAU_DISCHARGE)
                : value;
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

    /**
     * Обычный секундный тик инвентаря с ленивым наверстыванием после контейнера
     * или ItemEntity. Пропущенные интервалы считаются покоем (без взаимного
     * облучения); на текущем шаге применяется только реальный источник инвентаря.
     */
    public static double tickInducedAt(ItemStack stack, boolean hasSource, double sourceNzt,
                                       double factor, long nowTick) {
        if (stack.isEmpty()) return 0.0D;
        long last = lastDecayTick(stack);
        if (last == Long.MIN_VALUE) {
            tickInduced(stack, hasSource, sourceNzt, factor);
            markDecayTick(stack, nowTick);
            decayInducedDeepLazily(stack, nowTick);
            return getInduced(stack);
        }
        long elapsed = nowTick - last;
        if (elapsed < DECAY_STEP_TICKS && elapsed >= 0L) {
            decayInducedDeepLazily(stack, nowTick);
            return getInduced(stack);
        }
        long steps = elapsed < 0L ? 1L : Math.max(1L, elapsed / DECAY_STEP_TICKS);
        if (steps > 1L) decayInducedSteps(stack, steps - 1L);
        tickInduced(stack, hasSource, sourceNzt, factor);
        markDecayTick(stack, nowTick);
        decayInducedDeepLazily(stack, nowTick);
        return getInduced(stack);
    }

    /** Пропущенные пассивные интервалы перед текущим шагом источника — без записи NBT. */
    public static double inducedBeforeSourceStep(ItemStack stack, long nowTick) {
        if (stack.isEmpty()) return 0.0D;
        double current = getInduced(stack);
        long last = lastDecayTick(stack);
        if (current <= 0.0D || last == Long.MIN_VALUE) return current;
        long elapsed = nowTick - last;
        if (elapsed < 0L) return current;
        long steps = elapsed / DECAY_STEP_TICKS;
        if (steps > 1L) current *= Math.pow(1.0D / GROWTH, steps - 1L);
        return current >= CLEANUP_FLOOR ? current : 0.0D;
    }

    /**
     * Ленивый no-source decay для вещей в контейнерах, на полу и в рамках.
     * Возвращает true, если NBT радиации или таймера изменился. Пропущенные интервалы
     * применяются степенями, поэтому обновление не требует тика каждой вещи.
     */
    public static boolean decayInducedLazily(ItemStack stack, long nowTick) {
        if (stack.isEmpty()) return false;
        double before = getInduced(stack);
        if (before <= 0.0D) return false;
        long last = lastDecayTick(stack);
        if (last == Long.MIN_VALUE) {
            markDecayTick(stack, nowTick);
            return true;
        }
        long elapsed = nowTick - last;
        if (elapsed < DECAY_STEP_TICKS && elapsed >= 0L) return false;
        long steps = elapsed < 0L ? 1L : Math.max(1L, elapsed / DECAY_STEP_TICKS);
        decayInducedSteps(stack, steps);
        if (getInduced(stack) > 0.0D) {
            markDecayTick(stack, elapsed < 0L ? nowTick : last + steps * DECAY_STEP_TICKS);
        }
        return getInduced(stack) != before;
    }

    /**
     * Ленивое затухание контейнерного предмета и вложенных слотов (например,
     * шалкера-предмета). Вложенные вещи не облучают друг друга; их собственная
     * наведёнка лишь теряет активность.
     */
    public static boolean decayInducedDeepLazily(ItemStack stack, long nowTick) {
        return decayInducedDeepLazily(stack, nowTick, 0);
    }

    private static boolean decayInducedDeepLazily(ItemStack stack, long nowTick, int depth) {
        if (stack.isEmpty()) return false;
        boolean changed = decayInducedLazily(stack, nowTick);
        if (depth >= 4) return changed;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) return changed;
        List<ItemStack> slots = new ArrayList<>(contents.getSlots());
        boolean nestedChanged = false;
        for (int i = 0; i < contents.getSlots(); i++) {
            ItemStack nested = contents.getStackInSlot(i);
            nestedChanged |= decayInducedDeepLazily(nested, nowTick, depth + 1);
            slots.add(nested);
        }
        if (nestedChanged) {
            stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(slots));
            changed = true;
        }
        return changed;
    }

    /** Отметить уже обработанный инвентарный шаг, в том числе виртуальный held-stack. */
    public static void markDecayTick(ItemStack stack, long nowTick) {
        if (stack.isEmpty() || getInduced(stack) <= 0.0D) return;
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> tag.putLong(TAG_RAD_LAST_TICK, nowTick)));
    }

    private static long lastDecayTick(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Long.MIN_VALUE;
        CompoundTag tag = data.copyTag();
        return tag.contains(TAG_RAD_LAST_TICK) ? tag.getLong(TAG_RAD_LAST_TICK) : Long.MIN_VALUE;
    }

    private static void decayInducedSteps(ItemStack stack, long steps) {
        double current = getInduced(stack);
        if (current <= 0.0D || steps <= 0L) return;
        double next = current * Math.pow(1.0D / GROWTH, steps);
        if (next >= CLEANUP_FLOOR) setInduced(stack, next);
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

    /** Полная эмиссия стака: preset per-item × count + induced per-item × count. */
    public static double totalEmission(ItemStack stack) {
        return RadSources.emissionOfStack(stack) + getInduced(stack) * Math.max(1, stack.getCount());
    }

    public static double rawTagValue(CompoundTag tag) {
        return tag == null ? 0.0 : Math.max(0.0, tag.getDouble(TAG_RAD));
    }
}
