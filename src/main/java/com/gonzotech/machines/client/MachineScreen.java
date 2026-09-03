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
 * Слои Z1/Z0/Z-1 рисуются в {@link #renderBg} ДО того, как ванила отрисует
 * предметы, поэтому предметы (Z2) и тултипы (Z3) автоматически лягут выше.
 * <p>
 * <b>Идея с дырками.</b> Всё «железо» окна (рамки слотов, обводки, подписи,
 * фон) рисуешь ты в PNG. В местах шкал в переднем PNG (Z1) делаешь ПРОЗРАЧНЫЕ
 * дырки. Под ними (Z0) мы «проявляем» текстуру-заливку снизу-вверх (или
 * слева-направо для прогресса) — не растягивая, а открывая всё большую часть
 * заранее затайленной 16×16 текстуры (см. {@link #drawVBarTex}). Так как передний
 * PNG лежит ВЫШЕ шкалы, ей не нужно идеально влезать в дырку по ширине — лишнее
 * просто перекроется рисунком. Все три PNG (Z1/Z0/Z-1) — 32-битные ARGB с альфой.
 *
 * <h2>Размер листа и «большое меню» (512×512 @ −128,−128)</h2>
 * Зона взаимодействия (слоты/клики) остаётся стандартной 176×166 — её геометрию
 * менять нельзя, иначе поедут слоты и попадание мышью. Но сам РИСУНОК может быть
 * больше и вылезать за окно: переопредели {@link #sheetSize()} = 512 и
 * {@link #texOffsetX()}/{@link #texOffsetY()} = −128. Тогда лист 512×512 блитится
 * в точку {@code (leftPos−128, topPos−128)}, а интерактивное окно 176×166
 * оказывается ровно в центре листа (в его коорд. 128,128). Так меню выглядит
 * крупнее (рамка/декор торчат наружу до 128 px во все стороны), а клики и слоты
 * работают как обычно. Это оптимальный маршрут: увеличивать физический размер
 * слотов (масштаб предметов) ваниль без костылей не умеет — click-detection ломается.
 */
public abstract class MachineScreen<T extends BaseMachineMenu> extends AbstractContainerScreen<T> {

    /** Каталог текстур GUI машин. */
    public static final String GUI_DIR = "textures/gui/";

    private static ResourceLocation gui(String file) {
        return ResourceLocation.fromNamespaceAndPath("gonzotech", GUI_DIR + file);
    }

    /** Красные рамки-эталоны (для позиционирования, по умолчанию не показываются). */
    protected static final ResourceLocation REFERENCE_256 = gui("reference.png");
    protected static final ResourceLocation REFERENCE_512 = gui("reference_512.png");

    // Отладочные шахматные текстуры-заливки шкал (проявление, не растяжение).
    protected static final ResourceLocation BAR_HEAT = gui("bar_debug_a.png");   // GTH / пламя / переплавка
    protected static final ResourceLocation BAR_STEAM = gui("bar_debug_b.png");  // пар
    protected static final ResourceLocation BAR_FLUID = gui("bar_debug_c.png");  // вода / GTU

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

    /** Задний PNG (Z-1), под шкалами. По умолчанию нет. */
    protected ResourceLocation backgroundTexture() {
        return null;
    }

    /** Передний PNG с дырками (Z1), над шкалами. По умолчанию нет. */
    protected ResourceLocation foregroundTexture() {
        return null;
    }

    /** Размер квадратного листа PNG (256 или 512). */
    protected int sheetSize() {
        return 256;
    }

    /** Смещение блита листа относительно угла окна (для 512 обычно −128). */
    protected int texOffsetX() {
        return 0;
    }

    protected int texOffsetY() {
        return 0;
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

        // Z0: проявляющиеся шкалы (только заливка, без фона/обводки).
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
     * Вертикальная шкала: открывает нижние {@code fraction·h} пикселей заранее
     * затайленной 16×16 текстуры (растёт снизу вверх, тайлы стыкуются бесшовно).
     */
    protected void drawVBarTex(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
        int filled = Math.round(clamp01(fraction) * h);
        if (filled <= 0) return;
        int top = y + h - filled;
        g.enableScissor(x, top, x + w, y + h);
        // Тайлим 16×16, привязка к НИЗУ шкалы → рост снизу без сдвига паттерна.
        for (int py = y + h - 16; py > y - 16; py -= 16) {
            for (int px = x; px < x + w; px += 16) {
                g.blit(RenderType::guiTextured, tex, px, py, 0f, 0f, 16, 16, 16, 16);
            }
        }
        g.disableScissor();
    }

    /**
     * Горизонтальная шкала (прогресс): открывает левые {@code fraction·w} пикселей
     * затайленной текстуры (растёт слева направо).
     */
    protected void drawHBarTex(GuiGraphics g, int x, int y, int w, int h, float fraction, ResourceLocation tex) {
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
