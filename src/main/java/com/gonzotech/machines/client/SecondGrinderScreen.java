package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondGrinderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Self-drawn two-slot UI for Grinder II: GTU buffer and 35-tick grinding bar. */
public final class SecondGrinderScreen extends MachineScreen<SecondGrinderMenu> {

    public SecondGrinderScreen(SecondGrinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawPanel(g, x, y);
        drawSlot(g, x + 56, y + 35);
        drawSlot(g, x + 116, y + 35);

        int capacity = MachineDefs.toUnits(SecondTierDefs.GRINDER_GTU_CAPACITY);
        int gtuX = x + 20;
        int barY = y + 17;
        float gtu = capacity == 0 ? 0f : (float) menu.gtu() / capacity;
        float progress = menu.grindTotal() == 0 ? 0f : (float) menu.grindProgress() / menu.grindTotal();
        drawVBarTex(g, gtuX, barY, 16, 52, gtu, BAR_GTU);
        drawHBarTex(g, x + 80, y + 35, 24, 16, progress, BAR_SMELTING);

        g.drawString(font, Component.translatable("gui.gonzotech.grinder.input"), x + 50, y + 23, 0xD8E6EE, false);
        g.drawString(font, Component.translatable("gui.gonzotech.grinder.output"), x + 108, y + 23, 0xD8E6EE, false);

        if (inRect(mouseX, mouseY, gtuX, barY, 16, 52)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, x + 80, y + 35, 24, 16)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.grinder.grinding_progress", menu.grindProgressPercent())), mouseX, mouseY);
        }
    }

    static void drawPanel(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 176, y + 166, 0xFF17212B);
        g.fill(x, y, x + 176, y + 2, 0xFF6E8798);
        g.fill(x, y + 164, x + 176, y + 166, 0xFF314552);
        g.fill(x, y, x + 2, y + 166, 0xFF6E8798);
        g.fill(x + 174, y, x + 176, y + 166, 0xFF314552);
        g.fill(x + 8, y + 80, x + 168, y + 81, 0xFF314552);
    }

    static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF6E8798);
        g.fill(x, y, x + 16, y + 16, 0xFF0C1218);
        g.fill(x + 1, y + 1, x + 15, y + 15, 0xFF263845);
    }
}
