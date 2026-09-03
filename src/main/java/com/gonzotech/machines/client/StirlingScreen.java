package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.StirlingMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран генератора Стирлинга: шкалы пара и GTU, стрелка «пар → GTU». */
public class StirlingScreen extends MachineScreen<StirlingMenu> {

    public StirlingScreen(StirlingMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 12;
        int barH = 52;

        int steX = x + 60;
        int gtuX = x + 104;

        float steam = (float) menu.steam() / MachineDefs.STIRLING_STEAM_CAPACITY;
        float gtu = (float) menu.gtu() / MachineDefs.STIRLING_GTU_CAPACITY;

        drawVBar(g, steX, barY, barW, barH, steam, COL_STEAM);
        drawVBar(g, gtuX, barY, barW, barH, gtu, COL_GTU);

        // Стрелка «пар → GTU» между шкалами.
        drawHBar(g, x + 78, y + 40, 20, 6, menu.running() ? 1f : 0f, COL_GTU);

        if (inRect(mouseX, mouseY, steX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.steam", menu.steam(), MachineDefs.STIRLING_STEAM_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), MachineDefs.STIRLING_GTU_CAPACITY)), mouseX, mouseY);
        }
    }
}
