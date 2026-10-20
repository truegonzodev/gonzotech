package com.gonzotech.machines.client;

import com.gonzotech.core.text.GtUnits;
import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.PlotnickDefs;
import com.gonzotech.machines.menu.PlotnickButtonPayload;
import com.gonzotech.machines.menu.PlotnickMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Плотник (0.3.256) — GUI по замеру {@code plotnick_gui.png} автора:
 * окно 176×180, лист 512×512 со смещением (−128,−114), шкала GTU (7,17)
 * 18×54 (заливка 16×52), шесть кнопок 18×18 в (26/44, 18/36/54) —
 * пока рабочая только «Отметить» (выставить разметку сектора).
 */
public final class PlotnickScreen extends MachineScreen<PlotnickMenu> {

    /** Иконка кнопки — красный флажок автора (14×14). */
    private static final ResourceLocation BUTTON_ICON = gui("marker_flag.png");

    /** Прямоугольники кнопок 18×18 (макет автора): бурить, сеть, свернуться, сейсмо, отметить, динамит. */
    private static final int[][] BUTTON_RECTS = {
        {26, 18}, {44, 18}, {26, 36}, {44, 36}, {26, 54}, {44, 54}
    };

    public PlotnickScreen(PlotnickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PlotnickMenu.IMAGE_WIDTH;
        this.imageHeight = PlotnickMenu.IMAGE_HEIGHT;
    }

    @Override
    protected ResourceLocation backgroundTexture() {
        return machineGui("plotnick_gui_bg.png", "plotnick_gui_bg_neo.png");
    }

    @Override
    protected ResourceLocation foregroundTexture() {
        return machineGui("plotnick_gui.png", "plotnick_gui_neo.png");
    }

    @Override
    protected int texOffsetX() {
        return -128;
    }

    @Override
    protected int texOffsetY() {
        return -114;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // Макет автора без текстовых меток.
    }

    @Override
    protected void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        // Шкала GTU (7,17) 18×54, интерьер (8,18) 16×52 (замер автора).
        int capacity = MachineDefs.toUnits(PlotnickDefs.GTU_CAPACITY_MILLI);
        int gtuX = x + 8;
        int barY = y + 18;
        float gtu = capacity == 0 ? 0f : (float) menu.gtu() / capacity;
        drawVBarTex(g, gtuX, barY, 16, 52, gtu, BAR_GTU);

        // Шесть кнопок: флажок-маркер действия в центре каждой 18×18.
        for (int[] rect : BUTTON_RECTS) {
            g.blit(BUTTON_ICON, x + rect[0] + 2, y + rect[1] + 2, 0, 0, 14, 14, 14, 14);
        }

        if (inRect(mouseX, mouseY, gtuX, barY, 16, 52)) {
            g.renderComponentTooltip(font, List.of(
                GtUnits.gtuPair(menu.gtu(), capacity)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < BUTTON_RECTS.length; i++) {
                int bx = leftPos + BUTTON_RECTS[i][0];
                int by = topPos + BUTTON_RECTS[i][1];
                if (mouseX >= bx && mouseX < bx + 18 && mouseY >= by && mouseY < by + 18) {
                    PacketDistributor.sendToServer(
                        new PlotnickButtonPayload(menu.containerId, i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
