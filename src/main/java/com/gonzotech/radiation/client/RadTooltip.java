package com.gonzotech.radiation.client;

import com.gonzotech.radiation.ItemRadioactivity;
import com.gonzotech.radiation.ItemToxicity;
import com.gonzotech.radiation.RadUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Lore-строка радиоактивности (автор 20.09, п.1): если параметр применён к
 * предмету (пресетный источник или наведённый NBT-фон), она ВСЕГДА рендерится
 * в самом низу тултипа — через одну пустую строку.
 * Для стаков показывается суммарная эмиссия стопки (п.3).
 */
public final class RadTooltip {

    private RadTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        double total = ItemRadioactivity.totalEmission(event.getItemStack());
        if (total < 1.0) {
            return;
        }
        // пустая строка-отступ, затем «Радиоактивность: X» последней строкой (без значка)
        event.getToolTip().add(Component.empty());
        event.getToolTip().add(Component.translatable("tooltip.gonzotech.radioactivity",
                        Component.literal(RadUnits.format(total)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));

        // Токсичность (автор 22.09.2026): такой же параметр, но проще радиации и в Tx/с.
        double toxicity = ItemToxicity.toxicityOfStack(event.getItemStack());
        if (toxicity > 0.0) {
            event.getToolTip().add(Component.translatable("tooltip.gonzotech.toxicity",
                            Component.literal(ItemToxicity.format(toxicity)).withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
