package com.gonzotech.machines.client;

import com.gonzotech.machines.menu.BaseMachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Общая база экранов машин паровой ветки — «слоёный пирог» под будущий PNG-GUI.
 *
 * <h2>Как устроен рендер (слои снизу вверх)</h2>
 * <ol>
 *   <li><b>Мир</b> — затемняется движком ({@link #renderBackground}). Мы его не трогаем.</li>
 *   <li><b>Фон-окно (PNG)</b> — {@link #backgroundTexture()}. Пока это КРАСНАЯ
 *       РАМКА-эталон ({@code textures/gui/reference.png}, лист 256×256, само окно
 *       176×166 в левом-верхнем углу листа). Она почти прозрачная: видно и мир, и
 *       ванильный инвентарь игрока. По ней ты поймёшь, куда позиционировать свой
 *       рисованный PNG, чтобы он бесшовно лёг на серый инвентарь игрока.</li>
 *   <li><b>Слоты-контейнеры</b> — их рисует ванила поверх фона (подсветку при
 *       наведении и т.п.). Рамки слотов ИНВЕНТАРЯ игрока мы НЕ рисуем — они
 *       придут из твоего PNG (или из ванильного фона).</li>
 *   <li><b>«Парящие» элементы машины</b> — {@link #drawMachine}: рамки машинных
 *       слотов и динамические шкалы/стрелки. Рисуются всегда, поверх фона.</li>
 *   <li><b>Предметы в слотах + тултипы</b> — ванила, самый верх.</li>
 * </ol>
 *
 * <h2>Как подставить свой PNG</h2>
 * Нарисуй окно 176×166 в левом-верхнем углу листа 256×256, сохрани в
 * {@code assets/gonzotech/textures/gui/&lt;имя&gt;.png} и переопредели в наследнике:
 * <pre>
 * &#64;Override protected ResourceLocation backgroundTexture() {
 *     return ResourceLocation.fromNamespaceAndPath("gonzotech", GUI_DIR + "firebox.png");
 * }
 * </pre>
 * Больше ничего менять не нужно: логика, слоты и шкалы уже позиционированы.
 * (Если PNG сам рисует машинные слоты — можешь убрать их процедурную отрисовку,
 * см. {@link #drawFrames()}.)
 */
public abstract class MachineScreen<T extends BaseMachineMenu> extends AbstractContainerScreen<T> {

    /** Каталог текстур GUI машин. Художник кладёт PNG сюда. */
    public static final String GUI_DIR = "textures/gui/";
    /** Размер листа PNG-окна (стандарт GUI). */
    protected static final int TEX_SHEET = 256;

    /** Красная рамка-эталон 256×256 — временный ориентир, пока нет рисованного PNG. */
    protected static final ResourceLocation REFERENCE =
        ResourceLocation.fromNamespaceAndPath("gonzotech", GUI_DIR + "reference.png");

    // Цвета «парящих» шкал (сам синий фон-панель удалён насовсем).
    protected static final int COL_GTH = 0xFFE0562A;    // тепло — оранжево-красный
    protected static final int COL_STEAM = 0xFFB9C6D6;  // пар — светло-серый
    protected static final int COL_GTU = 0xFF3FB6E6;    // электричество — голубой
    protected static final int COL_WATER = 0xFF3B6BE0;  // вода — синий
    protected static final int COL_TRACK = 0xFF0B0C0E;  // фон шкалы
    protected static final int SLOT_EDGE = 0xFF54606E;  // рамка «парящего» слота
    protected static final int SLOT_BG = 0xFF101215;    // нутро «парящего» слота

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
     * Фон-окно. По умолчанию — красная рамка-эталон {@link #REFERENCE}.
     * Переопредели, вернув свой рисованный PNG, когда он будет готов.
     */
    protected ResourceLocation backgroundTexture() {
        return REFERENCE;
    }

    /**
     * Рисовать ли процедурные рамки машинных слотов и «дорожки» шкал.
     * Верни {@code false}, если всё это уже нарисовано прямо в твоём PNG.
     */
    protected boolean drawFrames() {
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g, mouseX, mouseY, partial);
        super.render(g, mouseX, mouseY, partial);
        this.renderTooltip(g, mouseX, mouseY);
    }

    /** Убираем ВЕСЬ текст-подписи («Топка», «Инвентарь»). */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // намеренно пусто
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        // Слой «фон-окно»: PNG (по умолчанию — красная рамка-эталон).
        ResourceLocation tex = backgroundTexture();
        if (tex != null) {
            g.blit(RenderType::guiTextured, tex, x, y, 0f, 0f,
                imageWidth, imageHeight, TEX_SHEET, TEX_SHEET);
        }

        // Слой «парящие элементы машины»: рамки машинных слотов + шкалы.
        drawMachine(g, x, y, mouseX, mouseY);
    }

    /** Рамка одного «парящего» слота 18×18 (внутри клетка 16×16). */
    protected void drawSlot(GuiGraphics g, int x, int y) {
        if (!drawFrames()) return;
        g.fill(x, y, x + 18, y + 18, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_BG);
    }

    /** Вертикальная шкала-заполнение снизу вверх. */
    protected void drawVBar(GuiGraphics g, int x, int y, int w, int h, float fraction, int color) {
        if (drawFrames()) {
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
        if (drawFrames()) {
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
