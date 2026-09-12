package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.SecondTierDefs;
import com.gonzotech.machines.menu.SecondCobbleGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** GUI for the fixed 60-tick, no-pickaxe cobblestone generator II. */
public final class SecondCobbleGeneratorScreen extends MachineScreen<SecondCobbleGeneratorMenu> {

    public SecondCobbleGeneratorScreen(SecondCobbleGeneratorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("cobble_generator_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("cobble_generator_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        int waterX = x + 62;
        int gtuX = x + 88;
        int gtuCapacity = MachineDefs.toUnits(SecondTierDefs.COBBLE_GTU_CAPACITY);
        drawVBarTex(g, waterX, barY, barW, barH,
            (float) menu.water() / SecondTierDefs.COBBLE_WATER_CAPACITY, BAR_WATER);
        drawVBarTex(g, gtuX, barY, barW, barH, (float) menu.gtu() / gtuCapacity, BAR_GTU);
        float dig = menu.digTotal() > 0 ? (float) menu.digProgress() / menu.digTotal() : 0f;
        drawHBarTex(g, x + 108, y + 35, 24, 16, dig, BAR_SMELTING);
        if (inRect(mouseX, mouseY, waterX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.water", menu.water(), SecondTierDefs.COBBLE_WATER_CAPACITY)),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), gtuCapacity)), mouseX, mouseY);
        }
    }
}
