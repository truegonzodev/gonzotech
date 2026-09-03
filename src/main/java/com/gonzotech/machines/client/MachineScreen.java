package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.BaseMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Общая база экранов машин паровой ветки.
 * <p>
 * БАЗА ПОД PNG-GUI. Экран может работать в двух режимах:
 * <ol>
 *   <li><b>Процедурный</b> (по умолчанию): фон, рамки слотов и «дорожки» шкал
 *       рисуются {@code fill}-прямоугольниками. Заведомо рабочий GUI без ассетов.</li>
 *   <li><b>Текстурный</b>: как только художник кладёт PNG-окно по пути
 *       {@code assets/gonzotech/textures/gui/&lt;name&gt;.png} (лист 256×256, само
 *       окно 176×166), наследник переопределяет {@link #backgroundTexture()} —
 *       и фон/рамки берутся из PNG. Динамические шкалы всё равно дорисовываются
 *       поверх ({@link #drawVBar}/{@link #drawHBar}), поэтому переход на рисованные
 *       меню не ломает логику.</li>
 * </ol>
 * Ничего в отрисовке не «прибито гвоздями»: чтобы включить PNG, достаточно вернуть
 * {@link ResourceLocation} из {@link #backgroundTexture()}.
 */
public abstract class MachineScreen<T extends BaseMachineMenu> extends AbstractContainerScreen<T> {

    /** Каталог текстур GUI машин. Художник кладёт PNG сюда. */
    public static final String GUI_DIR = "textures/gui/";
    /** Размер листа PNG-окна (стандарт GUI). */
    protected static final int TEX_SHEET = 256;

    // Палитра «тёмный металл» под индустриальный тон Gonzo Tech (процедурный режим).
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

    /**
     * Текстура-фон окна. По умолчанию {@code null} → процедурный режим.
     * Переопределите в наследнике, когда появится нарисованный PNG:
     * <pre>return ResourceLocation.fromNamespaceAndPath("gonzotech", GUI_DIR + "firebox.png");</pre>
     */
    protected ResourceLocation backgroundTexture() {
        return null;
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

        ResourceLocation tex = backgroundTexture();
        if (tex != null) {
            // Текстурный режим: всё окно (фон + рамки слотов) из PNG.
            g.blit(RenderType::guiTextured, tex, x, y, 0f, 0f,
                imageWidth, imageHeight, TEX_SHEET, TEX_SHEET);
        } else {
            drawProceduralChrome(g, x, y);
        }

        // Динамические элементы (шкалы/слоты машины) — всегда поверх фона.
        drawMachine(g, x, y, mouseX, mouseY);
    }

    /** Процедурный фон + рамки слотов инвентаря игрока. */
    private void drawProceduralChrome(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        g.fill(x, y, x + imageWidth, y + 1, PANEL_LIGHT);
        g.fill(x, y, x + 1, y + imageHeight, PANEL_LIGHT);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, PANEL_DARK);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, PANEL_DARK);

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
    }

    /** Рисуется ли фон процедурно (нет PNG). Наследники используют для рамок слотов. */
    protected boolean isProcedural() {
        return backgroundTexture() == null;
    }

    /** Рамка одного слота 18×18 (внутри клетка 16×16). Только в процедурном режиме. */
    protected void drawSlot(GuiGraphics g, int x, int y) {
        if (!isProcedural()) return;
        g.fill(x, y, x + 18, y + 18, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
    }

    /** Вертикальная шкала-заполнение снизу вверх. */
    protected void drawVBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        if (isProcedural()) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_EDGE);
            g.fill(x, y, x + w, y + h, COL_TRACK);
        }
        int filled = Math.round(Mathf.clamp01(fraction) * h);
        if (filled > 0) {
            g.fill(x, y + (h - filled), x + w, y + h, color);
        }
    }

    /** Горизонтальный прогресс-бар слева направо. */
    protected void drawHBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        if (isProcedural()) {
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SLOT_EDGE);
            g.fill(x, y, x + w, y + h, COL_TRACK);
        }
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
