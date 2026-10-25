package com.gonzotech.radiation.client;

import com.gonzotech.radiation.ItemRadioactivity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Гейт анимации для «тикающих» радиопредметов (баг-репорт автора 20.09, п.2):
 * NBT-штамп {@code gonzo_rad} обновляется каждую секунду → ваниль считала
 * стак «другим предметом» и проигрывала re-equip bob (предмет прыгает снизу
 * вверх). Гейт отвечает на вопрос: изменилось ли в стаке ЧТО-ЛИБО, кроме
 * нашего тега радиации? Если нет — анимацию подавляем (миксин
 * {@code ItemInHandRendererMixin} подставляет «мгновенную» замену).
 *
 * <p>Класс живёт ВНЕ пакета {@code com.gonzotech.mixin.*} (правило из прошлого
 * краша: на классы mixin-пакета нельзя ссылаться из миксинов).</p>
 */
public final class RadReequipGate {

    private RadReequipGate() {
    }

    /**
     * true, если {@code from} и {@code to} отличаются только радиационными
     * штампами gonzo_rad / gonzo_rad_model / gonzo_rad_last_tick (включая
     * затухание вложенного содержимого CONTAINER/BUNDLE) — такие различия
     * анимировать не нужно: предмет в руке тот же, поменялась лишь доза.
     *
     * <p>0.3.300 (автор: «прыгает в руке, скрой визуально»): гейт был написан
     * до появления тик-штампа markDecayTick — strip'ил два штампа из трёх,
     * стопки никогда не совпадали и bob играл каждый радиационный проход.
     * Ранний возврат «значение не менялось» убран: теперь меняется и один
     * last_tick (обычные предметы!), и разница обязана сверяться полным
     * сравнением после зачистки всех радиационных следов.</p>
     */
    public static boolean onlyRadChanged(ItemStack from, ItemStack to) {
        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }
        ItemStack a = from.copy();
        ItemStack b = to.copy();
        stripRadDeep(a, true);
        stripRadDeep(b, true);
        return ItemStack.matches(a, b);
    }

    /** Снять радиационные штампы; {@code allowNested} — зайти в child-стопки. */
    private static void stripRadDeep(ItemStack stack, boolean allowNested) {
        stack.update(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY,
                d -> d.update(tag -> {
                    tag.remove(ItemRadioactivity.TAG_RAD);
                    tag.remove(ItemRadioactivity.TAG_RAD_MODEL);
                    tag.remove(ItemRadioactivity.TAG_RAD_LAST_TICK);
                }));
        net.minecraft.world.item.component.CustomData after = stack.get(DataComponents.CUSTOM_DATA);
        if (after != null && after.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
        if (!allowNested) {
            return;
        }
        net.minecraft.world.item.component.ItemContainerContents content =
            stack.get(DataComponents.CONTAINER);
        if (content != null) {
            // 0.3.301: в Mojmap 1.21.4 ItemContainerContents НЕ Iterable —
            // обход индексно, как в ItemRadioactivity.decayInducedDeepLazily.
            java.util.List<ItemStack> slots = new java.util.ArrayList<>();
            for (int i = 0; i < content.getSlots(); i++) {
                ItemStack copy = content.getStackInSlot(i).copy();
                stripRadDeep(copy, false);
                slots.add(copy);
            }
            stack.set(DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(slots));
        }
        net.minecraft.world.item.component.BundleContents bundle =
            stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            java.util.List<ItemStack> inner = new java.util.ArrayList<>();
            for (ItemStack one : bundle.items()) {
                ItemStack copy = one.copy();
                stripRadDeep(copy, false);
                inner.add(copy);
            }
            stack.set(DataComponents.BUNDLE_CONTENTS,
                new net.minecraft.world.item.component.BundleContents(inner));
        }
    }
}
