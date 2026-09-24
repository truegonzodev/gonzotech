package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.WortKettleBlockEntity;
import com.gonzotech.machines.menu.WortKettleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * Экран Сусловарочного котла:
 * <ul>
 *   <li>Слот тары под пиво в (26, 35) [холст 154, 163];</li>
 *   <li>Шкала браги (16×52) в (80, 17) [холст 208, 145];</li>
 *   <li>Шкала сусла (16×52) в (116, 17) [холст 244, 145];</li>
 *   <li>Шкала GTH (16×52) в (152, 17) [холст 280, 145].</li>
 * </ul>
 */
public class WortKettleScreen extends MachineScreen<WortKettleMenu> {

    public WortKettleScreen(WortKettleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_wort_kettle_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_wort_kettle_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int mashX = x + 80;
        int wortX = x + 116;
        int gthX = x + 152;

        float mashFrac = (float) menu.mashAmount() / (float) WortKettleBlockEntity.MASH_CAPACITY;
        float wortFrac = (float) menu.wortAmount() / (float) WortKettleBlockEntity.WORT_CAPACITY;
        float gthFrac = (float) menu.gth() / (float) WortKettleBlockEntity.GTH_CAPACITY;

        drawVBarTex(g, mashX, barY, barW, barH, mashFrac, BAR_MASH);
        drawVBarTex(g, wortX, barY, barW, barH, wortFrac, BAR_WORT);
        drawVBarTex(g, gthX, barY, barW, barH, gthFrac, BAR_GTH);

        if (inRect(mouseX, mouseY, mashX, barY, barW, barH)) {
            String alcStr = String.format(Locale.ROOT, "%.1f", menu.mashAlcohol());
            String rotStr = String.format(Locale.ROOT, "%.1f", menu.mashRot());
            g.renderComponentTooltip(this.font,
                GtUnits.mashTooltip(menu.mashAmount(), WortKettleBlockEntity.MASH_CAPACITY, alcStr, rotStr), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, wortX, barY, barW, barH)) {
            String alcStr = String.format(Locale.ROOT, "%.1f", menu.wortAlcohol());
            g.renderComponentTooltip(this.font,
                GtUnits.wortTooltip(menu.wortAmount(), WortKettleBlockEntity.WORT_CAPACITY, alcStr), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth(), menu.maxGth())), mouseX, mouseY);
        }
    }
}
