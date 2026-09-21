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
     * true, если {@code from} и {@code to} отличаются ТОЛЬКО значением
     * наведённой радиации (gonzo_rad) — такие различия анимируют не нужно.
     */
    public static boolean onlyRadChanged(ItemStack from, ItemStack to) {
        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }
        double ra = ItemRadioactivity.getInduced(from);
        double rt = ItemRadioactivity.getInduced(to);
        if (ra == rt) {
            return false; // наш тег не менялся — пусть решает ваниль
        }
        ItemStack a = from.copy();
        ItemStack b = to.copy();
        stripRad(a);
        stripRad(b);
        return ItemStack.matches(a, b);
    }

    private static void stripRad(ItemStack stack) {
        stack.update(DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY,
                d -> d.update(tag -> tag.remove(ItemRadioactivity.TAG_RAD)));
        net.minecraft.world.item.component.CustomData after = stack.get(DataComponents.CUSTOM_DATA);
        if (after != null && after.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        }
    }
}
