package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.PcfsozMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран ПЦФСОЗ: кипяток, GTU и полоска разделения; скрытая вода не отображается. */
public class PcfsozScreen extends MachineScreen<PcfsozMenu> {

    private static final ResourceLocation BAR_HOT_WATER = gui("bar_hot_water.png");
    private static final ResourceLocation BAR_SEPARATION = gui("bar_washing.png");

    public PcfsozScreen(PcfsozMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("pcfsoz_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("pcfsoz_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int hotWaterX = x + 8;
        int gtuX = x + 26;
        int washX = x + 81;
        int washY = y + 35;
        int washW = 33;
        int washH = 16;

        float hotWater = (float) menu.hotWater() / MachineDefs.PCFSOZ_HOT_WATER_CAPACITY;
        float gtu = (float) menu.gtu() / MachineDefs.toUnits(MachineDefs.PCFSOZ_GTU_CAPACITY);
        float wash = menu.washTotal() > 0 ? (float) menu.washProgress() / menu.washTotal() : 0f;

        drawVBarTex(graphics, hotWaterX, barY, barW, barH, hotWater, BAR_HOT_WATER);
        drawVBarTex(graphics, gtuX, barY, barW, barH, gtu, BAR_GTU);
        drawHBarTex(graphics, washX, washY, washW, washH, wash, BAR_SEPARATION);

        if (inRect(mouseX, mouseY, hotWaterX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.hotWaterPair(menu.hotWater(), MachineDefs.PCFSOZ_HOT_WATER_CAPACITY)),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.gtuPair(menu.gtu(), MachineDefs.toUnits(MachineDefs.PCFSOZ_GTU_CAPACITY))),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, washX, washY, washW, washH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.third_pcfsoz.separation_progress", menu.washProgressPercent())),
                mouseX, mouseY);
        }
    }
}
