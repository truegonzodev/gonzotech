package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.CentrifugeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран ЦФ1УР: кипяток, GTU и полоска промывки; скрытые вода/пар не отображаются. */
public class CentrifugeScreen extends MachineScreen<CentrifugeMenu> {

    private static final ResourceLocation BAR_HOT_WATER = gui("bar_hot_water.png");
    private static final ResourceLocation BAR_WASHING = gui("bar_washing.png");

    public CentrifugeScreen(CentrifugeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("centrifuge_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("centrifuge_gui.png");
    }

    @Override
    protected boolean usesRasterMachineSlotHover() {
        return true;
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int hotWaterX = x + 8;
        int gtuX = x + 28;
        int washX = x + 76;
        int washY = y + 35;
        int washW = 24;
        int washH = 16;

        float hotWater = (float) menu.hotWater() / MachineDefs.CENTRIFUGE_HOT_WATER_CAPACITY;
        float gtu = (float) menu.gtu() / MachineDefs.toUnits(MachineDefs.CENTRIFUGE_GTU_CAPACITY);
        float wash = menu.washTotal() > 0 ? (float) menu.washProgress() / menu.washTotal() : 0f;

        drawVBarTex(graphics, hotWaterX, barY, barW, barH, hotWater, BAR_HOT_WATER);
        drawVBarTex(graphics, gtuX, barY, barW, barH, gtu, BAR_GTU);
        drawHBarTex(graphics, washX, washY, washW, washH, wash, BAR_WASHING);

        if (inRect(mouseX, mouseY, hotWaterX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.hot_water", menu.hotWater(), MachineDefs.CENTRIFUGE_HOT_WATER_CAPACITY)),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), MachineDefs.toUnits(MachineDefs.CENTRIFUGE_GTU_CAPACITY))),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, washX, washY, washW, washH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.centrifuge.washing_progress", menu.washProgress(), MachineDefs.CENTRIFUGE_WASH_TICKS)),
                mouseX, mouseY);
        }
    }
}
