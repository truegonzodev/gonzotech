package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.BaseMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Общая база экранов машин — «слоёный пирог» под рисованный PNG-GUI.
 *
 * <h2>Слои (сверху вниз по Z)</h2>
 * <pre>
 *   Z3  Предметы + тултипы .............. ванила, самый верх
 *   Z2  Предметы в слотах / подсветка ... ванила
 *   Z1  PNG-ОКНО С ДЫРКАМИ ............... {@link #foregroundTexture()}  (маскирует шкалы)
 *   Z0  ПРОЯВЛЯЮЩИЕСЯ ШКАЛЫ ............. {@link #drawMachine} (текстура-заливка)
 *   Z-1 PNG-ФОН (под шкалами) ........... {@link #backgroundTexture()}
 *   Z-2 Затемнение мира ................. движок ({@link #renderBackground})
 * </pre>
 *
 * <h2>Стандарт меню (512×512 @ −128,−128)</h2>
 * Лист PNG — {@code 512×512}, блитится в {@code (leftPos−128, topPos−128)}, так
 * что интерактивное окно 176×166 (слоты/клики) оказывается в ЦЕНТРЕ листа (его
 * координаты 128,128). Рисунок может торчать за окно до 128 px во все стороны —
 * меню выглядит крупным, а геометрия слотов остаётся стандартной. По умолчанию
 * оба слоя ({@code *_GUI_BG.png} и {@code *_GUI.png}) — это красные рамки-эталоны,
 * пока художник не заменит их своим рисунком.
 *
 * <h2>Защита шкал альфа-маской</h2>
 * Из переднего PNG ({@link #foregroundTexture()}) строится {@link GuiMask}:
 * пиксели с альфой выше порога «закрыты», шкалы туда физически не рисуются
 * (клипуются по наибольшей дырке). Так заливка не может вылезти за рисунок —
 * она навечно погребена под непрозрачным оверлеем.
 */
public abstract class MachineScreen<T extends BaseMachineMenu> extends AbstractContainerScreen<T> {

    /** Каталог текстур GUI машин. */
    public static final String GUI_DIR = "textures/gui/";

    protected static ResourceLocation gui(String file) {
        return ResourceLocation.fromNamespaceAndPath("gonzotech", GUI_DIR + file);
    }

    // Пер-ресурсные шахматные текстуры-заливки шкал (общие, но НЕ универсальные).
    protected static final ResourceLocation BAR_GTH = gui("bar_gth.png");
    protected static final ResourceLocation BAR_BURNUP = gui("bar_burnup.png");
    protected static final ResourceLocation BAR_SMELTING = gui("bar_smelting.png");
    protected static final ResourceLocation BAR_WATER = gui("bar_water.png");
    protected static final ResourceLocation BAR_STEAM = gui("bar_steam.png");
    protected static final ResourceLocation BAR_GTU = gui("bar_gtu.png");

    private GuiMask mask = GuiMask.forTexture(null, 0, 0);

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
        this.mask = GuiMask.forTexture(foregroundTexture(), texOffsetX(), texOffsetY());
    }

    // ─────────────────── Настройки PNG-листа (переопределяемые) ───────────────────

    /** Задний PNG (Z-1), под шкалами. По умолчанию нет — переопредели в наследнике. */
    protected ResourceLocation backgroundTexture() {
        return null;
    }

    /** Передний PNG с дырками (Z1), над шкалами. По умолчанию нет. */
    protected ResourceLocation foregroundTexture() {
        return null;
    }

    /** Размер квадратного листа PNG. Стандарт — 512. */
    protected int sheetSize() {
        return 512;
    }

    /** Смещение блита листа относительно угла окна. Стандарт — −128. */
    protected int texOffsetX() {
        return -128;
    }

    protected int texOffsetY() {
        return -128;
    }

    // ─────────────────── Рендер ───────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        this.renderTooltip(g, mouseX, mouseY);
    }

    /** Убираем ВЕСЬ текст-подписи (название машины, «Инвентарь»). */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // намеренно пусто — всё оформление приходит из PNG
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Z-1: задний PNG-фон.
        blitSheet(g, backgroundTexture(), x, y);

        // Z0: проявляющиеся шкалы (только заливка).
        drawMachine(g, x, y, mouseX, mouseY);

        // Z1: передний PNG с дырками — маскирует лишнее у шкал.
        blitSheet(g, foregroundTexture(), x, y);
    }

    /** Блит всего листа PNG в угол окна с учётом размера листа и смещения. */
    private void blitSheet(GuiGraphics g, ResourceLocation tex, int x, int y) {
        if (tex == null) return;
        int s = sheetSize();
        g.blit(RenderType::guiTextured, tex, x + texOffsetX(), y + texOffsetY(),
            0f, 0f, s, s, s, s);
    }

    // ─────────────────── Шкалы: «проявление» текстуры (без растяжения) ───────────────────

    /**
     * Вертикальная шкала: открывает нижние {@code fraction·h} пикселей затайленной
     * 16×16 текстуры (растёт снизу вверх). Клипуется по дырке в переднем PNG —
     * заливка не может залезть под непрозрачный рисунок.
     */
    protected void drawVBarTex(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
        int[] clip = clipRect(x, y, w, h);
        if (clip == null) return;
        int filled = Math.round(clamp01(fraction) * h);
        if (filled <= 0) return;
        int top = Math.max(clip[1], y + h - filled);
        int scLeft = clip[0], scRight = clip[2], scBottom = clip[3];
        if (top >= scBottom) return;
        g.enableScissor(scLeft, top, scRight, scBottom);
        // тайлим 16×16, привязка к НИЗУ шкалы → рост без сдвига паттерна
        for (int py = y + h - 16; py > y - 16; py -= 16) {
            for (int px = x; px < x + w; px += 16) {
                g.blit(RenderType::guiTextured, tex, px, py, 0f, 0f, 16, 16, 16, 16);
            }
        }
        g.disableScissor();
    }

    /**
     * Горизонтальная шкала (прогресс): открывает левые {@code fraction·w} пикселей
     * (растёт слева направо). Тоже клипуется по дырке PNG.
     */
    protected void drawHBarTex(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
        int[] clip = clipRect(x, y, w, h);
        if (clip == null) return;
        int filled = Math.round(clamp01(fraction) * w);
        if (filled <= 0) return;
        int right = Math.min(clip[2], x + filled);
        int scLeft = clip[0], scTop = clip[1], scBottom = clip[3];
        if (right <= scLeft) return;
        g.enableScissor(scLeft, scTop, right, scBottom);
        for (int py = y; py < y + h; py += 16) {
            for (int px = x; px < x + w; px += 16) {
                g.blit(RenderType::guiTextured, tex, px, py, 0f, 0f, 16, 16, 16, 16);
            }
        }
        g.disableScissor();
    }

    /**
     * Пересечение footprint шкалы с «дыркой» в переднем PNG, в ЭКРАННЫХ координатах.
     * @return {@code [left,top,right,bottom]} или {@code null}, если дырки нет.
     */
    private int[] clipRect(int x, int y, int w, int h) {
        int wx = x - this.leftPos;
        int wy = y - this.topPos;
        int[] open = mask.openSubRect(wx, wy, w, h);
        if (open == null) return null;
        return new int[]{
            this.leftPos + open[0], this.topPos + open[1],
            this.leftPos + open[2], this.topPos + open[3]
        };
    }

    /** Наведение мыши на прямоугольник (для тултипов шкал). */
    protected boolean inRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** Специфичная для машины отрисовка шкал (слой Z0). */
    protected abstract void drawMachine(GuiGraphics g, int x, int y, int mouseX, int mouseY);

    protected static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }
}
