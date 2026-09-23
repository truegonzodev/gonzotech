package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.block.entity.SnaketypeCondenserBlockEntity;
import com.gonzotech.machines.menu.SnaketypeCondenserMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран Змеевикового конденсатора:
 * <ul>
 *   <li>Шкала кипятка (16×52) в (62, 17) [холст 190, 145];</li>
 *   <li>Хитбокс тултипа охлаждения (16×52) от (80, 17) до (95, 68) [холст 208, 145 .. 223, 196];</li>
 *   <li>Шкала охлаждённой воды (16×52) в (98, 17) [холст 226, 145].</li>
 * </ul>
 */
public class SnaketypeCondenserScreen extends MachineScreen<SnaketypeCondenserMenu> {

    public SnaketypeCondenserScreen(SnaketypeCondenserMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("third_snaketype_condenser_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("third_snaketype_condenser_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int boilX = x + 62;
        int midX = x + 80;
        int waterX = x + 98;

        float boilFrac = (float) menu.boilingWater() / (float) SnaketypeCondenserBlockEntity.BOILING_WATER_CAPACITY;
        float waterFrac = (float) menu.water() / (float) SnaketypeCondenserBlockEntity.WATER_CAPACITY;

        drawVBarTex(g, boilX, barY, barW, barH, boilFrac, BAR_HOT_WATER);
        drawVBarTex(g, waterX, barY, barW, barH, waterFrac, BAR_WATER);

        if (inRect(mouseX, mouseY, boilX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font,
                GtUnits.boilingWaterTooltip(menu.boilingWater(), menu.maxBoilingWater()), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, waterX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                GtUnits.waterPair(menu.water(), menu.maxWater())), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, midX, barY, barW, barH)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(GtUnits.condenserCoolingRate(menu.coolingRate()));

            int reg = menu.regularIce();
            int pck = menu.packedIce();
            int eur = menu.europanIce();
            int blu = menu.blueIce();
            int sup = menu.superdenseIce();

            if (reg > 0) tooltip.add(Component.translatable("gui.gonzotech.condenser.ice_regular", reg, reg * 1));
            if (pck > 0) tooltip.add(Component.translatable("gui.gonzotech.condenser.ice_packed", pck, pck * 3));
            if (eur > 0) tooltip.add(Component.translatable("gui.gonzotech.condenser.ice_europan", eur, eur * 7));
            if (blu > 0) tooltip.add(Component.translatable("gui.gonzotech.condenser.ice_blue", blu, blu * 12));
            if (sup > 0) tooltip.add(Component.translatable("gui.gonzotech.condenser.ice_superdense", sup, sup * 29));

            if (reg == 0 && pck == 0 && eur == 0 && blu == 0 && sup == 0) {
                tooltip.add(Component.translatable("gui.gonzotech.condenser.ice_none"));
            }

            g.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }
}
