package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.BlastFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Экран доменной печи (0.3.65). Лист-плейсхолдер пока пустой (автор зальёт
 * рисунок): шкалы рисуются кодом по стандартной сетке листа 512×512 @ −128.
 * Раскладка (листовые координаты автора → оконные −128):
 * GTH-шкала (136,145..181) → (8,17) 16×52; burnout (208,163) → (80,35) 16×16;
 * пять топливных слотов (172..244,181) → (44..116, 53).
 */
public final class BlastFurnaceScreen extends MachineScreen<BlastFurnaceMenu> {

    public BlastFurnaceScreen(BlastFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected net.minecraft.resources.ResourceLocation backgroundTexture() {
        return gui("blast_furnace_gui_bg.png");
    }

    @Override
    protected net.minecraft.resources.ResourceLocation foregroundTexture() {
        return gui("blast_furnace_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics graphics, int x, int y, int mouseX, int mouseY) {
        // Burnout: полный бар тает сверху вниз, как у топки/ядерной топки.
        int burnX = x + 80;
        int burnY = y + 35;
        float lit = menu.litDuration() > 0 ? (float) menu.litTime() / menu.litDuration() : 0.0F;
        // Без тултипа — машина тир 1 (0.3.70).
        drawVBarTex(graphics, burnX, burnY, 16, 16, lit, BAR_BURNUP);

        // Шкала GTH: три ячейки (8, 17..69), растёт снизу вверх.
        int barX = x + 8;
        int barY = y + 17;
        int barW = 16;
        int barH = 52;
        // Обе величины в милли (0.3.72): раньше числитель был в милли, а
        // знаменатель в единицах — дробь зажималась в 100% за первый тик.
        float gth = (float) menu.gth() / (float) MachineDefs.BLAST_FURNACE_GTH_CAPACITY;
        drawVBarTex(graphics, barX, barY, barW, barH, gth, BAR_GTH);
        if (inRect(mouseX, mouseY, barX, barY, barW, barH)) {
            graphics.renderComponentTooltip(this.font, List.of(
                GtUnits.gthPair(menu.gth() / 1000, MachineDefs.BLAST_FURNACE_GTH_CAPACITY / 1_000)), mouseX, mouseY);
        }
    }
}
