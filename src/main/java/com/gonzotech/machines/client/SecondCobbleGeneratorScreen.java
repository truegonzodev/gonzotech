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
        return gui("second_cobble_generator_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("second_cobble_generator_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barH = 52;

        // Раскладка 1:1 с генератором булыжника I (без слота кирки).
        // Шкала воды — 17 px шириной (по текстуре), GTU — стандартные 16.
        int watX = x + 26;
        int gtuX = x + 44;
        int gtuCapacity = MachineDefs.toUnits(SecondTierDefs.COBBLE_GTU_CAPACITY);
        float water = (float) menu.water() / SecondTierDefs.COBBLE_WATER_CAPACITY;
        float gtu = (float) menu.gtu() / gtuCapacity;
        drawVBarTex(g, watX, barY, 17, barH, water, BAR_WATER);
        drawVBarTex(g, gtuX, barY, 16, barH, gtu, BAR_GTU);

        // Прогресс «вскапывания»: единая 68×16 текстура, НЕ тайлится;
        // tier 2 — с тултипом «Генерация: N%».
        int digX = x + 63;
        int digY = y + 35;
        int digW = 68;
        int digH = 16;
        float dig = menu.digTotal() > 0 ? (float) menu.digProgress() / menu.digTotal() : 0f;
        drawHBarTexFull(g, digX, digY, digW, digH, dig, BAR_COBBLESTONE);

        if (inRect(mouseX, mouseY, watX, barY, 17, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.water", menu.water(), SecondTierDefs.COBBLE_WATER_CAPACITY)),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, 16, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), gtuCapacity)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, digX, digY, digW, digH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.second_cobble_generator.generation_progress",
                    menu.digProgressPercent())),
                mouseX, mouseY);
        }
    }
}
