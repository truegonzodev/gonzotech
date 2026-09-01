package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.core.Analysis;
import com.gonzotech.chalkboard.core.DimVec;
import com.gonzotech.chalkboard.core.Evaluator;
import com.gonzotech.chalkboard.core.Expr;
import com.gonzotech.chalkboard.core.GameSolver;
import com.gonzotech.chalkboard.core.Manipulate;
import com.gonzotech.chalkboard.core.Quantities;
import com.gonzotech.chalkboard.core.Quantity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The whole game inside one Minecraft screen.
 * <p>
 * Left-drag a quantity from the tray onto a block: the edge you release on
 * decides the operation — left/right multiply, top/bottom build a fraction.
 */
public class ResonanceScreen extends Screen {

    // ── state ──
    private Expr expr;
    private GameSolver.Puzzle puzzle;
    private Analysis analysis;
    private Map<String, Double> aura = Map.of();

    private int difficulty = 1;
    private boolean showHint;
    private int sandboxIndex;

    private String selectedSlotId;
    private String hoverSlotId;
    private Manipulate.Direction hoverZone = Manipulate.Direction.CENTER;

    // ── drag ──
    private Quantity pressedQuantity;
    private String dragFromSlotId;
    private boolean dragging;
    private double pressX, pressY;

    // ── camera ──
    private int panX, panY;
    private boolean panning;
    private double panStartX, panStartY;
    private int panOriginX, panOriginY;

    // ── tray ──
    private EditBox search;
    private int trayScroll;
    private int weightFilter = -1;
    private int categoryFilter = -1;
    private List<Quantity> trayItems = List.of();

    // ── regions ──
    private int canvasX, canvasY, canvasW, canvasH;
    private int panelX, panelW;
    private int trayY, trayH;
    private int tilesY;

    private static final int TILE_W = 56;
    private static final int TILE_H = 42;
    private static final int HEADER_H = 26;

    public ResonanceScreen() {
        super(Component.literal("Резонанс · 7D"));
    }

    // ─────────────────────────── lifecycle ───────────────────────────

    @Override
    protected void init() {
        if (expr == null) newGame(difficulty);

        panelW = Math.max(150, Math.min(190, width / 4));
        panelX = width - panelW - 4;
        trayH = 74;
        trayY = height - trayH;
        canvasX = 6;
        canvasY = HEADER_H + 4;
        canvasW = panelX - canvasX - 6;
        canvasH = trayY - canvasY - 4;
        tilesY = trayY + 30;

        int bx = width - 4;
        bx -= 62;
        addRenderableWidget(Button.builder(Component.literal("Песочница"), b -> nextSandbox())
                .bounds(bx, 3, 62, 18).build());
        bx -= 66;
        addRenderableWidget(Button.builder(Component.literal("Подсказка"), b -> showHint = !showHint)
                .bounds(bx, 3, 64, 18).build());
        bx -= 60;
        addRenderableWidget(Button.builder(Component.literal("ИГРАТЬ"), b -> newGame(difficulty))
                .bounds(bx, 3, 58, 18).build());
        for (int i = 3; i >= 1; i--) {
            final int lvl = i;
            bx -= 18;
            addRenderableWidget(Button.builder(Component.literal(String.valueOf(i)), b -> {
                difficulty = lvl;
                newGame(lvl);
            }).bounds(bx, 3, 16, 18).build());
        }

        search = new EditBox(font, 8, trayY + 6, 120, 14, Component.literal("поиск"));
        search.setMaxLength(32);
        search.setHint(Component.literal("поиск: масса, Φ, тензор…"));
        search.setResponder(s -> {
            trayScroll = 0;
            refreshTray();
        });
        addRenderableWidget(search);

        refreshTray();
        recompute();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void recompute() {
        analysis = Evaluator.analyze(expr);
        aura = Evaluator.decayedLove(expr, analysis);
    }

    private void newGame(int level) {
        puzzle = GameSolver.generate(level);
        expr = puzzle.expr();
        selectedSlotId = null;
        showHint = false;
        panX = 0;
        panY = 0;
        recompute();
    }

    private void nextSandbox() {
        sandboxIndex++;
        expr = GameSolver.sandbox(sandboxIndex);
        puzzle = null;
        selectedSlotId = null;
        showHint = false;
        panX = 0;
        panY = 0;
        recompute();
    }

    // ─────────────────────────── tray ───────────────────────────

    private void refreshTray() {
        String needle = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Quantity> out = new ArrayList<>();
        for (Quantity q : Quantities.ALL) {
            if (categoryFilter >= 0 && q.category().ordinal() != categoryFilter) continue;
            if (weightFilter >= 0 && q.weight() != weightFilter) continue;
            if (!needle.isEmpty()) {
                String hay = (q.nameRu() + " " + q.nameEn() + " " + q.symbol() + " " + q.unit() + " " + q.id())
                        .toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            out.add(q);
        }
        trayItems = out;
    }

    /** The puzzle target is locked out of the tray for the whole round. */
    private boolean isBlocked(Quantity q) {
        return puzzle != null && puzzle.target() != null && puzzle.target().id().equals(q.id());
    }

    // ─────────────────────────── rendering ───────────────────────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Palette.BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Без этого сброса всё, что рисуем ДО super.render() (шапка/холст/
        // панель счёта/лоток), наследует остаточную тонировку шейдера от
        // того, что рендерилось раньше в этом кадре (в т.ч. авто-блюр/
        // затемнение фона за экраном в 1.21.x) — она никем явно не
        // сбрасывается для "сырых" fill()/drawString(), в отличие от
        // виджетов (Button и т.д.), которые сбрасывают её сами перед своей
        // отрисовкой. Отсюда эффект "через тёмное стекло" именно до
        // super.render() и нормальная яркость после.
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        renderBackground(g, mouseX, mouseY, partialTick);

        drawHeader(g);
        List<FormulaLayout.Box> boxes = drawCanvas(g, mouseX, mouseY);
        drawScorePanel(g);
        drawTray(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        drawDragGhost(g, mouseX, mouseY);
        drawTooltips(g, mouseX, mouseY, boxes);
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(0, 0, width, HEADER_H, Palette.PANEL);
        g.fill(0, HEADER_H - 1, width, HEADER_H, Palette.STROKE);
        g.drawString(font, "РЕЗОНАНС", 8, 4, Palette.CYAN, false);
        tiny(g, "7D конструктор · SI · любовь полей", 8, 15, Palette.TEXT_FAINT);

        String mode = puzzle != null ? ("Уровень " + difficulty) : "Песочница";
        int mx = 8 + font.width("7D конструктор · SI · любовь полей") / 2 + 12;
        tiny(g, mode, mx, 15, Palette.AMBER);
    }

    private List<FormulaLayout.Box> drawCanvas(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, Palette.PANEL_SOFT);
        outline(g, canvasX, canvasY, canvasW, canvasH, Palette.STROKE);

        int fw = FormulaLayout.totalWidth(expr);
        int fh = FormulaLayout.totalHeight(expr);
        int ox = canvasX + Math.max(8, (canvasW - fw) / 2) + panX;
        int oy = canvasY + Math.max(22, (canvasH - fh) / 2) + panY;

        List<FormulaLayout.Box> boxes = FormulaLayout.layout(expr, ox, oy);

        g.enableScissor(canvasX + 1, canvasY + 1, canvasX + canvasW - 1, canvasY + canvasH - 1);

        if (puzzle != null) {
            String goal = "Цель: " + puzzle.target().symbol() + " — " + puzzle.target().nameRu()
                    + "  [" + puzzle.target().vec().format() + "]";
            g.drawString(font, goal, canvasX + 8, canvasY + 6, Palette.AMBER, false);
        }
        if (showHint && puzzle != null) {
            StringBuilder sb = new StringBuilder("Решение 7D-компьютера: ");
            for (String qid : puzzle.sampleSolution().values()) {
                Quantity q = Quantities.get(qid);
                if (q != null) sb.append(q.symbol()).append(' ');
            }
            sb.append("(").append(Math.round(puzzle.bestScore())).append("%)");
            tiny(g, sb.toString(), canvasX + 8, canvasY + 16, Palette.GREEN);
        }

        for (FormulaLayout.Box box : boxes) {
            switch (box.kind()) {
                case SLOT -> drawSlot(g, box, mouseX, mouseY);
                case NUM -> drawNumChip(g, box);
                case OP -> drawOp(g, box);
                case EQ -> drawEq(g, box);
                case FRACTION_LINE -> {
                    int color = conflictColor(box.node().id());
                    g.fill(box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), color);
                }
                case POW_EXP -> tiny(g, box.text(), box.x(), box.y(), Palette.AMBER);
            }
        }

        g.disableScissor();
        return boxes;
    }

    private int conflictColor(String nodeId) {
        for (Analysis.Conflict c : analysis.conflicts) {
            if (c.nodeId().equals(nodeId)) return Palette.ROSE;
        }
        for (Analysis.LocalLove l : analysis.locals) {
            if (l.nodeId().equals(nodeId)) return Palette.love(l.score());
        }
        return Palette.withAlpha(Palette.TEXT, 140);
    }

    private void drawSlot(GuiGraphics g, FormulaLayout.Box box, int mouseX, int mouseY) {
        Expr.Slot slot = (Expr.Slot) box.node();
        Quantity q = Quantities.get(slot.quantityId());
        DimVec required = analysis.required.get(slot.id());
        boolean locked = slot.locked();
        boolean selected = slot.id().equals(selectedSlotId);
        boolean hovered = box.contains(mouseX, mouseY);
        boolean penalised = analysis.lhsExtraSlotIds.contains(slot.id());

        int x = box.x(), y = box.y(), w = box.w(), h = box.h();
        int accent = q != null ? Palette.flavor(q.vec(), q.kind())
                : required != null ? Palette.flavor(required, Quantity.Kind.SCALAR)
                : Palette.TEXT_FAINT;

        g.fill(x, y, x + w, y + h, q != null ? 0xE00A0E18 : 0x900A0E18);

        int border = locked ? Palette.AMBER : (selected ? Palette.CYAN : accent);
        if (penalised) border = Palette.ROSE;
        outline(g, x, y, w, h, border);
        if (locked || selected) outline(g, x - 1, y - 1, w + 2, h + 2, Palette.withAlpha(border, 90));

        if (q != null) {
            int sw = font.width(q.symbol());
            g.drawString(font, q.symbol(), x + (w - sw) / 2, y + 5, locked ? 0xFFFDE68A : accent, false);
            tinyCentered(g, clip(q.nameRu(), 22), x + w / 2, y + 17, Palette.TEXT_DIM);
            drawDimBars(g, x + w / 2 - 13, y + 26, q.vec(), 12);
            Double love = analysis.slotLove.get(slot.id());
            if (love != null) {
                tinyCentered(g, Math.round(love) + "%", x + w / 2, y + h - 8, Palette.love(love));
            }
        } else {
            int sw = font.width("?");
            g.drawString(font, "?", x + (w - sw) / 2, y + 7, Palette.TEXT_FAINT, false);
            if (required != null) {
                drawDimBars(g, x + w / 2 - 13, y + 20, required, 12);
                tinyCentered(g, clip(required.format(), 20), x + w / 2, y + h - 9, Palette.withAlpha(accent, 200));
            }
            Double a = aura.get(slot.id());
            if (a != null && a > 8) {
                g.fill(x + 2, y + 2, x + 5, y + 5, Palette.love(a));
            }
        }

        if (locked) {
            g.fill(x, y - 6, x + 26, y, Palette.AMBER);
            tiny(g, "ФИКС", x + 3, y - 5, 0xFF000000);
        }
        if (penalised) {
            g.fill(x + w - 24, y - 6, x + w, y, Palette.ROSE);
            tiny(g, "-1 ОЧК", x + w - 22, y - 5, 0xFFFFFFFF);
        }

        // remove / clear affordance
        if (!locked && (q != null || slot.isAdded())) {
            boolean hot = mouseX >= x + w - 9 && mouseX < x + w && mouseY >= y && mouseY < y + 9;
            g.fill(x + w - 9, y, x + w, y + 9, hot ? Palette.ROSE : Palette.withAlpha(Palette.ROSE, 110));
            tiny(g, "x", x + w - 6, y + 1, 0xFFFFFFFF);
        }

        // directional drop zones while dragging
        if (dragging && hovered) {
            Manipulate.Direction zone = zoneOf(box, mouseX, mouseY, locked);
            int zc = (zone == Manipulate.Direction.TOP || zone == Manipulate.Direction.BOTTOM)
                    ? Palette.VIOLET : Palette.CYAN;
            switch (zone) {
                case LEFT -> g.fill(x, y, x + w / 4, y + h, Palette.withAlpha(zc, 90));
                case RIGHT -> g.fill(x + w - w / 4, y, x + w, y + h, Palette.withAlpha(zc, 90));
                case TOP -> g.fill(x, y, x + w, y + h / 4, Palette.withAlpha(zc, 90));
                case BOTTOM -> g.fill(x, y + h - h / 4, x + w, y + h, Palette.withAlpha(zc, 90));
                case CENTER -> g.fill(x, y, x + w, y + h, Palette.withAlpha(Palette.GREEN, 70));
            }
            hoverSlotId = slot.id();
            hoverZone = zone;
        }
    }

    private void drawNumChip(GuiGraphics g, FormulaLayout.Box box) {
        int x = box.x(), y = box.y() + 10, w = box.w(), h = box.h() - 20;
        g.fill(x, y, x + w, y + h, 0x30FFFFFF);
        outline(g, x, y, w, h, Palette.STROKE);
        int sw = font.width(box.text());
        g.drawString(font, box.text(), x + (w - sw) / 2, y + h / 2 - 4, Palette.TEXT, false);
    }

    private void drawOp(GuiGraphics g, FormulaLayout.Box box) {
        int color = conflictColor(box.node().id());
        int sw = font.width(box.text());
        g.drawString(font, box.text(), box.x() + (box.w() - sw) / 2, box.y() + 3, color, false);
    }

    private void drawEq(GuiGraphics g, FormulaLayout.Box box) {
        double s = analysis.sD == null ? -1 : analysis.sD;
        int color = s < 0 ? Palette.TEXT_DIM : Palette.love(s);
        int sw = font.width("=");
        g.drawString(font, "=", box.x() + (box.w() - sw) / 2, box.y() + 4, color, false);
    }

    private void drawScorePanel(GuiGraphics g) {
        int x = panelX, y = canvasY, w = panelW, h = canvasH;
        g.fill(x, y, x + w, y + h, Palette.PANEL);
        outline(g, x, y, w, h, Palette.STROKE);

        int cy = y + 6;
        tiny(g, "ОЦЕНКА РЕЗОНАНСА", x + 6, cy, Palette.TEXT_FAINT);
        cy += 9;

        double score = analysis.scoreOr(-1);
        String big = score < 0 ? "—" : String.valueOf(Math.round(score));
        int col = score < 0 ? Palette.TEXT_DIM : Palette.love(score);
        g.pose().pushPose();
        g.pose().translate(x + 6, cy, 0);
        g.pose().scale(2.0f, 2.0f, 1.0f);
        g.drawString(font, big, 0, 0, col, false);
        g.pose().popPose();
        tiny(g, score < 0 ? "неполная" : Analysis.loveLabel(score), x + 44, cy + 6, col);
        cy += 22;

        cy = bar(g, x + 6, cy, w - 12, "S_D размерности", analysis.sD);
        cy = bar(g, x + 6, cy, w - 12, "S_N численный", analysis.sN);

        tiny(g, "S = S_D·S_N/100" + (analysis.lhsPenalty > 0 ? " − " + analysis.lhsPenalty : ""),
                x + 6, cy, Palette.TEXT_FAINT);
        cy += 10;

        if (analysis.lhsPenalty > 0) {
            g.fill(x + 5, cy - 1, x + w - 5, cy + 17, Palette.withAlpha(Palette.ROSE, 40));
            tiny(g, "Штраф " + analysis.lhsPenalty + ": искомое", x + 8, cy + 1, Palette.ROSE);
            tiny(g, "не изолировано в ЛЧ", x + 8, cy + 9, Palette.ROSE);
            cy += 20;
        }
        if (!analysis.cancelledIds.isEmpty()) {
            tiny(g, "Сжато: " + String.join(",", analysis.cancelledIds), x + 6, cy, Palette.CYAN);
            cy += 9;
        }
        if (!analysis.conflicts.isEmpty()) {
            tiny(g, "Конфликт +/−: " + analysis.conflicts.size(), x + 6, cy, Palette.ROSE);
            cy += 9;
        }

        cy += 3;
        tiny(g, "ЛЧ " + (analysis.leftVec == null ? "?" : analysis.leftVec.format()), x + 6, cy, Palette.CYAN);
        cy += 8;
        if (analysis.leftVec != null) {
            drawDimBars(g, x + 6, cy, analysis.leftVec, 14);
            cy += 17;
        }
        tiny(g, "ПЧ " + (analysis.rightVec == null ? "?" : analysis.rightVec.format()), x + 6, cy, Palette.AMBER);
        cy += 8;
        if (analysis.rightVec != null) {
            drawDimBars(g, x + 6, cy, analysis.rightVec, 14);
            cy += 17;
        }

        cy += 2;
        tiny(g, "слоты " + analysis.filledSlots + "/" + analysis.totalSlots, x + 6, cy, Palette.TEXT_FAINT);

        if (analysis.discovery) {
            int by = y + h - 26;
            g.fill(x + 4, by, x + w - 4, by + 22, Palette.withAlpha(Palette.GREEN, 55));
            outline(g, x + 4, by, w - 8, 22, Palette.GREEN);
            g.drawString(font, "ОТКРЫТИЕ!", x + 10, by + 3, Palette.GREEN, false);
            tiny(g, "S ≥ 90 · конфликтов нет", x + 10, by + 14, Palette.TEXT);
        }
    }

    private int bar(GuiGraphics g, int x, int y, int w, String name, Double value) {
        int col = value == null ? Palette.TEXT_FAINT : Palette.love(value);
        tiny(g, name, x, y, Palette.TEXT_DIM);
        String v = value == null ? "—" : String.format(Locale.ROOT, "%.1f", value);
        tiny(g, v, x + w - font.width(v) / 2 - 2, y, col);
        g.fill(x, y + 8, x + w, y + 10, 0x40FFFFFF);
        if (value != null) {
            g.fill(x, y + 8, x + (int) (w * Math.max(0, Math.min(100, value)) / 100.0), y + 10, col);
        }
        return y + 14;
    }

    private void drawTray(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, trayY, width, height, Palette.PANEL);
        g.fill(0, trayY, width, trayY + 1, Palette.STROKE);

        tiny(g, "ЛОТОК ВЕЛИЧИН · бесконечные копии", 134, trayY + 4, Palette.TEXT_FAINT);
        tiny(g, trayItems.size() + " / " + Quantities.ALL.size(), 134, trayY + 13, Palette.TEXT_DIM);

        // weight cycler
        int wx = 232;
        g.fill(wx, trayY + 4, wx + 62, trayY + 16, Palette.PANEL_SOFT);
        outline(g, wx, trayY + 4, 62, 12, Palette.STROKE);
        tiny(g, "вес: " + (weightFilter < 0 ? "все" : String.valueOf(weightFilter)), wx + 4, trayY + 7,
                weightFilter < 0 ? Palette.TEXT_DIM : Palette.weightColor(weightFilter));

        // category cycler
        int cx = 232;
        g.fill(cx, trayY + 18, cx + 62, trayY + 30, Palette.PANEL_SOFT);
        outline(g, cx, trayY + 18, 62, 12, Palette.STROKE);
        String catLabel = categoryFilter < 0 ? "все" : Quantity.Category.values()[categoryFilter].label;
        tiny(g, clip(catLabel, 14), cx + 4, trayY + 21, Palette.CYAN);

        tiny(g, "ЛКМ-тяни: ←→ умножить · ↑↓ дробь · центр — вставить", 302, trayY + 7, Palette.TEXT_FAINT);
        tiny(g, "колесо — прокрутка лотка · ПКМ по полю — сдвиг", 302, trayY + 20, Palette.TEXT_FAINT);

        g.enableScissor(0, tilesY, width, height);
        int x = 6 - trayScroll;
        for (Quantity q : trayItems) {
            if (x + TILE_W > 0 && x < width) drawTile(g, q, x, tilesY, mouseX, mouseY);
            x += TILE_W + 4;
        }
        g.disableScissor();
    }

    private void drawTile(GuiGraphics g, Quantity q, int x, int y, int mouseX, int mouseY) {
        boolean blocked = isBlocked(q);
        boolean hovered = !blocked && mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H;
        int accent = Palette.flavor(q.vec(), q.kind());

        g.fill(x, y, x + TILE_W, y + TILE_H, hovered ? 0xE0161E30 : 0xC00A0E18);
        outline(g, x, y, TILE_W, TILE_H, blocked ? Palette.withAlpha(Palette.AMBER, 120)
                : Palette.withAlpha(accent, hovered ? 255 : 140));

        int sw = font.width(q.symbol());
        g.drawString(font, q.symbol(), x + (TILE_W - sw) / 2, y + 3, blocked ? Palette.TEXT_FAINT : accent, false);
        tinyCentered(g, clip(q.nameRu(), 18), x + TILE_W / 2, y + 15, Palette.TEXT_FAINT);
        drawDimBars(g, x + TILE_W / 2 - 13, y + 25, q.vec(), 12);
        tiny(g, "W" + q.weight(), x + TILE_W - 12, y + 2, Palette.weightColor(q.weight()));

        if (blocked) {
            g.fill(x, y, x + TILE_W, y + TILE_H, 0xB0000000);
            tinyCentered(g, "ЦЕЛЬ", x + TILE_W / 2, y + 12, Palette.AMBER);
            tinyCentered(g, "закрыто", x + TILE_W / 2, y + 22, Palette.TEXT_FAINT);
        }
    }

    private void drawDragGhost(GuiGraphics g, int mouseX, int mouseY) {
        if (!dragging || pressedQuantity == null) return;
        int accent = Palette.flavor(pressedQuantity.vec(), pressedQuantity.kind());
        int x = mouseX + 6, y = mouseY - 10;
        g.fill(x, y, x + 52, y + 22, 0xE00A0E18);
        outline(g, x, y, 52, 22, accent);
        g.drawString(font, pressedQuantity.symbol(), x + 4, y + 3, accent, false);
        tiny(g, clip(pressedQuantity.vec().format(), 14), x + 4, y + 14, Palette.TEXT_FAINT);
    }

    private void drawTooltips(GuiGraphics g, int mouseX, int mouseY, List<FormulaLayout.Box> boxes) {
        if (dragging) {
            if (hoverSlotId != null) {
                String msg = switch (hoverZone) {
                    case LEFT -> "[новый] × блок";
                    case RIGHT -> "блок × [новый]";
                    case TOP -> "[новый] ÷ блок";
                    case BOTTOM -> "блок ÷ [новый]  (дробь)";
                    case CENTER -> "вставить в слот";
                };
                g.fill(mouseX + 6, mouseY + 14, mouseX + 12 + font.width(msg), mouseY + 26, 0xE0000000);
                g.drawString(font, msg, mouseX + 9, mouseY + 16, Palette.CYAN, false);
            }
            return;
        }
        if (mouseY >= tilesY) {
            Quantity q = tileAt(mouseX, mouseY);
            if (q != null) {
                List<Component> lines = List.of(
                        Component.literal(q.symbol() + " — " + q.nameRu()),
                        Component.literal("[" + q.vec().format() + "]  " + q.unit()),
                        Component.literal("вес " + q.weight() + " · " + q.kindLabelRu()));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
            return;
        }
        for (FormulaLayout.Box b : boxes) {
            if (b.kind() == FormulaLayout.BoxKind.SLOT && b.contains(mouseX, mouseY)) {
                Expr.Slot s = (Expr.Slot) b.node();
                Quantity q = Quantities.get(s.quantityId());
                DimVec req = analysis.required.get(s.id());
                List<Component> lines = new ArrayList<>();
                if (q != null) lines.add(Component.literal(q.symbol() + " — " + q.nameRu()));
                else lines.add(Component.literal("пустой слот"));
                if (req != null) lines.add(Component.literal("нужно: " + req.format()));
                if (s.locked()) lines.add(Component.literal("ФИКС · нельзя заменить, можно обвязать"));
                if (analysis.lhsExtraSlotIds.contains(s.id()))
                    lines.add(Component.literal("−1 очко: перенесите в правую часть"));
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    // ─────────────────────────── input ───────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        if (button == 1 && inCanvas(mx, my)) {
            panning = true;
            panStartX = mx;
            panStartY = my;
            panOriginX = panX;
            panOriginY = panY;
            return true;
        }

        // filter cyclers
        if (my >= trayY + 4 && my < trayY + 16 && mx >= 232 && mx < 294) {
            weightFilter = weightFilter >= 3 ? -1 : weightFilter + 1;
            trayScroll = 0;
            refreshTray();
            return true;
        }
        if (my >= trayY + 18 && my < trayY + 30 && mx >= 232 && mx < 294) {
            categoryFilter = categoryFilter >= Quantity.Category.values().length - 1 ? -1 : categoryFilter + 1;
            trayScroll = 0;
            refreshTray();
            return true;
        }

        // tray tiles
        if (my >= tilesY) {
            Quantity q = tileAt(mx, my);
            if (q != null && !isBlocked(q)) {
                pressedQuantity = q;
                dragFromSlotId = null;
                dragging = false;
                pressX = mx;
                pressY = my;
                return true;
            }
            return false;
        }

        // canvas blocks
        if (inCanvas(mx, my)) {
            FormulaLayout.Box box = slotBoxAt(mx, my);
            if (box != null) {
                Expr.Slot slot = (Expr.Slot) box.node();
                boolean hotRemove = mx >= box.x() + box.w() - 9 && mx < box.x() + box.w()
                        && my >= box.y() && my < box.y() + 9;
                if (hotRemove && !slot.locked()) {
                    if (slot.isAdded() && Manipulate.canRemoveNode(expr, slot.id())) {
                        expr = Manipulate.removeNode(expr, slot.id());
                    } else if (slot.quantityId() != null) {
                        expr = Manipulate.setSlotQuantity(expr, slot.id(), null);
                    }
                    selectedSlotId = null;
                    recompute();
                    return true;
                }
                Quantity q = Quantities.get(slot.quantityId());
                if (q != null && !slot.locked()) {
                    pressedQuantity = q;
                    dragFromSlotId = slot.id();
                    dragging = false;
                    pressX = mx;
                    pressY = my;
                    return true;
                }
                if (!slot.locked()) {
                    selectedSlotId = slot.id().equals(selectedSlotId) ? null : slot.id();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (panning) {
            panX = panOriginX + (int) (mx - panStartX);
            panY = panOriginY + (int) (my - panStartY);
            return true;
        }
        if (pressedQuantity != null && !dragging) {
            if (Math.abs(mx - pressX) > 3 || Math.abs(my - pressY) > 3) dragging = true;
        }
        if (dragging) {
            hoverSlotId = null;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (panning && button == 1) {
            panning = false;
            return true;
        }

        if (pressedQuantity != null) {
            Quantity q = pressedQuantity;
            String from = dragFromSlotId;
            boolean wasDragging = dragging;
            pressedQuantity = null;
            dragFromSlotId = null;
            dragging = false;
            hoverSlotId = null;

            if (wasDragging) {
                FormulaLayout.Box target = slotBoxAt(mx, my);
                if (target != null) {
                    Expr.Slot slot = (Expr.Slot) target.node();
                    Manipulate.Direction zone = zoneOf(target, mx, my, slot.locked());
                    if (zone == Manipulate.Direction.CENTER && !slot.locked()) {
                        expr = Manipulate.setSlotQuantity(expr, slot.id(), q.id());
                        if (from != null && !from.equals(slot.id())) {
                            expr = Manipulate.setSlotQuantity(expr, from, null);
                        }
                    } else if (zone != Manipulate.Direction.CENTER) {
                        expr = Manipulate.wrapNode(expr, slot.id(), zone, q.id());
                        if (from != null) expr = Manipulate.setSlotQuantity(expr, from, null);
                    }
                    selectedSlotId = null;
                    recompute();
                    return true;
                }
                // dropped into the void: pull the block out of the equation
                if (from != null && my < trayY) {
                    expr = Manipulate.setSlotQuantity(expr, from, null);
                    recompute();
                }
                return true;
            }

            // plain click on a tray tile → fill the selected or first empty slot
            if (from == null) {
                String targetId = selectedSlotId;
                if (targetId == null) {
                    for (Manipulate.SlotInfo s : Manipulate.collectSlots(expr)) {
                        if (!s.filled() && !s.locked()) {
                            targetId = s.id();
                            break;
                        }
                    }
                }
                if (targetId != null) {
                    expr = Manipulate.setSlotQuantity(expr, targetId, q.id());
                    selectedSlotId = null;
                    recompute();
                }
                return true;
            }
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (my >= trayY) {
            int max = Math.max(0, trayItems.size() * (TILE_W + 4) - width + 12);
            trayScroll = (int) Math.max(0, Math.min(max, trayScroll - scrollY * 34));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (search != null && search.isFocused()) return super.keyPressed(key, scan, mods);
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            newGame(difficulty);
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_H) {
            showHint = !showHint;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private boolean inCanvas(double mx, double my) {
        return mx >= canvasX && mx < canvasX + canvasW && my >= canvasY && my < canvasY + canvasH;
    }

    private FormulaLayout.Box slotBoxAt(double mx, double my) {
        if (!inCanvas(mx, my)) return null;
        int fw = FormulaLayout.totalWidth(expr);
        int fh = FormulaLayout.totalHeight(expr);
        int ox = canvasX + Math.max(8, (canvasW - fw) / 2) + panX;
        int oy = canvasY + Math.max(22, (canvasH - fh) / 2) + panY;
        for (FormulaLayout.Box b : FormulaLayout.layout(expr, ox, oy)) {
            if (b.kind() == FormulaLayout.BoxKind.SLOT && b.contains(mx, my)) return b;
        }
        return null;
    }

    private Manipulate.Direction zoneOf(FormulaLayout.Box box, double mx, double my, boolean locked) {
        double rx = (mx - box.x()) / box.w();
        double ry = (my - box.y()) / box.h();
        if (rx < 0.25) return Manipulate.Direction.LEFT;
        if (rx > 0.75) return Manipulate.Direction.RIGHT;
        if (ry < 0.25) return Manipulate.Direction.TOP;
        if (ry > 0.75) return Manipulate.Direction.BOTTOM;
        return locked ? Manipulate.Direction.BOTTOM : Manipulate.Direction.CENTER;
    }

    private Quantity tileAt(double mx, double my) {
        if (my < tilesY || my >= tilesY + TILE_H) return null;
        int idx = (int) ((mx + trayScroll - 6) / (TILE_W + 4));
        if (idx < 0 || idx >= trayItems.size()) return null;
        double local = (mx + trayScroll - 6) % (TILE_W + 4);
        if (local > TILE_W) return null;
        return trayItems.get(idx);
    }

    private void drawDimBars(GuiGraphics g, int x, int y, DimVec v, int h) {
        double max = 1;
        for (int i = 0; i < DimVec.SIZE; i++) max = Math.max(max, Math.abs(v.get(i)));
        int mid = y + h / 2;
        for (int i = 0; i < DimVec.SIZE; i++) {
            int bx = x + i * 4;
            double val = v.get(i);
            int col = 0xFF000000 | DimVec.AXIS_COLOR[i];
            if (Math.abs(val) < 1e-9) {
                g.fill(bx, mid, bx + 3, mid + 1, Palette.withAlpha(col, 70));
                continue;
            }
            int len = Math.max(1, (int) Math.round(Math.abs(val) / max * (h / 2.0)));
            if (val > 0) g.fill(bx, mid - len, bx + 3, mid, col);
            else g.fill(bx, mid, bx + 3, mid + len, col);
        }
    }

    private void tiny(GuiGraphics g, String text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.5f, 0.5f, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void tinyCentered(GuiGraphics g, String text, int cx, int y, int color) {
        tiny(g, text, cx - font.width(text) / 4, y, color);
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…";
    }
}
