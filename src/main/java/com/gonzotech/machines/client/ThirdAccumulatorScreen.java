package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.menu.ThirdAccumulatorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

/** Milli-exact menu sync; standard MachineScreen canvas transform (−128,−128). */
public final class ThirdAccumulatorScreen extends MachineScreen<ThirdAccumulatorMenu> {
    public ThirdAccumulatorScreen(ThirdAccumulatorMenu menu, Inventory inv, Component title) { super(menu,inv,title); }
    @Override protected ResourceLocation backgroundTexture() {
        return machineGui("third_accumulator_gui_bg.png","third_accumulator_gui_bg_neo.png");
    }
    @Override protected ResourceLocation foregroundTexture() {
        return machineGui("third_accumulator_gui.png","third_accumulator_gui_neo.png");
    }
    @Override protected void drawMachine(GuiGraphics g,int x,int y,int mouseX,int mouseY) {
        int barX=x+62, barY=y+17;
        drawVBarTex(g,barX,barY,16,52,(float)((double)menu.storedMilli()/menu.capacityMilli()),BAR_GTU);
        if(inRect(mouseX,mouseY,barX,barY,16,52))
            g.renderComponentTooltip(font,List.of(GtUnits.gtuPair(GtUnits.x1(menu.gtuUnits()),GtUnits.x1(menu.capacityUnits()))),mouseX,mouseY);
    }
}
