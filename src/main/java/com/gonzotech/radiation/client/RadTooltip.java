package com.gonzotech.radiation.client;

import com.gonzotech.radiation.ItemRadioactivity;
import com.gonzotech.radiation.ItemToxicity;
import com.gonzotech.core.text.GtUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Lore-строки радиации и токсичности (авторы 20.09 и 22.09.2026): если параметр применён
 * к предмету (пресетный источник или наведённый NBT-фон для радиации; пресет материала
 * или NBT-тег для токсичности), строка ВСЕГДА рендерится в самом низу тултипа — через одну
 * пустую строку. Для стаков показывается суммарное значение стопки (п.3).
 *
 * <p><b>Хотфикс 22.09.2026:</b> раньше обе строки сидели под одним ранним выходом
 * «нет радиоактивности — выходим», поэтому у металлов (ртуть, свинец, хром…) токсичность
 * в лоре НЕ показывалась: они не радиоактивны. Теперь параметры независимы — у предмета
 * может быть только токсичность, только радиация или обе строки сразу.</p>
 */
public final class RadTooltip {

    private RadTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        double total = ItemRadioactivity.totalEmission(event.getItemStack());
        double toxicity = ItemToxicity.toxicityOfStack(event.getItemStack());
        if (total < 1.0 && toxicity <= 0.0) {
            return;
        }
        // Одна пустая строка-отступ на весь блок параметров, дальше — только те строки,
        // которые реально применены к предмету.
        event.getToolTip().add(Component.empty());
        if (total >= 1.0) {
            // Радиация ВСЕГДА своим цветом #ceeb2d, «/t» — основным цветом строки (&7).
            event.getToolTip().add(Component.translatable("tooltip.gonzotech.radioactivity",
                            GtUnits.zt(total))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (toxicity > 0.0) {
            // Токсичность ВСЕГДА своим цветом #d12176.
            event.getToolTip().add(Component.translatable("tooltip.gonzotech.toxicity",
                            GtUnits.tx(toxicity))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
