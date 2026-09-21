package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondGrinderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Grinder II UI using the standard paired PNG background/foreground sheet convention. */
public final class SecondGrinderScreen extends MachineScreen<SecondGrinderMenu> {

    public SecondGrinderScreen(SecondGrinderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("second_grinder_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("second_grinder_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int capacity = MachineDefs.toUnits(SecondTierDefs.GRINDER_GTU_CAPACITY);
        int gtuX = x + 8;
        int barY = y + 17;
        int progressX = x + 63;
        int progressY = y + 36;
        float gtu = capacity == 0 ? 0f : (float) menu.gtu() / capacity;
        float progress = menu.grindTotal() == 0 ? 0f : (float) menu.grindProgress() / menu.grindTotal();
        drawVBarTex(g, gtuX, barY, 16, 52, gtu, BAR_GTU);
        drawHBarTex(g, progressX, progressY, 50, 14, progress, BAR_SMELTING);

        if (inRect(mouseX, mouseY, gtuX, barY, 16, 52)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, progressX, progressY, 50, 14)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.grinder.grinding_progress", menu.grindProgressPercent())), mouseX, mouseY);
        }
    }
}
