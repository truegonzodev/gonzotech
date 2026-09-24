package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.NuclearDefs;
import com.gonzotech.machines.menu.NuclearFireboxMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Dedicated one-slot UI contract for the nuclear firebox. */
public final class NuclearFireboxScreen extends MachineScreen<NuclearFireboxMenu> {

    public NuclearFireboxScreen(NuclearFireboxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("nuclear_firebox_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("nuclear_firebox_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Горение топлива: вертикальная шкала + тултип «осталось X%».
        int burnX = x + 80;
        int burnY = y + 35;
        int burnW = 16;
        int burnH = 16;
        float lit = menu.litDuration() > 0 ? (float) menu.litTime() / menu.litDuration() : 0.0F;
        drawVBarTex(graphics, burnX, burnY, burnW, burnH, lit, BAR_BURNUP);

        int barX = x + 8;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        float gth = (float) menu.gth() / (float) (NuclearDefs.NUCLEAR_FIREBOX_GTH_CAPACITY / 1_000);
        drawVBarTex(graphics, barX, barY, barW, barH, gth, BAR_GTH);
        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth(), NuclearDefs.NUCLEAR_FIREBOX_GTH_CAPACITY / 1_000)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, burnX, burnY, burnW, burnH)) {
            int percent = menu.litDuration() <= 0 ? 0
                : Math.min(100, (int) ((long) menu.litTime() * 100L / menu.litDuration()));
            graphics.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.second_nuclear_firebox.burning", percent)), mouseX, mouseY);
        }
    }
}
