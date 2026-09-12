package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Reuses the electric-furnace art while rendering two independently working lanes. */
public final class SecondElectricFurnaceScreen extends MachineScreen<SecondElectricFurnaceMenu> {

    public SecondElectricFurnaceScreen(SecondElectricFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("electric_furnace_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("electric_furnace_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        for (int lane = 0; lane < 2; lane++) {
            float cook = menu.cookTotal(lane) > 0
                ? (float) menu.cookProgress(lane) / menu.cookTotal(lane)
                : 0f;
            drawHBarTex(g, x + 80, y + (lane == 0 ? 22 : 48), 24, 16, cook, BAR_SMELTING);
        }
        int barX = x + 20;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int capacity = MachineDefs.toUnits(SecondTierDefs.ELECTRIC_GTU_CAPACITY);
        float gtu = (float) menu.gtu() / capacity;
        drawVBarTex(g, barX, barY, barW, barH, gtu, BAR_GTU);
        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        }
    }
}
