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
 * меню выглядит крупным, а геометрия слотов остаётся стандартной. Слои
 * {@code *_gui_bg.png} и {@code *_gui.png} берутся из ресурсов конкретной машины;
 * цвет/оформление не задаются этим классом.
 *
 * Шкалы ограничены только своей заданной в коде областью и долей заполнения.
 * PNG не читается на CPU: непрозрачные части переднего слоя визуально закрывают заливку.
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
    protected static final ResourceLocation BAR_CHEMICAL = gui("bar_chemical.png");
    protected static final ResourceLocation BAR_WATER = gui("bar_water.png");
    protected static final ResourceLocation BAR_HOT_WATER = gui("bar_hot_water.png");
    protected static final ResourceLocation BAR_STEAM = gui("bar_steam.png");
    protected static final ResourceLocation BAR_GTU = gui("bar_gtu.png");
    protected static final ResourceLocation BAR_COBBLESTONE = gui("bar_cobblestone.png");
    protected static final ResourceLocation BAR_MASH = gui("bar_mash.png");
    protected static final ResourceLocation BAR_WORT = gui("bar_wort.png");
    protected static final ResourceLocation BAR_DISTILLATE = gui("bar_distillate.png");
    protected static final ResourceLocation BAR_RECTIFICATE = gui("bar_rectificate.png");
    protected static final ResourceLocation BAR_POISON = gui("bar_poison.png");
    protected static final ResourceLocation BAR_SULFURIC_ACID = gui("bar_sulfuric_acid.png");
    protected static final ResourceLocation BAR_ETHYLENE = gui("bar_ethylene.png");
    protected static final ResourceLocation BAR_AMINOBLAZEETHANOL = gui("bar_aminoblazeethanol.png");
    protected static final ResourceLocation BAR_FORMALDEHYDE = gui("bar_formaldehyde.png");

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
     * 16×16 текстуры (растёт снизу вверх). Scissor ограничивает тайлы областью заполнения.
     */
    protected void drawVBarTex(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
        if (w <= 0 || h <= 0) return;
        int filled = Math.round(clamp01(fraction) * h);
        if (filled <= 0) return;
        g.enableScissor(x, y + h - filled, x + w, y + h);
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
     * (растёт слева направо). Ограничена областью заполнения.
     */
    protected void drawHBarTex(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
        if (w <= 0 || h <= 0) return;
        int filled = Math.round(clamp01(fraction) * w);
        if (filled <= 0) return;
        g.enableScissor(x, y, x + filled, y + h);
        for (int py = y; py < y + h; py += 16) {
            for (int px = x; px < x + w; px += 16) {
                g.blit(RenderType::guiTextured, tex, px, py, 0f, 0f, 16, 16, 16, 16);
            }
        }
        g.disableScissor();
    }

    /**
     * Горизонтальная шкала (прогресс): открывает правые {@code fraction·w} пикселей
     * (растёт справа налево). Ограничена областью заполнения.
     */
    protected void drawHBarTexRightToLeft(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
        if (w <= 0 || h <= 0) return;
        int filled = Math.round(clamp01(fraction) * w);
        if (filled <= 0) return;
        g.enableScissor(x + w - filled, y, x + w, y + h);
        for (int py = y; py < y + h; py += 16) {
            for (int px = x; px < x + w; px += 16) {
                g.blit(RenderType::guiTextured, tex, px, py, 0f, 0f, 16, 16, 16, 16);
            }
        }
        g.disableScissor();
    }

    /**
     * Горизонтальная шкала из ЕДИНОЙ (не тайлящейся) текстуры ровно w×h:
     * открывает левые {@code fraction·w} пикселей (растёт слева направо),
     * как {@link #drawHBarTex}, но текстура блитится одним куском, а не
     * тайлом 16×16. Ограничена областью заполнения.
     */
    protected void drawHBarTexFull(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
        if (w <= 0 || h <= 0) return;
        int filled = Math.round(clamp01(fraction) * w);
        if (filled <= 0) return;
        g.enableScissor(x, y, x + filled, y + h);
        g.blit(RenderType::guiTextured, tex, x, y, 0f, 0f, w, h, w, h);
        g.disableScissor();
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
