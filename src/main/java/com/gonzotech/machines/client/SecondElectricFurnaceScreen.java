package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Electric Furnace II art has two independently rendered working lanes. */
public final class SecondElectricFurnaceScreen extends MachineScreen<SecondElectricFurnaceMenu> {

    public SecondElectricFurnaceScreen(SecondElectricFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("second_electric_furnace_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("second_electric_furnace_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Две стрелки прогресса переплавки (50x14). Tier 2: у каждой — свой
        // тултип «Переплавка: N%».
        int[] progressX = {x + 63, x + 63};
        int[] progressY = {y + 18, y + 54};
        for (int lane = 0; lane < 2; lane++) {
            float cook = menu.cookTotal(lane) > 0
                ? (float) menu.cookProgress(lane) / menu.cookTotal(lane)
                : 0f;
            drawHBarTex(g, progressX[lane], progressY[lane], 50, 14, cook, BAR_SMELTING);
        }

        int barX = x + 8;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int capacity = MachineDefs.toUnits(SecondTierDefs.ELECTRIC_GTU_CAPACITY);
        float gtu = (float) menu.gtu() / capacity;
        drawVBarTex(g, barX, barY, barW, barH, gtu, BAR_GTU);
        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, progressX[0], progressY[0], 50, 14)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.smelting_progress", menu.cookProgressPercent(0))),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, progressX[1], progressY[1], 50, 14)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.smelting_progress", menu.cookProgressPercent(1))),
                mouseX, mouseY);
        }
    }
}
