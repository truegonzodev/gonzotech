package com.gonzotech.radiation.client;

import com.gonzotech.radiation.ItemToxicity;
import com.gonzotech.core.text.GtUnits;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Toxicity remains in the item tooltip. Radiation is painted separately by RadiationPanel. */
public final class RadTooltip {

    private RadTooltip() {
    }

    public static void append(ItemTooltipEvent event) {
        double toxicity = ItemToxicity.toxicityOfStack(event.getItemStack());
        if (toxicity > 0.0) {
            event.getToolTip().add(Component.empty());
            event.getToolTip().add(Component.translatable("tooltip.gonzotech.toxicity",
                    GtUnits.tx(toxicity)).withStyle(ChatFormatting.GRAY));
        }
    }
}
