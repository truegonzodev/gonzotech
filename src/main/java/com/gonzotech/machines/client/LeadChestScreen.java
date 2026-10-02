package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.ThirdLeadChestMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Экран свинцового ящика: один ряд слотов, шкал нет. */
public class LeadChestScreen extends MachineScreen<ThirdLeadChestMenu> {

    public LeadChestScreen(ThirdLeadChestMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("lead_chest_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("lead_chest_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Содержимое не рисует шкал — только слоты.
    }
}
