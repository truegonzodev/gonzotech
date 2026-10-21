package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.SeismicSourceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/** Источник колебаний: GUI с единственным слотом динамита. */
public final class SeismicSourceScreen extends MachineScreen<SeismicSourceMenu> {

    public SeismicSourceScreen(SeismicSourceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return machineGui("seismic_source_gui_bg.png", "seismic_source_gui_bg_neo.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return machineGui("seismic_source_gui.png", "seismic_source_gui_neo.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Шкал у источника колебаний пока нет: только слот динамита.
    }
}
