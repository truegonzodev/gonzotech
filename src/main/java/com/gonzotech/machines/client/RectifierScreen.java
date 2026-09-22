package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.RectifierBlockEntity;
import com.gonzotech.machines.menu.RectifierMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Экран Ректификатора:
 * <ul>
 *   <li>Шкала GTU (16×52) в (8, 17) [холст 136, 145];</li>
 *   <li>Шкала GTH (16×52) в (26, 17) [холст 154, 145];</li>
 *   <li>Шкала дистиллята (16×52) в (62, 17) [холст 190, 145];</li>
 *   <li>Шкала ректификата (16×52) в (98, 17) [холст 226, 145].</li>
 * </ul>
 */
public class RectifierScreen extends MachineScreen<RectifierMenu> {

    public RectifierScreen(RectifierMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_rectifier_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_rectifier_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int gtuX = x + 8;
        int gthX = x + 26;
        int distX = x + 62;
        int rectX = x + 98;

        float gtuFrac = (float) menu.gtu() / (float) RectifierBlockEntity.GTU_CAPACITY;
        float gthFrac = (float) menu.gth() / (float) RectifierBlockEntity.GTH_CAPACITY;
        float distFrac = (float) menu.distillate() / (float) RectifierBlockEntity.DISTILLATE_CAPACITY;
        float rectFrac = (float) menu.rectificate() / (float) RectifierBlockEntity.RECTIFICATE_CAPACITY;

        drawVBarTex(g, gtuX, barY, barW, barH, gtuFrac, BAR_GTU);
        drawVBarTex(g, gthX, barY, barW, barH, gthFrac, BAR_GTH);
        drawVBarTex(g, distX, barY, barW, barH, distFrac, BAR_DISTILLATE);
        drawVBarTex(g, rectX, barY, barW, barH, rectFrac, BAR_RECTIFICATE);

        if (inRect(mouseX, mouseY, gtuX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.gtuPair(menu.gtu(), menu.maxGtu())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth(), menu.maxGth())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, distX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.distillatePair(menu.distillate(), RectifierBlockEntity.DISTILLATE_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, rectX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.rectificatePair(menu.rectificate(), RectifierBlockEntity.RECTIFICATE_CAPACITY)), mouseX, mouseY);
        }
    }
}
