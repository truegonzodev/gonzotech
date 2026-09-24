package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
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
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("firebox_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("firebox_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Индикатор горения топлива (вертикальный, между слотами сырья и топлива).
        float lit = menu.litDuration() > 0 ? (float) menu.litTime() / menu.litDuration() : 0f;
        drawVBarTex(g, x + 44, y + 35, 16, 16, lit, BAR_BURNUP);

        // Стрелка прогресса переплавки.
        float cook = menu.cookTotal() > 0 ? (float) menu.cookProgress() / menu.cookTotal() : 0f;
        drawHBarTex(g, x + 63, y + 36, 50, 14, cook, BAR_SMELTING);

        // Шкала GTH слева.
        int barX = x + 8;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        float gth = menu.gthCapacity() > 0 ? (float) menu.gth() / menu.gthCapacity() : 0f;
        drawVBarTex(g, barX, barY, barW, barH, gth, BAR_GTH);

        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth(), MachineDefs.toUnits(MachineDefs.FIREBOX_GTH_CAPACITY))
            ), mouseX, mouseY);
        }
    }
}
