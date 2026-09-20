package com.gonzotech.radiation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Наведённая («приобретённая») радиоактивность предмета — динамический NBT-тег
 * {@code gonzo_rad} внутри {@code custom_data} (автор 20.09, п.1). Значение —
 * эмиссия ЦЕЛОГО СТАКА в nZt/с (не per-item: деление стака делит и фон
 * примерно пропорционально, чем мельче дробь — см. {@link #split}).
 *
 * <p>Правила из спеки:</p>
 * <ul>
 *   <li>наводится, пока рядом в инвентаре лежат пресетные источники
 *       ({@link RadSources}) или стоишь в фонящем чанке (>30mZt);</li>
 *   <li>растёт/затухает ЭКСПОНЕНЦИАЛЬНО с одним шагом ×1.25/с в обе стороны
 *       (автор: «затухает так же, как постепенно поднимался»);</li>
 *   <li><b>свинец и всё с ним связанное не становится радиоактивным</b> —
 *       иммунитет по id: путь содержит {@code "lead"} (покрывает слиток, пыль,
 *       самородок, блок, свинцовое стекло и будущие свинцовые предметы).</li>
 * </ul>
 */
public final class ItemRadioactivity {

    /** Имя NBT-поля эмиссии в custom_data стака (nZt/с, double). */
    public static final String TAG_RAD = "gonzo_rad";

    /** Шаг роста/затухания в секунду (×1.25 ≈ ×10⁶ за минуту — точно пример автора «1n→1m за минуту»). */
    public static final double GROWTH = 1.25;
    /** Потолок наведённой эмиссии стака. */
    public static final double INDUCED_CAP = 5.0 * RadUnits.UNIT;
    /** Порог сброса тега (ниже — стак снова «чистый», NBT удаляется). */
    public static final double CLEANUP_FLOOR = 1.0;

    private ItemRadioactivity() {
    }

    public static boolean isLeadImmune(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath().contains("lead");
    }

    /** Наведённая эмиссия стака (nZt/с); 0, если тега нет. */
    public static double getInduced(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0.0 : data.copyTag().getDouble(TAG_RAD);
    }

    public static void setInduced(ItemStack stack, double value) {
        if (value < CLEANUP_FLOOR) {
            clear(stack);
            return;
        }
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                data -> data.update(tag -> tag.putDouble(TAG_RAD, value)));
    }

    private static void clear(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY,
                d -> d.update(tag -> tag.remove(TAG_RAD)));
        CustomData after = stack.get(DataComponents.CUSTOM_DATA);
        if (after != null && after.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
    }

    /**
     * Один серверный шаг (раз в секунду) роста/затухания стака.
     *
     * @param hasSource рядом есть пресетный источник (инвентарь) или фонящий чанк
     */
    public static void tickInduced(ItemStack stack, boolean hasSource) {
        double current = getInduced(stack);
        if (hasSource) {
            if (isLeadImmune(stack)) {
                return;
            }
            double next = Math.min(Math.max(current, 1.0) * GROWTH, INDUCED_CAP);
            if (next != current) {
                setInduced(stack, next);
            }
        } else if (current > 0.0) {
            setInduced(stack, current / GROWTH);
        }
    }

    /** Доля фона при делении стака пополам и т.п. (вызывается из событий переноса при необходимости). */
    public static void split(ItemStack stack, double fraction) {
        double current = getInduced(stack);
        if (current > 0.0) {
            setInduced(stack, current * fraction);
        }
    }

    /** Полная эмиссия стака для тултипа/дозы: пресетная (×count) + наведённая. */
    public static double totalEmission(ItemStack stack) {
        return RadSources.emissionOfStack(stack) + getInduced(stack);
    }

    /** Дебаг/совместимость: прямое чтение любого стороннего {@code custom_data}. */
    public static double rawTagValue(CompoundTag tag) {
        return tag == null ? 0.0 : tag.getDouble(TAG_RAD);
    }
}
