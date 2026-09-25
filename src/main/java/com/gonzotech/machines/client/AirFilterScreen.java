package com.gonzotech.machines.client;

import com.gonzotech.cleanroom.AirFilterBlockEntity;
import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.menu.AirFilterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Standard 512x512 layered GUI; no texture scaling or client BlockEntity reads. */
public final class AirFilterScreen extends MachineScreen<AirFilterMenu> {
    public AirFilterScreen(AirFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 184;
    }

    @Override protected ResourceLocation backgroundTexture() { return gui("third_air_filter_gui_bg.png"); }
    @Override protected ResourceLocation foregroundTexture() { return gui("third_air_filter_gui.png"); }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        drawVBarTex(graphics, x + 8, y + 110, 16, 52,
                menu.gtu() / (float) AirFilterBlockEntity.CAPACITY_GTU, BAR_GTU);
        if (inRect(mouseX, mouseY, x + 8, y + 110, 16, 52)) {
            graphics.renderComponentTooltip(font, List.of(GtUnits.gtuPair(menu.gtu(), AirFilterBlockEntity.CAPACITY_GTU)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, x + 40, y + 145, 125, 25)) {
            Component quality = menu.qualityHundredths() < 0
                    ? Component.translatable("gui.gonzotech.air_filter.no_room")
                    : Component.translatable("message.gonzotech.telifon.air_quality", Math.round(menu.qualityHundredths() / 100.0f) + "%");
            graphics.renderComponentTooltip(font, List.of(quality,
                    Component.translatable("gui.gonzotech.air_filter.fuel_time", menu.coalSeconds(), menu.catalystSeconds())), mouseX, mouseY);
        }
    }
}
