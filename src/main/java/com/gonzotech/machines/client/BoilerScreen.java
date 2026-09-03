package com.gonzotech.machines.client;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.menu.BoilerMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Экран котла: слоты ведра, три шкалы (GTH/вода/пар) и индикатор цепочки. */
public class BoilerScreen extends MachineScreen<BoilerMenu> {

    public BoilerScreen(BoilerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        drawSlot(g, x + 44 - 1, y + 35 - 1);  // ведро воды (вход)
        drawSlot(g, x + 44 - 1, y + 57 - 1);  // пустое ведро (выход)

        int barY = y + 17;
        int barW = 10;
        int barH = 52;

        int gthX = x + 76;
        int watX = x + 100;
        int steX = x + 124;

        float gth = (float) menu.gth() / MachineDefs.GTH_CAPACITY;
        float water = (float) menu.water() / MachineDefs.WATER_CAPACITY;
        float steam = (float) menu.steam() / MachineDefs.STEAM_CAPACITY;

        drawVBar(g, gthX, barY, barW, barH, gth, COL_GTH);
        drawVBar(g, watX, barY, barW, barH, water, COL_WATER);
        drawVBar(g, steX, barY, barW, barH, steam, COL_STEAM);

        // Индикатор собранной цепочки (нужна топка рядом).
        Component chain = menu.chainOk()
            ? Component.translatable("gui.gonzotech.chain_ok").withStyle(ChatFormatting.GREEN)
            : Component.translatable("gui.gonzotech.chain_missing_firebox").withStyle(ChatFormatting.RED);
        g.drawString(this.font, chain, x + 8, y + 72, TEXT, false);

        if (inRect(mouseX, mouseY, gthX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.gth", menu.gth(), MachineDefs.GTH_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, watX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.water", menu.water(), MachineDefs.WATER_CAPACITY)), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, steX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                Component.translatable("gui.gonzotech.steam", menu.steam(), MachineDefs.STEAM_CAPACITY)), mouseX, mouseY);
        }
    }
}
