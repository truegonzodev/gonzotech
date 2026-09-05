package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.PumpMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран помпы: две шкалы (GTU / вода) — только проявление текстур. */
public class PumpScreen extends MachineScreen<PumpMenu> {

    public PumpScreen(PumpMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("pump_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("pump_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int gtuX = x + 100;
        int watX = x + 126;

        float gtu = (float) menu.gtu() / MachineDefs.PUMP_GTU_CAPACITY;
        float water = (float) menu.water() / MachineDefs.PUMP_WATER_CAPACITY;

        drawVBarTex(g, gtuX, barY, barW, barH, gtu, BAR_GTU);
        drawVBarTex(g, watX, barY, barW, barH, water, BAR_WATER);

        if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), MachineDefs.PUMP_GTU_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, watX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.water", menu.water(), MachineDefs.PUMP_WATER_CAPACITY)), mouseX, mouseY);
        }
    }
}
