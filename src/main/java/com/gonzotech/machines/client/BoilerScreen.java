package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.BoilerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран котла: слоты ведра, три шкалы (GTH/вода/пар). */
public class BoilerScreen extends MachineScreen<BoilerMenu> {

    public BoilerScreen(BoilerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawSlot(g, x + 44 - 1, y + 35 - 1);  // ведро-провайдер воды (вход)
        drawSlot(g, x + 44 - 1, y + 57 - 1);  // пустое ведро (выход)

        int barY = y + 17;
        int barW = 10;
        int barH = 52;

        int gthX = x + 76;
        int watX = x + 100;
        int steX = x + 124;

        float gth = (float) menu.gth() / MachineDefs.BOILER_GTH_CAPACITY;
        float water = (float) menu.water() / MachineDefs.BOILER_WATER_CAPACITY;
        float steam = (float) menu.steam() / MachineDefs.BOILER_STEAM_CAPACITY;

        drawVBar(g, gthX, barY, barW, barH, gth, COL_GTH);
        drawVBar(g, watX, barY, barW, barH, water, COL_WATER);
        drawVBar(g, steX, barY, barW, barH, steam, COL_STEAM);

        if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gth", menu.gth(), MachineDefs.BOILER_GTH_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, watX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.water", menu.water(), MachineDefs.BOILER_WATER_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, steX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.steam", menu.steam(), MachineDefs.BOILER_STEAM_CAPACITY)), mouseX, mouseY);
        }
    }
}
