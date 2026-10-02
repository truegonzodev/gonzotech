package com.gonzotech.radiation;

import net.minecraft.world.item.ItemStack;

/**
 * Предмет-носитель опасных грузов (0.3.79): щипцы и ковш.
 *
 * <p>Носитель снижает дозу СОДЕРЖИМОГО для держателя: радиоактивность
 * × {@link #RADIOACTIVITY_FACTOR} (−40 %), токсичность ×
 * {@link #TOXICITY_FACTOR} (−80 %) — автор 01.10.2026 (вместо старых
 * −60/−70/−90 из EPOCH3-BASE §2.8; «64 слитка фонят как 19» больше не норма).
 *
 * <p>Точки перехвата: {@link RadSources#emissionOfStack} и
 * {@link ItemToxicity#toxicityOfStack} — через них идут все суммы доз
 * (RadiationSystem, чанки, тултипы), поэтому носитель «светится» меньше
 * везде автоматически.
 */
public interface CarrierItem {

    /** Остаточная доля радиоактивности содержимого (−40 %). */
    double RADIOACTIVITY_FACTOR = 0.60;

    /** Остаточная доля токсичности содержимого (−80 %). */
    double TOXICITY_FACTOR = 0.20;

    /** Есть ли в носителе предметное содержимое (жидкость ковша не считается). */
    boolean hasCarried(ItemStack carrier);

    /** Содержимое как стак (чтение; носитель не меняется). */
    ItemStack previewCarried(ItemStack carrier);

    /** Извлечь содержимое, опустошив носитель (для выгрузки в станок). */
    ItemStack takeCarried(ItemStack carrier);

    /** Эмиссия носителя = эмиссия содержимого × остаточная доля (−40 %). */
    default double carriedEmission(ItemStack carrier) {
        ItemStack content = previewCarried(carrier);
        return content.isEmpty() ? 0.0 : RadSources.emissionOfStack(content) * RADIOACTIVITY_FACTOR;
    }

    /** Токсичность носителя = токсичность содержимого × остаточная доля (−80 %). */
    default double carriedToxicity(ItemStack carrier) {
        ItemStack content = previewCarried(carrier);
        return content.isEmpty() ? 0.0 : ItemToxicity.toxicityOfStack(content) * TOXICITY_FACTOR;
    }
}
