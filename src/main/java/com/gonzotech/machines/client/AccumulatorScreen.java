package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.AccumulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран энергохранилища: одна большая шкала GTU — только проявление текстуры. */
public class AccumulatorScreen extends MachineScreen<AccumulatorMenu> {

    public AccumulatorScreen(AccumulatorMenu menu, Inventory inv, Component title) {
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
        // Крупная центральная шкала GTU.
        int barX = x + 80;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        float gtu = (float) menu.gtu() / MachineDefs.toUnits(MachineDefs.ACCUMULATOR_GTU_CAPACITY);
        drawVBarTex(g, barX, barY, barW, barH, gtu, BAR_GTU);

        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(),
                    MachineDefs.toUnits(MachineDefs.ACCUMULATOR_GTU_CAPACITY))), mouseX, mouseY);
        }
    }
}
