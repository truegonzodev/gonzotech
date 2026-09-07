package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.network.NotesNetwork;
import com.gonzotech.chalkboard.notes.ScholarChapter;
import com.gonzotech.chalkboard.notes.ScholarNotesContent;
import com.gonzotech.chalkboard.notes.ScholarPage;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GUI-буклет «Заметки учёного» (вариант 2): контейнер (фон панели и вкладки) —
 * рисованные PNG; весь текст рисуется шрифтом ради локализации; иллюстрации
 * страниц — рисованный PNG на страницу ({@code page_N.png}).
 *
 * <p>Контейнер = панель-фон ПО ГЛАВЕ ({@code notes_bg_era1..era5.png}, выбирается
 * активной эрой) + {@code notes_unlocked_tab.png} + {@code notes_locked_tab.png}.
 * Иконка-предмет главы, тинт активной вкладки, «?» на закрытой — это РЕНДЕР поверх
 * PNG. Вкладки рисуются ПЕРВЫМИ (уходят под панель).
 *
 * <p>Раскладка страницы задаётся per-page ({@link ScholarPage.Layout}):
 * TEXT_FULL / TEXT_LEFT (правая половина под иллюстрацию) / IMAGE_FULL (только арт).
 * Заголовки — жирные. Длинный текст мотается колёсиком.
 *
 * <p>Разблокировка — {@link com.gonzotech.chalkboard.notes.ScholarUnlock}; данные
 * (наигранное время + tier 1) берутся из {@link NotesNetwork#CLIENT_DATA}.
 */
public class ScholarNotesScreen extends Screen {

    private static final int FRAME_W = 256;
    private static final int FRAME_H = 200;

    private static final int TAB_W = 28;
    private static final int TAB_H = 26;
    private static final int TAB_GAP = 4;

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

    public ScholarNotesScreen() {
        super(Component.translatable("gui.gonzotech.notes.title"));
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - FRAME_W) / 2;
        this.topPos = (this.height - FRAME_H) / 2;
        this.pageIndex = ScholarNotesContent.firstUnlockedIndex(playtime(), tier1());
        this.scroll = 0;
        // Сбрасываем кэш размеров: если автор перерисовал PNG в другом разрешении
        // и сделал перезагрузку ресурсов (F3+T), подхватим новый размер.
        PNG_SIZE_CACHE.clear();
    }

    private long playtime() {
        NotesNetwork.NotesDataPayload d = NotesNetwork.CLIENT_DATA;
        return d != null ? d.playtimeTicks() : 0L;
    }

    private boolean tier1() {
        NotesNetwork.NotesDataPayload d = NotesNetwork.CLIENT_DATA;
        return d != null && d.tier1Unlocked();
    }

    // ─────────────────────────── навигация ───────────────────────────

    private boolean unlocked(int idx) {
        List<ScholarPage> pages = ScholarNotesContent.PAGES;
        if (idx < 0 || idx >= pages.size()) return false;
        return ScholarNotesContent.isUnlocked(pages.get(idx), playtime(), tier1());
    }

    private int nextUnlocked(int from) {
        for (int i = from + 1; i < ScholarNotesContent.PAGES.size(); i++) {
            if (unlocked(i)) return i;
        }
        return -1;
    }

    private int prevUnlocked(int from) {
        for (int i = from - 1; i >= 0; i--) {
            if (unlocked(i)) return i;
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
        // Фон-панель контейнера зависит от активной главы (эры): у каждой свой bg.
        ScholarChapter chapter = ScholarNotesContent.PAGES.get(pageIndex).chapter();
        ResourceLocation bg = ResourceLocation.fromNamespaceAndPath("gonzotech", chapter.backgroundPath());
        blit(g, bg, leftPos, topPos, FRAME_W, FRAME_H);
        ScholarPage page = ScholarNotesContent.PAGES.get(pageIndex);
        ResourceLocation pageBg = ResourceLocation.fromNamespaceAndPath("gonzotech", page.backgroundPath());
        // Иллюстрация страницы всегда занимает ОКНО 256×200, но сэмплируется из
        // реального размера файла: 256×200 → 1:1, 512×400 (или любой) → ужимается
        // в то же окно = выше плотность пикселей. Размер PNG читаем из заголовка.
        long size = pngSize(pageBg);
        int texW = (int) (size >>> 32);
        int texH = (int) (size & 0xffffffffL);
        blitScaled(g, pageBg, leftPos, topPos, FRAME_W, FRAME_H, texW, texH);
    }

    private void drawTabs(GuiGraphics g, int mouseX, int mouseY) {
        tabRects.clear();
        ScholarChapter[] chapters = ScholarChapter.values();
        int totalH = chapters.length * TAB_H + (chapters.length - 1) * TAB_GAP;
        int startY = topPos + (FRAME_H - totalH) / 2;
        int tabX = leftPos - TAB_W + 6;

        ScholarChapter active = ScholarNotesContent.PAGES.get(pageIndex).chapter();

        for (int i = 0; i < chapters.length; i++) {
            ScholarChapter ch = chapters[i];
            int ty = startY + i * (TAB_H + TAB_GAP);
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
        }
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

        // Заголовок — жирный.
        Component title = Component.translatable(page.titleKey()).withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, title, contentX, contentY, INK, false);

        // Иллюстрация (правая половина при TEXT_LEFT) рисуется автором прямо в
        // page_N.png — фон страницы. Плейсхолдер-рамка «иллюстрация» больше не
        // рисуется, чтобы не перекрывать нарисованный арт.

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

    private int visibleCount() {
        int c = 0;
        for (int i = 0; i < ScholarNotesContent.PAGES.size(); i++) if (unlocked(i)) c++;
        return Math.max(1, c);
    }

    private int visibleOrdinal(int idx) {
        int c = 0;
        for (int i = 0; i < idx; i++) if (unlocked(i)) c++;
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
