package com.gonzotech.radiation.client;

import com.gonzotech.radiation.RadMaterials;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.Locale;

/** Inventory/placed-item radiation shielding. The item factor is deliberately
 * used here: a placeable block item has the same protection shown in inventory. */
public final class ShieldingTooltip {
    private ShieldingTooltip() {}

    public static void append(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        double factor = RadMaterials.itemFactor(stack);
        if (factor >= 1.0) return;
        double percent = (1.0 - factor) * 100.0;
        String value = Math.abs(percent - Math.rint(percent)) < 0.0001
                ? String.format(Locale.ROOT, "%.0f%%", percent)
                : String.format(Locale.ROOT, "%.1f%%", percent);
        event.getToolTip().add(Component.translatable("tooltip.gonzotech.shielding",
                        Component.literal(value).withColor(0xFFFFFF))
                .withStyle(ChatFormatting.GRAY));
    }
}
