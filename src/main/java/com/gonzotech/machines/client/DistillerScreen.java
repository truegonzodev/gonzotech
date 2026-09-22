package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.DistillerBlockEntity;
import com.gonzotech.machines.menu.DistillerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * Экран Дистиллятора:
 * <ul>
 *   <li>Шкала GTH (16×52) в (8, 17) [холст 136, 145];</li>
 *   <li>Шкала воды (16×52) в (44, 17) [холст 172, 145];</li>
 *   <li>Шкала кипятка (16×52) в (80, 17) [холст 208, 145];</li>
 *   <li>Шкала входного сырья (16×52) в (116, 17) [холст 244, 145];</li>
 *   <li>Шкала выхода дистиллята / зелья (16×52) в (152, 17) [холст 280, 145].</li>
 * </ul>
 */
public class DistillerScreen extends MachineScreen<DistillerMenu> {

    public DistillerScreen(DistillerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_distiller_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_distiller_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int gthX = x + 8;
        int waterX = x + 44;
        int boilX = x + 80;
        int rawX = x + 116;
        int outX = x + 152;

        float gthFrac = (float) menu.gth() / (float) DistillerBlockEntity.GTH_CAPACITY;
        float waterFrac = (float) menu.water() / (float) DistillerBlockEntity.WATER_CAPACITY;
        float boilFrac = (float) menu.boilingWater() / (float) DistillerBlockEntity.HOT_WATER_CAPACITY;
        float rawFrac = (float) menu.rawAmount() / (float) DistillerBlockEntity.INPUT_CAPACITY;

        boolean hasPoison = menu.poison() > 0;
        float outFrac = hasPoison
            ? (float) menu.poison() / (float) DistillerBlockEntity.OUTPUT_CAPACITY
            : (float) menu.distillate() / (float) DistillerBlockEntity.OUTPUT_CAPACITY;
        ResourceLocation outTex = hasPoison ? BAR_POISON : BAR_DISTILLATE;
        ResourceLocation rawTex = menu.rawRot() > 0 ? BAR_MASH : BAR_WORT;

        drawVBarTex(g, gthX, barY, barW, barH, gthFrac, BAR_GTH);
        drawVBarTex(g, waterX, barY, barW, barH, waterFrac, BAR_WATER);
        drawVBarTex(g, boilX, barY, barW, barH, boilFrac, BAR_GTH);
        drawVBarTex(g, rawX, barY, barW, barH, rawFrac, rawTex);
        drawVBarTex(g, outX, barY, barW, barH, outFrac, outTex);

        if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth(), menu.maxGth())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, waterX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.waterPair(menu.water(), DistillerBlockEntity.WATER_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, boilX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.hotWaterPair(menu.boilingWater(), DistillerBlockEntity.HOT_WATER_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, rawX, barY, barW, barH)) {
            String alcStr = String.format(Locale.ROOT, "%.1f", menu.rawAlcohol());
            String rotStr = String.format(Locale.ROOT, "%.1f", menu.rawRot());
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.mashPair(menu.rawAmount(), DistillerBlockEntity.INPUT_CAPACITY, alcStr, rotStr)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, outX, barY, barW, barH)) {
            if (hasPoison) {
                g.renderComponentTooltip(this.font, List.of(
                    GtUnits.poisonPair(menu.poison(), DistillerBlockEntity.OUTPUT_CAPACITY)), mouseX, mouseY);
            } else {
                g.renderComponentTooltip(this.font, List.of(
                    GtUnits.distillatePair(menu.distillate(), DistillerBlockEntity.OUTPUT_CAPACITY)), mouseX, mouseY);
            }
        }
    }
}
