package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.FermentationVatBlockEntity;
import com.gonzotech.machines.menu.FermentationVatMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * Экран Бродильного чана:
 * <ul>
 *   <li>Шкала GTH (16×52) в (8, 17) [холст 136, 145];</li>
 *   <li>Шкала браги (16×52) в (152, 17) [холст 280, 145];</li>
 *   <li>Слот органики в (80, 35) [холст 208, 163].</li>
 * </ul>
 */
public class FermentationVatScreen extends MachineScreen<FermentationVatMenu> {

    public FermentationVatScreen(FermentationVatMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_fermentation_vat_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_fermentation_vat_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int gthX = x + 8;
        int mashX = x + 152;

        int maxGth = Math.max(1, menu.maxGth());
        float gthFrac = (float) menu.gth() / (float) maxGth;
        float mashFrac = (float) menu.mashAmount() / (float) FermentationVatBlockEntity.MASH_CAPACITY;

        drawVBarTex(g, gthX, barY, barW, barH, gthFrac, BAR_GTH);
        drawVBarTex(g, mashX, barY, barW, barH, mashFrac, BAR_MASH);

        if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth(), menu.maxGth())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, mashX, barY, barW, barH)) {
            String alcStr = String.format(Locale.ROOT, "%.1f", menu.mashAlcohol());
            String rotStr = String.format(Locale.ROOT, "%.1f", menu.mashRot());
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.mashPair(menu.mashAmount(), FermentationVatBlockEntity.MASH_CAPACITY, alcStr, rotStr)), mouseX, mouseY);
        }
    }
}
