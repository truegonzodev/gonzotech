package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.ElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран электропечи: стрелка переплавки + шкала GTU — только проявление текстур. */
public class ElectricFurnaceScreen extends MachineScreen<ElectricFurnaceMenu> {

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Стрелка прогресса переплавки.
        float cook = menu.cookTotal() > 0 ? (float) menu.cookProgress() / menu.cookTotal() : 0f;
        drawHBarTex(g, x + 80, y + 34, 24, 16, cook, BAR_FLUID);

        // Шкала GTU слева.
        int barX = x + 20;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        float gtu = (float) menu.gtu() / MachineDefs.ELECTRIC_GTU_CAPACITY;
        drawVBarTex(g, barX, barY, barW, barH, gtu, BAR_FLUID);

        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), MachineDefs.ELECTRIC_GTU_CAPACITY)), mouseX, mouseY);
        }
    }
}
