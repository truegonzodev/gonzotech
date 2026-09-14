package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondPressMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Self-drawn four-slot UI for Press II: GTU plus the punch-return fatigue bar. */
public final class SecondPressScreen extends MachineScreen<SecondPressMenu> {

    public SecondPressScreen(SecondPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        SecondGrinderScreen.drawPanel(g, x, y);
        SecondGrinderScreen.drawSlot(g, x + 50, y + 35);
        SecondGrinderScreen.drawSlot(g, x + 128, y + 35);
        SecondGrinderScreen.drawSlot(g, x + 76, y + 17);
        SecondGrinderScreen.drawSlot(g, x + 76, y + 53);

        int capacity = MachineDefs.toUnits(SecondTierDefs.PRESS_GTU_CAPACITY);
        int gtuX = x + 20;
        int barY = y + 17;
        float gtu = capacity == 0 ? 0f : (float) menu.gtu() / capacity;
        float fatigue = menu.fatigueTotal() == 0 ? 0f : (float) menu.fatigueProgress() / menu.fatigueTotal();
        drawVBarTex(g, gtuX, barY, 16, 52, gtu, BAR_GTU);
        drawHBarTex(g, x + 102, y + 35, 20, 16, fatigue, BAR_SMELTING);

        g.drawString(font, Component.translatable("gui.gonzotech.press.form"), x + 65, y + 7, 0xD8E6EE, false);
        g.drawString(font, Component.translatable("gui.gonzotech.press.punch"), x + 62, y + 72, 0xD8E6EE, false);
        g.drawString(font, Component.translatable("gui.gonzotech.press.input"), x + 42, y + 23, 0xD8E6EE, false);
        g.drawString(font, Component.translatable("gui.gonzotech.press.output"), x + 122, y + 23, 0xD8E6EE, false);

        if (inRect(mouseX, mouseY, gtuX, barY, 16, 52)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, x + 102, y + 35, 20, 16)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.press.fatigue", menu.fatiguePercent())), mouseX, mouseY);
        }
    }
}
