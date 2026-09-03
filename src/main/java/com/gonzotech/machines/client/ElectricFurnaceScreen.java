package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.ElectricFurnaceMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран электропечи: слоты вход/выход, стрелка переплавки, шкала GTU, индикатор цепочки. */
public class ElectricFurnaceScreen extends MachineScreen<ElectricFurnaceMenu> {

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawSlot(g, x + 56 - 1, y + 35 - 1);   // вход
        drawSlot(g, x + 116 - 1, y + 35 - 1);  // выход

        // Стрелка прогресса переплавки.
        float cook = menu.cookTotal() > 0 ? (float) menu.cookProgress() / menu.cookTotal() : 0f;
        drawHBar(g, x + 80, y + 34, 24, 6, cook, COL_GTU);

        // Шкала GTU слева.
        int barX = x + 20;
        int barY = y + 17;
        int barW = 12;
        int barH = 52;
        float gtu = (float) menu.gtu() / MachineDefs.GTU_CAPACITY;
        drawVBar(g, barX, barY, barW, barH, gtu, COL_GTU);

        if (!menu.chainOk()) {
            g.drawString(this.font,
                Component.translatable("gui.gonzotech.chain_missing_stirling").withStyle(ChatFormatting.RED),
                x + 8, y + 72, TEXT, false);
        }

        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), MachineDefs.GTU_CAPACITY)), mouseX, mouseY);
        }
    }
}
