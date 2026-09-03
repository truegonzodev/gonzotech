package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.FireboxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран топки: шкала пламени, стрелка переплавки, шкала GTH — только проявление текстур. */
public class FireboxScreen extends MachineScreen<FireboxMenu> {

    public FireboxScreen(FireboxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Индикатор пламени (между слотами топлива и нагрузки).
        float lit = menu.litDuration() > 0 ? (float) menu.litTime() / menu.litDuration() : 0f;
        drawVBarTex(g, x + 46, y + 37, 14, 14, lit, BAR_HEAT);

        // Стрелка прогресса переплавки.
        float cook = menu.cookTotal() > 0 ? (float) menu.cookProgress() / menu.cookTotal() : 0f;
        drawHBarTex(g, x + 68, y + 34, 24, 16, cook, BAR_HEAT);

        // Шкала GTH справа.
        int barX = x + 150;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        float gth = menu.gthCapacity() > 0 ? (float) menu.gth() / menu.gthCapacity() : 0f;
        drawVBarTex(g, barX, barY, barW, barH, gth, BAR_HEAT);

        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gth", menu.gth(), MachineDefs.FIREBOX_GTH_CAPACITY)
            ), mouseX, mouseY);
        }
    }
}
