package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondAccumulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Visual twin of the accumulator screen, scaled to the level-II buffer. */
public final class SecondAccumulatorScreen extends MachineScreen<SecondAccumulatorMenu> {

    public SecondAccumulatorScreen(SecondAccumulatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("accumulator_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("accumulator_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barX = x + 80;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int capacity = MachineDefs.toUnits(SecondTierDefs.ACCUMULATOR_GTU_CAPACITY);
        float gtu = (float) menu.gtu() / capacity;
        drawVBarTex(g, barX, barY, barW, barH, gtu, BAR_GTU);
        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        }
    }
}
