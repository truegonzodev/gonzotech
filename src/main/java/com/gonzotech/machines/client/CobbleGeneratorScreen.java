package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.CobbleGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Экран генератора булыжника: две шкалы (вода / GTU) — проявление текстур-заливок
 * под передним PNG. Меню и текстуры — плейсхолдеры (художник заменит).
 */
public class CobbleGeneratorScreen extends MachineScreen<CobbleGeneratorMenu> {

    public CobbleGeneratorScreen(CobbleGeneratorMenu menu, Inventory inv, Component title) {
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
        int barH = 52;

        // Шкала воды — 17 px шириной (по текстуре), GTU — стандартные 16.
        int watX = x + 26;
        int gtuX = x + 44;

        float water = (float) menu.water() / MachineDefs.COBBLE_WATER_CAPACITY;
        float gtu = (float) menu.gtu() / MachineDefs.toUnits(MachineDefs.COBBLE_GTU_CAPACITY);

        drawVBarTex(g, watX, barY, 17, barH, water, BAR_WATER);
        drawVBarTex(g, gtuX, barY, 16, barH, gtu, BAR_GTU);

        // Горизонтальный прогресс-бар «вскапывания» (0 → digTotal тиков;
        // для незеритовой кирки digTotal = 50): единая 68×16 текстура, НЕ тайлится.
        int digX = x + 63;
        int digY = y + 35;
        int digW = 68;
        int digH = 16;
        float dig = menu.digTotal() > 0 ? (float) menu.digProgress() / menu.digTotal() : 0f;
        drawHBarTexFull(g, digX, digY, digW, digH, dig, BAR_COBBLESTONE);

        if (inRect(mouseX, mouseY, watX, barY, 17, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.water", menu.water(), MachineDefs.COBBLE_WATER_CAPACITY)),
                mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, gtuX, barY, 16, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gtu", menu.gtu(), MachineDefs.toUnits(MachineDefs.COBBLE_GTU_CAPACITY))),
                mouseX, mouseY);
        }
    }
}
