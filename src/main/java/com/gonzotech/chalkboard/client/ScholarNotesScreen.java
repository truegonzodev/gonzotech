package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.network.NotesNetwork;
import com.gonzotech.chalkboard.notes.NoteIllustration;
import com.gonzotech.chalkboard.notes.NoteIllustrationKind;
import com.gonzotech.chalkboard.notes.NotesState;
import com.gonzotech.chalkboard.notes.ScholarChapter;
import com.gonzotech.chalkboard.notes.ScholarNotesContent;
import com.gonzotech.chalkboard.notes.ScholarPage;
import com.gonzotech.chalkboard.notes.StructureBlock;
import com.gonzotech.chalkboard.notes.StructureModel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * GUI-буклет «Заметки учёного» (вариант 2): контейнер (фон панели и вкладки) —
 * рисованные PNG; весь текст рисуется шрифтом ради локализации; иллюстрации
 * страниц — ШАБЛОНЫ ({@link com.gonzotech.chalkboard.notes.NoteIllustrationKind}),
 * а не личные PNG на страницу.
 *
 * <p>Две книги: <b>линейная</b> — главы-эпохи (I/III/IV/V) делят одну историю
 * страниц 1..X, стрелки листают насквозь все открытые главы; <b>отдельная</b> —
 * «Познание мира» (своя история 1..X по действиям игрока): вкладка отделена от
 * четырёх эпох, стрелки и счётчик страниц не пересекаются между книгами.
 *
 * <p>Контейнер = панель-фон ПО ГЛАВЕ ({@code notes_bg_era1..era5.png}, выбирается
 * активной эрой — это и есть бумага страницы) + {@code notes_unlocked_tab.png}
 * + {@code notes_locked_tab.png}. Иконка-предмет главы, тинт активной вкладки,
 * «?» на закрытой — это РЕНДЕР поверх PNG. Вкладки рисуются ПЕРВЫМИ
 * (уходят под панель).
 *
 * <p>Иллюстрации — ШАБЛОНЫ ({@link com.gonzotech.chalkboard.notes.NoteIllustrationKind}):
 * прозрачный PNG-оверлей (сетки/панели/стрелки) поверх фона главы + предметы,
 * которые GUI рендерит в слоты как на витрине (hover-тултипы) + локализуемые
 * подписи «Создание»/«Структура» шрифтом. Личных PNG на страницу больше нет.
 *
 * <p>Раскладка ТЕКСТА задаётся per-page ({@link ScholarPage.Layout}):
 * TEXT_FULL / TEXT_LEFT (правая половина под иллюстрацию).
 * Заголовки — жирные. Длинный текст мотается колёсиком.
 *
 * <p>Разблокировка — {@link com.gonzotech.chalkboard.notes.ScholarUnlock}; состояние
 * (наигранное время + «Открытия» + флаги «Познания мира») берётся из
 * {@link NotesNetwork#CLIENT_DATA} и собирается в {@link NotesState}.
 */
public class ScholarNotesScreen extends Screen {

    private static final int FRAME_W = 256;
    private static final int FRAME_H = 200;

    private static final int TAB_W = 28;
    private static final int TAB_H = 26;
    private static final int TAB_GAP = 4;
    /** Разрыв между вкладками линейных эпох и отдельной книгой «Познание мира». */
    private static final int SIDE_BOOK_GAP = 12;

    private static final ResourceLocation TEX_TAB_UNLOCKED =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/notes/notes_unlocked_tab.png");
    private static final ResourceLocation TEX_TAB_LOCKED =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/notes/notes_locked_tab.png");

    private static final int INK = 0xFF2B1D0E;
    private static final int INK_FAINT = 0xFF6B5433;
    private static final int TAB_ACTIVE_TINT = 0x66FFFFFF;
    private static final int SLOT_TINT = 0x33000000;

    private int leftPos;
    private int topPos;
    private int pageIndex;
    private int scroll;

    private final List<int[]> tabRects = new ArrayList<>();
    private int[] prevRect;
    private int[] nextRect;

    private Component tooltipComponent;
    private ItemStack tooltipStack;
    private int tooltipX, tooltipY;

    private int bodyViewTop, bodyViewBottom, bodyContentHeight;

    // ── Структуры (подстраницы: «Сборка» или слои «вид сверху») ──
    /** Текущая подстраница структуры (0 = нижний слой/«Сборка», вверх). */
    private int structureSubpage;
    /** Прямоугольники навигации подстраниц (экран, пересобираются каждый кадр). */
    private int[] structPrevRect;
    private int[] structNextRect;

    public ScholarNotesScreen() {
        super(Component.translatable("gui.gonzotech.notes.title"));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - FRAME_W) / 2;
        this.topPos = (this.height - FRAME_H) / 2;
        this.pageIndex = ScholarNotesContent.firstUnlockedIndex(state());
        this.scroll = 0;
        this.structureSubpage = 0;
        // Сбрасываем кэш размеров: если автор перерисовал PNG в другом разрешении
        // и сделал перезагрузку ресурсов (F3+T), подхватим новый размер.
        PNG_SIZE_CACHE.clear();
    }

    /** Актуальное состояние гейтинга страниц из последнего серверного ответа. */
    private NotesState state() {
        NotesNetwork.NotesDataPayload d = NotesNetwork.CLIENT_DATA;
        if (d == null) return new NotesState(0L, false, false, Set.of());
        return new NotesState(d.playtimeTicks(), d.tier1Unlocked(), d.tier2Unlocked(),
                Set.copyOf(d.noteFlags()));
    }

    // ─────────────────────────── навигация ───────────────────────────

    private boolean unlocked(int idx) {
        List<ScholarPage> pages = ScholarNotesContent.PAGES;
        if (idx < 0 || idx >= pages.size()) return false;
        return ScholarNotesContent.isUnlocked(pages.get(idx), state());
    }

    /** Индекс из той же книги, что и текущая страница
     *  (линейная эпоха ↔ «Познание мира» не пересекаются). */
    private boolean sameBook(int idx) {
        List<ScholarPage> pages = ScholarNotesContent.PAGES;
        if (idx < 0 || idx >= pages.size() || pageIndex < 0 || pageIndex >= pages.size()) {
            return false;
        }
        return pages.get(idx).chapter().side() == pages.get(pageIndex).chapter().side();
    }

    private int nextUnlocked(int from) {
        for (int i = from + 1; i < ScholarNotesContent.PAGES.size(); i++) {
            if (sameBook(i) && unlocked(i)) return i;
        }
        return -1;
    }

    private int prevUnlocked(int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (sameBook(i) && unlocked(i)) return i;
        }
        return -1;
    }

    private int firstPageOfChapter(ScholarChapter chapter) {
        List<ScholarPage> pages = ScholarNotesContent.PAGES;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).chapter() == chapter && unlocked(i)) return i;
        }
        return -1;
    }

    private void gotoPage(int idx) {
        this.pageIndex = idx;
        this.scroll = 0;
        this.structureSubpage = 0;
        playPageSound();
    }

    // ─────────────────────────── отрисовка ───────────────────────────

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xC0101010);

        tooltipComponent = null;
        tooltipStack = null;

        drawTabs(g, mouseX, mouseY);   // слой 1 — под панелью
        drawFrame(g);                  // слой 2 — панель
        drawArrows(g, mouseX, mouseY); // слой 3 — контент
        drawPage(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        if (tooltipStack != null && !tooltipStack.isEmpty()) {
            g.renderTooltip(this.font, tooltipStack, tooltipX, tooltipY);
        } else if (tooltipComponent != null) {
            g.renderTooltip(this.font, tooltipComponent, tooltipX, tooltipY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics g) {
    }

    private void drawFrame(GuiGraphics g) {
        // Бумага страницы — фон-панель активной главы (эры): у каждой свой bg.
        // Иллюстрация (шаблон + предметы) рисуется в drawIllustration().
        ScholarChapter chapter = ScholarNotesContent.PAGES.get(pageIndex).chapter();
        ResourceLocation bg = ResourceLocation.fromNamespaceAndPath("gonzotech", chapter.backgroundPath());
        blit(g, bg, leftPos, topPos, FRAME_W, FRAME_H);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        tabRects.clear();
        ScholarChapter[] chapters = orderedChapters();
        int totalH = chapters.length * TAB_H + (chapters.length - 1) * TAB_GAP
                + (chapters[chapters.length - 1].side() ? SIDE_BOOK_GAP : 0);
        int startY = topPos + (FRAME_H - totalH) / 2;
        int tabX = leftPos - TAB_W + 6;

        ScholarChapter active = ScholarNotesContent.PAGES.get(pageIndex).chapter();

        int ty = startY;
        for (int i = 0; i < chapters.length; i++) {
            ScholarChapter ch = chapters[i];
            // Отдельная книга («Познание мира») отделена разрывом от вкладок эпох.
            if (ch.side()) ty += SIDE_BOOK_GAP;
            boolean isActive = ch == active;
            boolean available = firstPageOfChapter(ch) >= 0;
            boolean hovered = inRect(mouseX, mouseY, tabX, ty, TAB_W, TAB_H);

            blit(g, available ? TEX_TAB_UNLOCKED : TEX_TAB_LOCKED, tabX, ty, TAB_W, TAB_H);

            if (isActive) {
                g.fill(tabX, ty, tabX + TAB_W, ty + TAB_H, TAB_ACTIVE_TINT);
            }

            if (available) {
                ItemStack icon = stackOf(ch.iconItemId());
                g.renderItem(icon, tabX + 3, ty + 5);
            } else {
                String q = "?";
                int qw = this.font.width(q);
                g.drawString(this.font, q, tabX + (TAB_W - qw) / 2, ty + (TAB_H - 8) / 2, INK_FAINT, false);
            }

            if (hovered) {
                tooltipComponent = available
                        ? Component.translatable(ch.titleKey())
                        : Component.translatable("gui.gonzotech.notes.locked");
                tooltipStack = null;
                tooltipX = mouseX;
                tooltipY = mouseY;
            }

            tabRects.add(new int[]{tabX, ty, TAB_W, TAB_H, ch.ordinal()});
            ty += TAB_H + TAB_GAP;
        }
    }

    /** Порядок вкладок: линейные эпохи (порядок enum), затем отдельная книга. */
    private static ScholarChapter[] orderedChapters() {
        List<ScholarChapter> linear = new ArrayList<>();
        List<ScholarChapter> side = new ArrayList<>();
        for (ScholarChapter ch : ScholarChapter.values()) {
            if (ch.side()) side.add(ch);
            else linear.add(ch);
        }
        List<ScholarChapter> out = new ArrayList<>(linear.size() + side.size());
        out.addAll(linear);
        out.addAll(side);
        return out.toArray(new ScholarChapter[0]);
    }

    private void drawArrows(GuiGraphics g, int mouseX, int mouseY) {
        int arrowW = 16, arrowH = 20;
        int ay = topPos + FRAME_H - arrowH - 8;

        prevRect = new int[]{leftPos + 6, ay, arrowW, arrowH};
        nextRect = new int[]{leftPos + FRAME_W - arrowW - 6, ay, arrowW, arrowH};

        drawArrow(g, prevRect, true, prevUnlocked(pageIndex) >= 0, inRect(mouseX, mouseY, prevRect));
        drawArrow(g, nextRect, false, nextUnlocked(pageIndex) >= 0, inRect(mouseX, mouseY, nextRect));
    }

    private void drawArrow(GuiGraphics g, int[] r, boolean left, boolean enabled, boolean hovered) {
        int x = r[0], y = r[1], w = r[2], h = r[3];
        int col = !enabled ? 0xFFB9A778 : (hovered ? INK : INK_FAINT);
        String glyph = left ? "\u25C0" : "\u25B6";
        int gw = this.font.width(glyph);
        g.drawString(this.font, glyph, x + (w - gw) / 2, y + (h - 8) / 2, col, false);
    }

    private void drawPage(GuiGraphics g, int mouseX, int mouseY) {
        ScholarPage page = ScholarNotesContent.PAGES.get(pageIndex);
        int contentX = leftPos + 20;
        int contentY = topPos + 16;
        int pageInnerW = FRAME_W - 40;

        if (!unlocked(pageIndex)) {
            drawLockedPage(g, contentX, contentY, pageInnerW);
            return;
        }

        // Заголовок — жирный (пустые страницы-иллюстрации заголовка не имеют).
        if (page.titleKey() != null) {
            Component title = Component.translatable(page.titleKey()).withStyle(ChatFormatting.BOLD);
            g.drawString(this.font, title, contentX, contentY, INK, false);
        }

        // Тело (если есть).
        if (page.hasBody()) {
            int textW = page.layout() == ScholarPage.Layout.TEXT_LEFT
                    ? (pageInnerW / 2) - 4
                    : pageInnerW;
            int lineH = this.font.lineHeight + 1;
            int viewTop = contentY + 16;
            int viewBottom = topPos + FRAME_H - 52;
            List<net.minecraft.util.FormattedCharSequence> lines =
                    this.font.split(bodyComponent(page.bodyKey()), textW);

            this.bodyViewTop = viewTop;
            this.bodyViewBottom = viewBottom;
            this.bodyContentHeight = lines.size() * lineH;
            clampScroll();

            g.enableScissor(contentX, viewTop, contentX + textW, viewBottom);
            int y = viewTop - scroll;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                if (y + lineH >= viewTop && y <= viewBottom) {
                    g.drawString(this.font, line, contentX, y, INK, false);
                }
                y += lineH;
            }
            g.disableScissor();

            int overflow = bodyContentHeight - (viewBottom - viewTop);
            if (overflow > 0) {
                drawScrollbar(g, contentX + textW + 2, viewTop, viewBottom, overflow);
            }
        } else {
            this.bodyContentHeight = 0;
        }

        // Шаблон-иллюстрация (сетка/структура + предметы) — если страница её имеет.
        if (page.illustration() != null) {
            drawIllustration(g, page, mouseX, mouseY);
        }

        drawShowcase(g, page, mouseX, mouseY);

        String pageLabel = (visibleOrdinal(pageIndex) + 1) + " / " + visibleCount();
        int pw = this.font.width(pageLabel);
        g.drawString(this.font, pageLabel, leftPos + (FRAME_W - pw) / 2, topPos + FRAME_H - 12, INK_FAINT, false);
    }

    /**
     * Собирает тело из lang-строки, поддерживая простую разметку
     * {@code **жирный**} и {@code *курсив*}.
     */
    private MutableComponent bodyComponent(String key) {
        String raw = Component.translatable(key).getString();
        MutableComponent out = Component.empty();
        int i = 0;
        boolean bold = false;
        boolean italic = false;
        StringBuilder buf = new StringBuilder();
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '*' && i + 1 < raw.length() && raw.charAt(i + 1) == '*') {
                flush(out, buf, bold, italic);
                bold = !bold;
                i += 2;
            } else if (c == '*') {
                flush(out, buf, bold, italic);
                italic = !italic;
                i += 1;
            } else {
                buf.append(c);
                i++;
            }
        }
        flush(out, buf, bold, italic);
        return out;
    }

    private void flush(MutableComponent out, StringBuilder buf, boolean bold, boolean italic) {
        if (buf.length() == 0) return;
        Style style = Style.EMPTY.withBold(bold).withItalic(italic);
        out.append(Component.literal(buf.toString()).withStyle(style));
        buf.setLength(0);
    }

    private void drawScrollbar(GuiGraphics g, int x, int top, int bottom, int overflow) {
        int trackH = bottom - top;
        int thumbH = Math.max(12, (int) ((long) trackH * trackH / (trackH + overflow)));
        int maxThumbY = trackH - thumbH;
        int thumbY = top + (overflow == 0 ? 0 : (int) ((long) scroll * maxThumbY / overflow));
        g.fill(x, top, x + 2, bottom, 0x33000000);
        g.fill(x, thumbY, x + 2, thumbY + thumbH, INK_FAINT);
    }

    // ─────────────── шаблонные иллюстрации ───────────────

    /** Слот шаблона: позиция в координатах страницы (256×200) + id предмета. */
    private record NoteSlot(int x, int y, String itemId) {
        boolean hasItem() {
            return itemId != null && !itemId.isEmpty();
        }
    }

    // Раскладка слотов — абсолютные координаты страницы (от левого верхнего угла),
    // совпадают с графическими сетками в PNG-шаблонах (textures/gui/notes/).
    private static final int[] GRID_Y = {47, 65, 83};
    private static final int[] GRID_RIGHT_X = {158, 176, 194};
    private static final int[] GRID_LEFT_X = {50, 68, 86};
    private static final int RESULT_Y = 120;
    private static final int[] FERMENT_Y = {40, 68, 96, 124};
    private static final int FERMENT_IN_X = 135;
    private static final int FERMENT_OUT_X = 212;

    // Подписи шаблонов шрифтом (локализация; в PNG не запекаются).
    private static final int CAPTION_Y = 34;
    private static final int CAPTION_CRAFT_RIGHT_X = 184; // центр правой сетки
    private static final int CAPTION_CRAFT_LEFT_X = 76;   // центр левой сетки
    private static final int CAPTION_STRUCTURE_X = 192;   // центр панели структуры

    // Точки привязки панели структуры — АБСОЛЮТНЫЕ page coords (256×200),
    // заданы автором точно под арт (авто-детект рамки убран):
    /** Подпись «Сборка (1)» / «Слой (N)» ВКЛЮЧАЯ СТРЕЛКИ: левый верхний угол
     *  описывающего прямоугольника строки — (133, 34); y = CAPTION_Y (34). */
    private static final int STRUCT_CAPTION_LEFT_X = 133;
    /** Выкладка слоёв: центр описывающего прямоугольника сетки — (183, 89). */
    private static final int STRUCT_GRID_CENTER_X = 183;
    private static final int STRUCT_GRID_CENTER_Y = 89;

    /** Шаблоны, которых нет в ресурсах (не рисуем и не ищем повторно). */
    private static final Set<ResourceLocation> MISSING_TEMPLATE_TEX = new HashSet<>();

    /** Шаблон-иллюстрация страницы: PNG-оверлей + подписи + предметы в слотах. */
    private void drawIllustration(GuiGraphics g, ScholarPage page, int mouseX, int mouseY) {
        NoteIllustration il = page.illustration();
        NoteIllustrationKind kind = il.kind();
        structPrevRect = null;
        structNextRect = null;

        // 1) Прозрачный PNG-шаблон (сетки/панели) поверх бумаги главы.
        //    Файл 256×200 → 1:1, 512×400 → в то же окно (выше плотность).
        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(
                "gonzotech", "textures/gui/notes/" + kind.textureName());
        if (!MISSING_TEMPLATE_TEX.contains(tex)
                && Minecraft.getInstance().getResourceManager().getResource(tex).isPresent()) {
            long size = pngSize(tex);
            blitScaled(g, tex, leftPos, topPos, FRAME_W, FRAME_H,
                    (int) (size >>> 32), (int) (size & 0xffffffffL));
        } else {
            MISSING_TEMPLATE_TEX.add(tex);
        }

        // 2) Локализуемые подписи над сетками/панелями.
        switch (kind) {
            case CRAFTING_RIGHT ->
                    drawCaption(g, "gui.gonzotech.notes.illustration.crafting", CAPTION_CRAFT_RIGHT_X);
            case CRAFTING_FULL -> {
                drawCaption(g, "gui.gonzotech.notes.illustration.crafting", CAPTION_CRAFT_LEFT_X);
                drawCaption(g, "gui.gonzotech.notes.illustration.crafting", CAPTION_CRAFT_RIGHT_X);
            }
            case CRAFTING_STRUCTURE -> {
                drawCaption(g, "gui.gonzotech.notes.illustration.crafting", CAPTION_CRAFT_LEFT_X);
                drawStructureCaption(g, il);
            }
            case STRUCTURE_RIGHT -> {
                drawStructureCaption(g, il);
            }
            case CRAFTING_FERMENTATION ->
                    drawCaption(g, "gui.gonzotech.notes.illustration.crafting", CAPTION_CRAFT_LEFT_X);
            case FERMENTATION -> {
                // Подписи — в самом шаблоне (уникальная иллюстрация).
            }
        }

        // 3) Предметы в слотах — как на витрине: renderItem + hover-тултип.
        int cycle = cycleIndex(il);
        for (NoteSlot slot : slotsOf(il, cycle)) {
            if (!slot.hasItem()) continue;
            ItemStack st = stackOf(slot.itemId());
            if (st.isEmpty()) continue;
            int sx = leftPos + slot.x();
            int sy = topPos + slot.y();
            g.renderItem(st, sx, sy);
            if (inRect(mouseX, mouseY, sx, sy, 16, 16)) {
                tooltipStack = st;
                tooltipComponent = null;
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
        }

        // 4) Панель структуры: подстраницы («Сборка» / слои «вид сверху») или
        //    плоский вид («Вид сверху»/«Вид сбоку», возможно «тикает»), тултипы.
        //    Точки привязки — абсолютные page coords (автор).
        if (il.structure() != null) {
            drawStructureContent(g, il.structure(), mouseX, mouseY);
        } else if (il.deckView() != null) {
            drawDeckContent(g, il.deckView(), cycle, mouseX, mouseY);
        } else if (il.flatView() != null) {
            drawFlatStructure(g, il.flatView(), cycle, mouseX, mouseY);
        }
    }

    private void drawCaption(GuiGraphics g, String key, int centerX) {
        String text = Component.translatable(key).getString();
        int tw = this.font.width(text);
        g.drawString(this.font, text, leftPos + centerX - tw / 2, topPos + CAPTION_Y, INK_FAINT, false);
    }

    // ─────────────── структуры: подстраницы (сборка / слои) ───────────────

    /**
     * Подпись панели структуры + навигация подстраниц
     * «‹  Нижний слой (1)  ›» (котёл-топка — «Сборка (1)» без стрелок).
     * Привязка — ЛЕВЫЙ ВЕРХНИЙ угол описывающего прямоугольника видимой
     * строки (стрелки входят в строку): (133, 34) page coords (автор).
     * Прямоугольники ‹ › запоминаются для mouseClicked.
     */
    private void drawStructureNav(GuiGraphics g, StructureModel model) {
        drawSubpageNav(g, structureTitle(model, structureSubpage), model.subpageCount());
    }

    /**
     * Навигация подстраниц «‹  Заголовок  ›» (или просто заголовок, если
     * подстраница одна). Привязка — ЛЕВЫЙ ВЕРХНИЙ угол описывающего
     * прямоугольника видимой строки: (133, 34) page coords (автор).
     */
    private void drawSubpageNav(GuiGraphics g, String title, int total) {
        int cw = this.font.width(title);
        int side = 14;
        // Левый край строки: без стрелок — сам текст; со стрелками — левая стрелка.
        int titleX = STRUCT_CAPTION_LEFT_X + (total > 1 ? side : 0);
        int capY = topPos + CAPTION_Y;

        g.drawString(this.font, title, leftPos + titleX, capY, INK_FAINT, false);

        if (total > 1) {
            int prevX = titleX - side;
            int nextX = titleX + cw + side - this.font.width("\u203A");
            int prevCol = structureSubpage > 0 ? INK : 0xFFB9A778;
            int nextCol = structureSubpage < total - 1 ? INK : 0xFFB9A778;
            g.drawString(this.font, "\u2039", leftPos + prevX, capY, prevCol, false);
            g.drawString(this.font, "\u203A", leftPos + nextX, capY, nextCol, false);
            structPrevRect = new int[]{leftPos + prevX - 3, capY - 3, 12, 12};
            structNextRect = new int[]{leftPos + nextX - 3, capY - 3, 12, 12};
        }
    }

    /**
     * Подпись панели структуры: модель — навигация «‹  Заголовок  ›»;
     * плоский вид — «Вид сверху»/«Вид сбоку» (левый верхний угол — 133,34);
     * ничего нет — заглушка «Структура».
     */
    private void drawStructureCaption(GuiGraphics g, NoteIllustration il) {
        if (il.structure() != null) {
            drawStructureNav(g, il.structure());
        } else if (il.deckView() != null) {
            drawSubpageNav(g, "Слой (" + (structureSubpage + 1) + "/" + il.deckView().subpageCount() + ")",
                    il.deckView().subpageCount());
        } else if (il.flatView() != null) {
            String text = Component.translatable(il.flatView().captionKey()).getString();
            g.drawString(this.font, text, leftPos + STRUCT_CAPTION_LEFT_X, topPos + CAPTION_Y, INK_FAINT, false);
        } else {
            drawCaption(g, "gui.gonzotech.notes.illustration.structure", CAPTION_STRUCTURE_X);
        }
    }

    /** Подпись подстраницы: «Сборка (1)» или «Нижний/Средний/Верхний слой (N)»
     *  (1 = нижний слой, подстраницы идут снизу вверх). */
    private static String structureTitle(StructureModel m, int sub) {
        if (m.assembly()) return "Сборка (1)";
        int total = m.sizeY();
        if (total == 3) {
            if (sub == 0) return "Нижний слой (1)";
            if (sub == 1) return "Средний слой (2)";
            return "Верхний слой (3)";
        }
        if (total == 2) {
            return sub == 0 ? "Нижний слой (1)" : "Верхний слой (2)";
        }
        return "Слой (" + (sub + 1) + "/" + total + ")";
    }

    /**
     * Подстраница структуры — сетка иконок предметов С ГЭПОМ (гэп подчёркивает
     * структурность): «Сборка» — вид спереди модели (передний блок колонки);
     * иначе — слой {@code y = structureSubpage} «вид сверху» (1 = нижний).
     * Центр описывающего прямоугольника сетки — (183, 89) page coords (автор);
     * размер сетки зависит от GUI-масштаба (иконки = масштаб + 1).
     */
    private void drawStructureContent(GuiGraphics g, StructureModel model, int mouseX, int mouseY) {
        boolean asm = model.assembly();
        int cols = model.sizeX();
        int rows = asm ? model.sizeY() : model.sizeZ();
        int layerY = structureSubpage;
        // Слои «вид сверху»: z=0 (перед) — ВЕРХНИЙ ряд. Сборка: y=0 — НИЖНИЙ ряд.
        BiFunction<Integer, Integer, String> cell = asm
                ? (gx, gy) -> itemId(model.front(gx, rows - 1 - gy))
                : (gx, gy) -> itemId(model.at(gx, layerY, gy));
        drawItemGrid(g, cols, rows, cell, mouseX, mouseY, 1);
    }

    /** Плоский вид («Вид сверху»/«Вид сбоку»): кадр {@code frameIdx} (цикл),
     *  строка 0 кадра — верхний ряд сетки. */
    private void drawFlatStructure(GuiGraphics g, NoteIllustration.FlatView view, int frameIdx,
                                   int mouseX, int mouseY) {
        List<List<String>> frames = view.frames();
        List<String> frame = frames.get(frameIdx % frames.size());
        int cols = view.cols();
        int rows = frame.size() / cols;
        drawItemGrid(g, cols, rows, (gx, gy) -> frame.get(gy * cols + gx), mouseX, mouseY, 1);
    }

    /**
     * Плоская «колода»: активный слой (подстраница) сеткой cols×rows.
     * Случайные клетки — ОДИН расклад на кадр на ВСЮ колоду: один Random на
     * все слои в фиксированном порядке (не зависит от открытого слоя —
     * раскладка не дрожит и слои согласованы), новый расклад — каждый
     * cycleTicks. Перед показом гарантируется минимум {@code minGuaranteed}
     * клеток с {@code guaranteedId} среди случайных клеток всех слоёв
     * (ядра парогена всегда видны, а не только «драгоценные» блоки).
     */
    private void drawDeckContent(GuiGraphics g, NoteIllustration.DeckView view, int cycle,
                                 int mouseX, int mouseY) {
        int cols = view.cols();
        Random rnd = new Random(((long) cycle + 1) * 1000003L);
        List<List<String>> rolls = new ArrayList<>(view.layers().size());
        List<int[]> randomSpots = new ArrayList<>(); // [layerIdx, row-major idx]
        for (int li = 0; li < view.layers().size(); li++) {
            NoteIllustration.DeckLayer layer = view.layers().get(li);
            List<String> cells = new ArrayList<>(layer.fixed().size());
            boolean roll = layer.pool() != null && !layer.pool().isEmpty();
            for (int i = 0; i < layer.fixed().size(); i++) {
                String fixed = layer.fixed().get(i);
                if (roll && fixed.isEmpty()) {
                    cells.add(layer.pool().get(rnd.nextInt(layer.pool().size())));
                    randomSpots.add(new int[]{li, i});
                } else {
                    cells.add(fixed);
                }
            }
            rolls.add(cells);
        }
        // Гарантия: минимум minGuaranteed ядра (guaranteedId) в случайных клетках
        // всей колоды; недостающие вписываем в случайные позиции (тот же Random).
        String guaranteed = view.guaranteedId();
        if (guaranteed != null && view.minGuaranteed() > 0 && !randomSpots.isEmpty()) {
            int have = 0;
            for (List<String> cells : rolls) {
                for (String id : cells) {
                    if (guaranteed.equals(id)) have++;
                }
            }
            if (have < view.minGuaranteed()) {
                List<int[]> candidates = new ArrayList<>(randomSpots);
                Collections.shuffle(candidates, rnd);
                for (int[] spot : candidates) {
                    if (have >= view.minGuaranteed()) break;
                    List<String> cells = rolls.get(spot[0]);
                    if (guaranteed.equals(cells.get(spot[1]))) continue;
                    cells.set(spot[1], guaranteed);
                    have++;
                }
            }
        }
        NoteIllustration.DeckLayer layer = view.layers().get(structureSubpage);
        List<String> cells = rolls.get(structureSubpage);
        int rows = layer.fixed().size() / cols;
        BiFunction<Integer, Integer, String> cell = (gx, gy) -> cells.get(gy * cols + gx);
        drawItemGrid(g, cols, rows, cell, mouseX, mouseY, 0);
    }

    /** Есть ли в колоде хотя бы один слой со случайным пулом (нужен ли таймер). */
    private static boolean deckHasRandom(NoteIllustration.DeckView view) {
        for (NoteIllustration.DeckLayer layer : view.layers()) {
            if (layer.pool() != null && !layer.pool().isEmpty()) return true;
        }
        return false;
    }

    private static String itemId(StructureBlock b) {
        return b == null ? null : b.itemId();
    }

    /**
     * Общий рендер сетки иконок предметов С ГЭПОМ: центр описывающего
     * прямоугольника — (183, 89) page coords (автор); гэп пропорциональный;
     * пустая клетка — бледный слот; ховер — тултип.
     *
     * @param sizeExtra прирост размера иконки к GUI-масштабу: 1 — «N+1»
     *                  (обычные структуры), 0 — «N+0» (широкие сетки, которые
     *                  при «N+1» вылазят за окно искусства, — колода 5×5).
     */
    private void drawItemGrid(GuiGraphics g, int cols, int rows,
                              BiFunction<Integer, Integer, String> cell, int mouseX, int mouseY,
                              int sizeExtra) {
        int icon = structIconSize(sizeExtra);
        int gap = structGap(sizeExtra);
        int cellSize = icon + gap;
        int gridW = cols * cellSize - gap;
        int gridH = rows * cellSize - gap;
        int gx0 = STRUCT_GRID_CENTER_X - gridW / 2;
        int gy0 = STRUCT_GRID_CENTER_Y - gridH / 2;
        float kf = icon / 16f;
        for (int gx = 0; gx < cols; gx++) {
            for (int gy = 0; gy < rows; gy++) {
                int sx = leftPos + gx0 + gx * cellSize;
                int sy = topPos + gy0 + gy * cellSize;
                String id = cell.apply(gx, gy);
                ItemStack st = (id == null || id.isEmpty()) ? ItemStack.EMPTY : stackOf(id);
                if (st.isEmpty()) {
                    // Пустая клетка ИЛИ id без предметной формы (например
                    // ванильный блок лавы) — бледный слот, не дырка.
                    g.fill(sx, sy, sx + icon, sy + icon, 0x0F000000);
                    drawSlotOutline(g, sx, sy, icon, icon, 0x2E000000);
                    continue;
                }
                renderScaledItem(g, st, sx, sy, kf);
                if (inRect(mouseX, mouseY, sx, sy, icon, icon)) {
                    tooltipStack = st;
                    tooltipComponent = null;
                    tooltipX = mouseX;
                    tooltipY = mouseY;
                }
            }
        }
    }

    /** Размер иконки (page px): 16 × (N + extra) / N, где N = GUI-масштаб.
     *  extra = 1 (автор: «увеличить на +1 от размера интерфейса»):
     *  интерфейс 2 → иконки 24px; extra = 0 — базовые 16px (широкие сетки). */
    private static int structIconSize(int extra) {
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        if (s < 1) s = 1;
        return Math.max(16, (int) Math.round(16 * (s + extra) / s));
    }

    /** Гэп между иконками (page px), пропорционально иконке (база 2px при 16px). */
    private static int structGap(int extra) {
        double s = Minecraft.getInstance().getWindow().getGuiScale();
        if (s < 1) s = 1;
        return Math.max(2, (int) Math.round(2 * (s + extra) / s));
    }

    /** Иконка предмета с масштабированием (pose-матрица, как в blitScaled). */
    private void renderScaledItem(GuiGraphics g, ItemStack stack, int x, int y, float k) {
        if (k <= 1f) {
            g.renderItem(stack, x, y);
            return;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(k, k, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    /** Обводка прямоугольника через fill (у GuiGraphics 1.21.4 нет drawOutline). */
    private void drawSlotOutline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Точка отсчёта цикла кадров (System.currentTimeMillis, 0 = ещё не шла). */
    private long cycleBaseMs;
    private long lastCycleBaseMs;

    /** Номер кадра цикла в реальном времени (1 тик = 50 мс; оба окна на одном
     *  таймере). Не зависит от FPS: рендер вызывается каждый кадр, а номер
     *  кадра считается по истечённым тикам с момента открытия экрана. */
    private int cycleIndex(NoteIllustration il) {
        boolean cycling = (il.leftSequence() != null && !il.leftSequence().isEmpty())
                || (il.rightSequence() != null && !il.rightSequence().isEmpty())
                || (il.flatView() != null && il.flatView().frames().size() > 1)
                || (il.deckView() != null && deckHasRandom(il.deckView()));
        if (!cycling || il.cycleTicks() <= 0) return 0;
        long now = System.currentTimeMillis();
        if (now != lastCycleBaseMs) {
            lastCycleBaseMs = now;
            if (cycleBaseMs == 0) cycleBaseMs = now;
        }
        return (int) ((now - cycleBaseMs) / (il.cycleTicks() * 50L));
    }

    /** Слоты иллюстрации с предметами (шаблон → координаты страницы). */
    private List<NoteSlot> slotsOf(NoteIllustration il, int cycle) {
        List<NoteSlot> out = new ArrayList<>();
        switch (il.kind()) {
            case CRAFTING_RIGHT -> {
                gridSlots(out, GRID_RIGHT_X, il.leftGrid());
                addResultSlot(out, 176, il.leftResult());
            }
            case CRAFTING_FULL -> {
                NoteIllustration.Craft left =
                        pickCraft(il.leftSequence(), il.leftGrid(), il.leftResult(), cycle);
                NoteIllustration.Craft right =
                        pickCraft(il.rightSequence(), il.rightGrid(), il.rightResult(), cycle);
                gridSlots(out, GRID_LEFT_X, left.grid());
                addResultSlot(out, 68, left.result());
                gridSlots(out, GRID_RIGHT_X, right.grid());
                addResultSlot(out, 176, right.result());
            }
            case CRAFTING_STRUCTURE -> {
                // Справа — панель структуры; раскладка ЕЁ слотов — по каждой структуре (позже).
                gridSlots(out, GRID_LEFT_X, il.leftGrid());
                addResultSlot(out, 68, il.leftResult());
            }
            case STRUCTURE_RIGHT -> {
                // Раскладка слотов структуры — по каждой структуре (позже).
            }
            case FERMENTATION -> addFermentation(out, il);
            case CRAFTING_FERMENTATION -> {
                gridSlots(out, GRID_LEFT_X, il.leftGrid());
                addResultSlot(out, 68, il.leftResult());
                addFermentation(out, il);
            }
        }
        return out;
    }

    /** Кадр окна: кадр последовательности (цикл) или статичная сетка. */
    private static NoteIllustration.Craft pickCraft(List<NoteIllustration.Craft> sequence,
                                                    List<String> grid, String result, int cycle) {
        if (sequence != null && !sequence.isEmpty()) {
            return sequence.get(cycle % sequence.size());
        }
        return new NoteIllustration.Craft(grid, result);
    }

    private void addFermentation(List<NoteSlot> out, NoteIllustration il) {
        List<String> inputs = il.fermInputs();
        List<String> outputs = il.fermOutputs();
        for (int i = 0; i < FERMENT_Y.length; i++) {
            if (inputs != null && i < inputs.size()) {
                out.add(new NoteSlot(FERMENT_IN_X, FERMENT_Y[i], inputs.get(i)));
            }
            if (outputs != null && i < outputs.size()) {
                out.add(new NoteSlot(FERMENT_OUT_X, FERMENT_Y[i], outputs.get(i)));
            }
        }
    }

    private void gridSlots(List<NoteSlot> out, int[] xs, List<String> grid) {
        if (grid == null) return;
        for (int i = 0; i < 9 && i < grid.size(); i++) {
            out.add(new NoteSlot(xs[i % 3], GRID_Y[i / 3], grid.get(i)));
        }
    }

    private void addResultSlot(List<NoteSlot> out, int x, String item) {
        if (item != null && !item.isEmpty()) {
            out.add(new NoteSlot(x, RESULT_Y, item));
        }
    }

    private void drawShowcase(GuiGraphics g, ScholarPage page, int mouseX, int mouseY) {
        List<String> items = page.showcaseItems();
        if (items.isEmpty()) return;
        int slot = 20;
        int y = topPos + FRAME_H - 44;
        int startX = leftPos + 20;
        for (int i = 0; i < items.size(); i++) {
            int sx = startX + i * slot;
            g.fill(sx - 1, y - 1, sx + 17, y + 17, SLOT_TINT);
            ItemStack st = stackOf(items.get(i));
            g.renderItem(st, sx, y);
            if (inRect(mouseX, mouseY, sx, y, 16, 16) && !st.isEmpty()) {
                tooltipStack = st;
                tooltipComponent = null;
                tooltipX = mouseX;
                tooltipY = mouseY;
            }
        }
    }

    private void drawLockedPage(GuiGraphics g, int x, int y, int w) {
        int cx = x + w / 2;
        int lockY = y + 40;
        g.fill(cx - 7, lockY, cx + 7, lockY + 11, INK_FAINT);
        g.fill(cx - 4, lockY - 5, cx - 3, lockY, INK_FAINT);
        g.fill(cx + 3, lockY - 5, cx + 4, lockY, INK_FAINT);
        g.fill(cx - 4, lockY - 6, cx + 4, lockY - 5, INK_FAINT);
        g.fill(cx - 1, lockY + 4, cx + 1, lockY + 8, INK);

        Component locked = Component.translatable("gui.gonzotech.notes.locked");
        int lw = this.font.width(locked);
        g.drawString(this.font, locked, x + (w - lw) / 2, lockY + 20, INK_FAINT, false);
    }

    // ─────────────────────────── ввод ───────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Панель структуры (навигация подстраниц) — до вкладок и стрелок буклета.
        NoteIllustration il = ScholarNotesContent.PAGES.get(pageIndex).illustration();
        if (il != null && (il.structure() != null || il.deckView() != null)) {
            int subpages = il.structure() != null ? il.structure().subpageCount() : il.deckView().subpageCount();
            if (structPrevRect != null && inRect((int) mouseX, (int) mouseY, structPrevRect)) {
                if (structureSubpage > 0) {
                    structureSubpage--;
                    playPageSound();
                }
                return true;
            }
            if (structNextRect != null && inRect((int) mouseX, (int) mouseY, structNextRect)) {
                if (structureSubpage < subpages - 1) {
                    structureSubpage++;
                    playPageSound();
                }
                return true;
            }
        }

        if (button == 0) {
            for (int[] r : tabRects) {
                if (inRect((int) mouseX, (int) mouseY, r[0], r[1], r[2], r[3])) {
                    ScholarChapter ch = ScholarChapter.values()[r[4]];
                    int target = firstPageOfChapter(ch);
                    if (target >= 0) gotoPage(target);
                    return true;
                }
            }
            if (prevRect != null && inRect((int) mouseX, (int) mouseY, prevRect)) {
                int p = prevUnlocked(pageIndex);
                if (p >= 0) gotoPage(p);
                return true;
            }
            if (nextRect != null && inRect((int) mouseX, (int) mouseY, nextRect)) {
                int n = nextUnlocked(pageIndex);
                if (n >= 0) gotoPage(n);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int overflow = bodyContentHeight - (bodyViewBottom - bodyViewTop);
        if (overflow > 0) {
            scroll -= (int) (scrollY * (this.font.lineHeight + 1) * 2);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int overflow = Math.max(0, bodyContentHeight - (bodyViewBottom - bodyViewTop));
        if (scroll < 0) scroll = 0;
        if (scroll > overflow) scroll = overflow;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263) {
            int p = prevUnlocked(pageIndex);
            if (p >= 0) gotoPage(p);
            return true;
        }
        if (keyCode == 262) {
            int n = nextUnlocked(pageIndex);
            if (n >= 0) gotoPage(n);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playPageSound() {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    // ─────────────────────────── утилиты ───────────────────────────

    private void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h) {
        g.blit(RenderType::guiTextured, tex, x, y, 0f, 0f, w, h, w, h);
    }

    /**
     * Рисует текстуру в окно {@code w×h} экранных пикселей, сэмплируя её целиком из
     * файла размером {@code texW×texH}. Если файл 256×200 — это 1:1; если 512×400 —
     * весь файл ужимается в то же окно (вдвое выше плотность пикселей). Окно на
     * экране НЕ меняется от размера PNG.
     */
    private void blitScaled(GuiGraphics g, ResourceLocation tex, int x, int y,
                            int w, int h, int texW, int texH) {
        // Рисуем текстуру в её НАТИВНОМ размере (texW×texH), а матрицу масштабируем
        // так, чтобы она уложилась в окно w×h экранных пикселей. Так любой размер
        // PNG ужимается в фиксированное окно = выше плотность без роста окна.
        // Используем ту же 10-арг сигнатуру blit, что и обычный blit(): она надёжна.
        if (texW <= 0 || texH <= 0) { texW = w; texH = h; }
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale((float) w / texW, (float) h / texH, 1f);
        g.blit(RenderType::guiTextured, tex, 0, 0, 0f, 0f, texW, texH, texW, texH);
        g.pose().popPose();
    }

    /** Кэш «путь → упакованные (ширина<<32 | высота)» размеров PNG. */
    private static final Map<ResourceLocation, Long> PNG_SIZE_CACHE = new HashMap<>();

    /**
     * Возвращает размеры PNG, упакованные в long: {@code (width << 32) | height}.
     * Читается ровно 24 байта заголовка PNG (сигнатура + IHDR), без декодирования
     * картинки. Результат кэшируется. При любой ошибке — дефолт 256×200.
     */
    private long pngSize(ResourceLocation tex) {
        Long cached = PNG_SIZE_CACHE.get(tex);
        if (cached != null) return cached;

        int w = FRAME_W;
        int h = FRAME_H;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isPresent()) {
            try (InputStream in = res.get().open()) {
                byte[] head = in.readNBytes(24);
                // Байты 16..19 — ширина, 20..23 — высота (big-endian), после 8-байтовой
                // сигнатуры PNG и заголовка IHDR-чанка.
                if (head.length >= 24) {
                    w = readBE(head, 16);
                    h = readBE(head, 20);
                    if (w <= 0 || h <= 0) { w = FRAME_W; h = FRAME_H; }
                }
            } catch (Exception ignored) {
                // оставляем дефолт 256×200
            }
        }
        long packed = ((long) w << 32) | (h & 0xffffffffL);
        PNG_SIZE_CACHE.put(tex, packed);
        return packed;
    }

    private static int readBE(byte[] b, int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16)
                | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    private void drawDashedRect(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        for (int x = x0; x < x1; x += 4) {
            g.fill(x, y0, Math.min(x + 2, x1), y0 + 1, color);
            g.fill(x, y1 - 1, Math.min(x + 2, x1), y1, color);
        }
        for (int y = y0; y < y1; y += 4) {
            g.fill(x0, y, x0 + 1, Math.min(y + 2, y1), color);
            g.fill(x1 - 1, y, x1, Math.min(y + 2, y1), color);
        }
    }

    /** Сколько ОТКРЫТЫХ страниц в книге текущей страницы (для «N / M»). */
    private int visibleCount() {
        int c = 0;
        for (int i = 0; i < ScholarNotesContent.PAGES.size(); i++) {
            if (sameBook(i) && unlocked(i)) c++;
        }
        return Math.max(1, c);
    }

    /** Позиция открытой страницы среди открытых страниц ЕЁ книги (0-based). */
    private int visibleOrdinal(int idx) {
        int c = 0;
        for (int i = 0; i < idx; i++) {
            if (sameBook(i) && unlocked(i)) c++;
        }
        return c;
    }

    private ItemStack stackOf(String id) {
        ResourceLocation rl = ResourceLocation.parse(id);
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(rl);
        if (item == null || item == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    private boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean inRect(int mx, int my, int[] r) {
        return inRect(mx, my, r[0], r[1], r[2], r[3]);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
