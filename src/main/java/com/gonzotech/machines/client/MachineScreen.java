package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.BaseMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Общая база экранов машин паровой ветки. Рисует фон процедурно
 * ({@code fill}-прямоугольники + рамки слотов), без завязки на пиксель-в-пиксель
 * PNG — это соответствует стилю проекта (см. ResonanceScreen) и даёт простые,
 * заведомо рабочие GUI, поверх которых художник позже нарисует текстуры.
 */
public abstract class MachineScreen<T extends BaseMachineMenu> extends AbstractContainerScreen<T> {

    // Палитра «тёмный металл» под индустриальный тон Gonzo Tech.
    protected static final int PANEL_BG = 0xFF2B2F36;
    protected static final int PANEL_LIGHT = 0xFF3C424C;
    protected static final int PANEL_DARK = 0xFF15171B;
    protected static final int SLOT_BG = 0xFF101215;
    protected static final int SLOT_EDGE = 0xFF54606E;
    protected static final int TEXT = 0xFFE6E9EF;

    // Цвета ресурсов.
    protected static final int COL_GTH = 0xFFE0562A;    // тепло — оранжево-красный
    protected static final int COL_STEAM = 0xFFB9C6D6;  // пар — светло-серый
    protected static final int COL_GTU = 0xFF3FB6E6;    // электричество — голубой
    protected static final int COL_WATER = 0xFF3B6BE0;  // вода — синий
    protected static final int COL_TRACK = 0xFF0B0C0E;  // фон шкалы

    protected MachineScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Основная панель + рамка.
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        g.fill(x, y, x + imageWidth, y + 1, PANEL_LIGHT);
        g.fill(x, y, x + 1, y + imageHeight, PANEL_LIGHT);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_DARK);

        // Слоты инвентаря игрока (3×9 + хотбар).
        int invX = x + 8;
        int invY = y + 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, invX + col * 18, invY + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, invX + col * 18, invY + 58);
        }

        drawMachine(g, x, y, mouseX, mouseY);
    }

    /** Рамка одного слота 18×18 (внутри клетка 16×16). */
    protected void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
    }

    /** Вертикальная шкала-заполнение снизу вверх. */
    protected void drawVBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_EDGE);
        g.fill(x, y, x + w, y + h, COL_TRACK);
        int filled = Math.round(Mathf.clamp01(fraction) * h);
        if (filled > 0) {
            g.fill(x, y + (h - filled), x + w, y + h, color);
        }
    }

    /** Горизонтальный прогресс-бар слева направо. */
    protected void drawHBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_EDGE);
        g.fill(x, y, x + w, y + h, COL_TRACK);
        int filled = Math.round(Mathf.clamp01(fraction) * w);
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
        }
    }

    /** Наведение мыши на прямоугольник. */
    protected boolean inRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Специфичная для машины отрисовка (слоты машины, шкалы). */
    protected abstract void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY);

    /** Мелкий матан без завязки на внешние классы. */
    protected static final class Mathf {
        private Mathf() {
        }

        static float clamp01(float v) {
            return v < 0 ? 0 : (v > 1 ? 1 : v);
        }
    }
}
