package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondPressMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Press II UI using the standard paired PNG background/foreground sheet convention. */
public final class SecondPressScreen extends MachineScreen<SecondPressMenu> {

    public SecondPressScreen(SecondPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("second_press_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("second_press_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Раскладка 1:1 с электропечью I: ГТУ слева, сырьё/выдача по центру,
        // шкала усталости там, где у печи стрелка переплавки; пуансон/форма
        // — правее, в две строки.
        int capacity = MachineDefs.toUnits(SecondTierDefs.PRESS_GTU_CAPACITY);
        int gtuX = x + 8;
        int barY = y + 17;
        int fatigueX = x + 63;
        int fatigueY = y + 36;
        float gtu = capacity == 0 ? 0f : (float) menu.gtu() / capacity;
        float fatigue = menu.fatigueTotal() == 0 ? 0f : (float) menu.fatigueProgress() / menu.fatigueTotal();
        drawVBarTex(g, gtuX, barY, 16, 52, gtu, BAR_GTU);
        drawHBarTex(g, fatigueX, fatigueY, 50, 14, fatigue, BAR_SMELTING);

        if (inRect(mouseX, mouseY, gtuX, barY, 16, 52)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), capacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, fatigueX, fatigueY, 50, 14)) {
            g.renderComponentTooltip(font, List.of(
                Component.translatable("gui.gonzotech.press.fatigue", menu.fatiguePercent())), mouseX, mouseY);
        }
    }
}
