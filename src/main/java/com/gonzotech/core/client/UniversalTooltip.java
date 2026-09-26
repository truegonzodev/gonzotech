package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.radiation.client.HazmatTooltips;
import com.gonzotech.radiation.client.RadTooltip;
import com.gonzotech.radiation.client.ShieldingTooltip;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Single owner of mod lore ordering. Vanilla has already built the name,
 * item lore and (when F3+H is on) advanced lines when this event fires. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class UniversalTooltip {
    private UniversalTooltip() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        TooltipLayout.ensureCreativeCategory(event.getToolTip(), event.getItemStack(),
                event.getEntity() != null && event.getEntity().isCreative());
        List<Component> advanced = TooltipLayout.takeAdvanced(event.getToolTip());
        // block 0 (creative category) remains vanilla-owned; mod blocks start
        // at the first lore line and are deliberately ordered here.
        MaterialStatTooltips.append(event); // description + metal stats, gated by opening 2
        HazmatTooltips.append(event);       // item description block
        ShieldingTooltip.append(event);     // block 13, only when factor < 1
        RadTooltip.append(event);           // blocks 14-15, only when present
        TooltipLayout.collapseEmptyRuns(event.getToolTip());
        event.getToolTip().addAll(advanced); // block 17: vanilla F3+H id/components
        GateTooltips.append(event);         // creative-only gate diagnostics, after vanilla
    }
}
