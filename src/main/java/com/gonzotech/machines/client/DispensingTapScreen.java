package com.gonzotech.machines.client;

import com.gonzotech.machines.block.entity.DispensingTapBlockEntity;
import com.gonzotech.machines.menu.DispensingTapMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/**
 * Экран разливного крана:
 * раскладка точь-в-точь как у помпы (pump_gui_bg.png), две шкалы:
 * дистиллят (256 mB, bar_distillate.png) и сусло (1024 mB, bar_wort.png).
 */
public class DispensingTapScreen extends MachineScreen<DispensingTapMenu> {

    private static final ResourceLocation BAR_DISTILLATE = gui("bar_distillate.png");
    private static final ResourceLocation BAR_WORT = gui("bar_wort.png");

    public DispensingTapScreen(DispensingTapMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return gui("pump_gui_bg.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return gui("pump_gui.png");
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int barY = y + 17;
        int barW = 16;
        int barH = 52;

        int distX = x + 8;
        int wortX = x + 152;

        float distRatio = (float) menu.distillate() / DispensingTapBlockEntity.DISTILLATE_CAPACITY;
        float wortRatio = (float) menu.wort() / DispensingTapBlockEntity.WORT_CAPACITY;

        drawVBarTex(g, distX, barY, barW, barH, distRatio, BAR_DISTILLATE);
        drawVBarTex(g, wortX, barY, barW, barH, wortRatio, BAR_WORT);

        if (inRect(mouseX, mouseY, distX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                    Component.literal("§bДистиллят: " + menu.distillate() + " / " + DispensingTapBlockEntity.DISTILLATE_CAPACITY + " mB")
            ), mouseX, mouseY);
        } else if (inRect(mouseX, mouseY, wortX, barY, barW, barH)) {
            g.renderComponentTooltip(this.font, List.of(
                    Component.literal("§6Сусло: " + menu.wort() + " / " + DispensingTapBlockEntity.WORT_CAPACITY + " mB")
            ), mouseX, mouseY);
        }
    }
}
