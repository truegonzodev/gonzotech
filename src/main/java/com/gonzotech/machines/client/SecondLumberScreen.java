package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondLumberMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Dedicated tier-two lumber machine screen. */
public final class SecondLumberScreen extends MachineScreen<SecondLumberMenu> {

    public SecondLumberScreen(SecondLumberMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("second_lumber_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("second_lumber_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int capacity = MachineDefs.toUnits(SecondTierDefs.SECOND_LUMBER_GTU_CAPACITY);
        int gtuX = x + 8;
        int barY = y + 17;
        int progressX = x + 82;
        int progressY = y + 36;
        float gtu = capacity == 0 ? 0f : (float) menu.gtu() / capacity;
        float progress = menu.cutTotal() == 0 ? 0f : (float) menu.cutProgress() / menu.cutTotal();
        drawVBarTex(g, gtuX, barY, 16, 52, gtu, BAR_GTU);
        drawHBarTex(g, progressX, progressY, 50, 14, progress, BAR_SMELTING);

        Component title = Component.translatable("container.gonzotech.second_lumber");
        Component status = Component.translatable(menu.powered()
            ? "gui.gonzotech.second_lumber.powered"
            : "gui.gonzotech.second_lumber.unpowered");
        g.drawString(font, title, x + 8, y + 6, 0xFFE3D6B8, false);
        g.drawString(font, status, x + 82, y + 56, menu.powered() ? 0xFF79D48B : 0xFFA8A8A8, false);

        if (inRect(mouseX, mouseY, gtuX, barY, 16, 52)) {
            g.renderComponentTooltip(font, List.of(
                GtUnits.gtuPair(menu.gtu(), capacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, progressX, progressY, 50, 14)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.second_lumber.progress",
                    menu.cutProgress(), menu.cutTotal())), mouseX, mouseY);
        }
    }
}
