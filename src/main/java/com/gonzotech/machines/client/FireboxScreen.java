package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.FireboxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран топки: слоты нагрузки/топлива/выхода, стрелка переплавки, шкала GTH справа. */
public class FireboxScreen extends MachineScreen<FireboxMenu> {

    public FireboxScreen(FireboxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Слоты машины (совпадают с координатами в FireboxMenu).
        drawSlot(g, x + 44 - 1, y + 17 - 1);   // вход-нагрузка
        drawSlot(g, x + 44 - 1, y + 53 - 1);   // топливо
        drawSlot(g, x + 104 - 1, y + 35 - 1);  // выход

        // Индикатор пламени между слотами топлива и нагрузки.
        float lit = menu.litDuration() > 0 ? (float) menu.litTime() / menu.litDuration() : 0f;
        drawVBar(g, x + 46, y + 37, 12, 12, lit, COL_GTH);

        // Стрелка прогресса переплавки.
        float cook = menu.cookTotal() > 0 ? (float) menu.cookProgress() / menu.cookTotal() : 0f;
        drawHBar(g, x + 68, y + 34, 24, 6, cook, 0xFFB8B8B8);

        // Шкала GTH справа.
        int barX = x + 150;
        int barY = y + 17;
        int barW = 10;
        int barH = 52;
        float gth = menu.gthCapacity() > 0 ? (float) menu.gth() / menu.gthCapacity() : 0f;
        drawVBar(g, barX, barY, barW, barH, gth, COL_GTH);

        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gth", menu.gth(), MachineDefs.FIREBOX_GTH_CAPACITY)
            ), mouseX, mouseY);
        }
    }
}
