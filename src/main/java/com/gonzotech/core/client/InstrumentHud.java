package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/** The layer manager supplies the depth ordering, including chat's background.
 * Post is too late: it runs after the manager restores the root pose/Z, while
 * the depth buffer still contains the chat layer. No global depth-state hacks. */
public final class InstrumentHud {
    private InstrumentHud() {}

    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CHAT,
                ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "instruments"),
                (graphics, partialTick) -> {
                    SolarWatchHud.render(graphics);
                    SpeedometerHud.render(graphics);
                });
    }
}
