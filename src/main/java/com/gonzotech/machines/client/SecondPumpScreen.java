package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondPumpMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Pump II screen with its own paired PNG sheets and two resource gauges. */
public final class SecondPumpScreen extends MachineScreen<SecondPumpMenu> {

    public SecondPumpScreen(SecondPumpMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("second_pump_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("second_pump_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int gtuX = x + 62;
        int waterX = x + 98;
        int gtuCapacity = MachineDefs.toUnits(SecondTierDefs.PUMP_GTU_CAPACITY);
        drawVBarTex(g, gtuX, barY, barW, barH, (float) menu.gtu() / gtuCapacity, BAR_GTU);
        drawVBarTex(g, waterX, barY, barW, barH,
            (float) menu.water() / SecondTierDefs.PUMP_WATER_CAPACITY, BAR_WATER);
        if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.gtuPair(menu.gtu(), gtuCapacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, waterX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.waterPair(menu.water(), SecondTierDefs.PUMP_WATER_CAPACITY)),
                mouseX, mouseY);
        }
    }
}
