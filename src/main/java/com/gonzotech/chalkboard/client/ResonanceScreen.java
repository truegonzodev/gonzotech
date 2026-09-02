package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.core.Analysis;
import com.gonzotech.chalkboard.core.DimVec;
import com.gonzotech.chalkboard.core.Evaluator;
import com.gonzotech.chalkboard.core.Expr;
import com.gonzotech.chalkboard.core.GameSolver;
import com.gonzotech.chalkboard.core.Manipulate;
import com.gonzotech.chalkboard.core.Quantities;
import com.gonzotech.chalkboard.core.Quantity;
import com.gonzotech.chalkboard.core.Serde;
import com.gonzotech.chalkboard.network.ChalkboardNetwork;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Green chalkboard theme GUI with framed composition, pan/zoom camera navigation,
 * freehand persistent chalk drawing per player, tabbed tray filtering,
 * 2x enlarged circular resonance gauge, custom chalkboard buttons, unified GOST tooltips, and full EN/RU localization.
 */
public class ResonanceScreen extends Screen {

    // ── custom chalkboard button ──
    public static class ChalkButton extends Button {
        public ChalkButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (!this.visible) return;
            boolean hovered = this.isHoveredOrFocused();
            int bg = !this.active ? 0xFF274222 : (hovered ? 0xFF4F7A47 : 0xFF3B6334);
            int stroke = !this.active ? 0xFF666666 : (hovered ? Palette.CYAN : Palette.STROKE);
            int textColor = !this.active ? Palette.TEXT_FAINT : Palette.TEXT;

            int bx = this.getX();
            int by = this.getY();
            int bw = this.width;
            int bh = this.height;

            g.fill(bx, by, bx + bw, by + bh, bg);

            // 1px solid outline (minX, minY, maxX, maxY, color)
            g.fill(bx, by, bx + bw, by + 1, stroke);
            g.fill(bx, by + bh - 1, bx + bw, by + bh, stroke);
            g.fill(bx, by, bx + 1, by + bh, stroke);
            g.fill(bx + bw - 1, by, bx + bw, by + bh, stroke);

            net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
            int tw = font.width(this.getMessage());
            g.drawString(font, this.getMessage(), bx + (bw - tw) / 2, by + (bh - 8) / 2, textColor, false);
        }
    }

    // ── state ──
    private Expr expr;
    private Analysis analysis;
    private Map<String, Double> aura = Map.of();

    private int activeDiscoveryIndex = -1;
    private String titleRu = "...";
    private String titleEn = "...";
    private String targetId = "";
    private String targetSymbol = "?";
    private String targetNameRu = "...";
    private String targetNameEn = "...";
    private String targetUnit = "";
    private int unlockedTrayTier = 0;
    private Set<String> unlockedSecrets = new HashSet<>();
    private boolean cheatsEnabled = false;

    private String selectedSlotId;
    private String hoverSlotId;
    private Manipulate.Direction hoverZone = Manipulate.Direction.CENTER;

    // ── drag ──
    private Quantity pressedQuantity;
    private String dragFromSlotId;
    private boolean dragging;
    private double pressX, pressY;

    // ── camera pan & zoom ──
    private int panX, panY;
    private float zoomScale = 1.0f;
    private boolean panning;
    private double panStartX, panStartY;
    private int panOriginX, panOriginY;

    // ── freehand chalk drawing ──
    public static class ChalkStroke {
        public List<int[]> points = new ArrayList<>(); // Each point is [canvasX, canvasY]
    }

    public static class FreeboardItem {
        public int x; // relative to canvas center
        public int y; // relative to canvas center
        public Expr expr;

        public FreeboardItem(int x, int y, Expr expr) {
            this.x = x;
            this.y = y;
            this.expr = expr;
        }
    }

    private List<ChalkStroke> chalkStrokes = new ArrayList<>();
    private ChalkStroke currentStroke = null;
    private boolean drawingChalk = false;

    private List<FreeboardItem> freeboardExprs = new ArrayList<>();
    private FreeboardItem draggedFreeboardItem = null;
    private int fbDragOffsetX = 0;
    private int fbDragOffsetY = 0;

    // ── tray ──
    private EditBox search;
    private int trayScroll;
    private int activeCategoryTab = 0;
    private int weightFilter = -1;     // -1: все, 0, 1, 2, 3
    private int tierFilter = -1;       // -1: все, 0, 1, 2, 3, 4, 99
    private List<Quantity> trayItems = List.of();

    // ── regions ──
    private int guiX, guiY, guiW, guiH;
    private int canvasX, canvasY, canvasW, canvasH;
    private int panelX, panelW;
    private int trayY, trayH;
    private int tilesY;

    private Button claimButton;
    private Button resetCamButton;
    private Button clearChalkButton;

    private static final int TILE_W = 56;
    private static final int TILE_H = 42;
    private static final int HEADER_H = 26;

    private static final String[] CATEGORY_TAB_KEYS = {
            "gui.gonzotech.chalkboard.cat.all",
            "gui.gonzotech.chalkboard.cat.si",
            "gui.gonzotech.chalkboard.cat.mech",
            "gui.gonzotech.chalkboard.cat.em",
            "gui.gonzotech.chalkboard.cat.thermo",
            "gui.gonzotech.chalkboard.cat.nuclear_quantum",
            "gui.gonzotech.chalkboard.cat.optics",
            "gui.gonzotech.chalkboard.cat.chem",
            "gui.gonzotech.chalkboard.cat.tensors",
            "gui.gonzotech.chalkboard.cat.secret",
            "gui.gonzotech.chalkboard.cat.super_secret"
    };

    private static final int[] TIER_CYCLER_VALUES = {-1, 0, 1, 2, 3, 4, 99};

    public ResonanceScreen() {
        super(Component.translatable("gui.gonzotech.chalkboard.title"));
    }

    // ─────────────────────────── localization helpers ───────────────────────────

    private boolean isEnglish() {
        if (minecraft != null && minecraft.getLanguageManager() != null) {
            String lang = minecraft.getLanguageManager().getSelected();
            return lang != null && !lang.toLowerCase(Locale.ROOT).startsWith("ru");
        }
        return false;
    }

    private String qName(Quantity q) {
        if (q == null) return "";
        return isEnglish() ? q.nameEn() : q.nameRu();
    }

    private String targetName() {
        return isEnglish() ? targetNameEn : targetNameRu;
    }

    private String discoveryTitle() {
        return isEnglish() ? titleEn : titleRu;
    }

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // ─────────────────────────── lifecycle ───────────────────────────

    @Override
    protected void init() {
        // Framed composition inside screen margins
        guiX = Math.max(10, (width - 980) / 2);
        guiY = Math.max(10, (height - 560) / 2);
        guiW = width - guiX * 2;
        guiH = height - guiY * 2;

        panelW = Math.max(180, Math.min(220, guiW / 4));
        panelX = guiX + guiW - panelW - 6;
        trayH = 88;
        trayY = guiY + guiH - trayH - 6;
        canvasX = guiX + 6;
        canvasY = guiY + HEADER_H + 4;
        canvasW = panelX - canvasX - 6;
        canvasH = trayY - canvasY - 4;
        tilesY = trayY + 44;

        // Search Box in tray
        search = new EditBox(font, guiX + 8, trayY + 24, 110, 14, Component.translatable("gui.gonzotech.chalkboard.search_box"));
        search.setMaxLength(32);
        search.setHint(Component.translatable("gui.gonzotech.chalkboard.search_hint"));
        search.setResponder(s -> {
            trayScroll = 0;
            refreshTray();
        });
        addRenderableWidget(search);

        // Claim Discovery Button
        claimButton = new ChalkButton(panelX + 4, canvasY + canvasH - 24, panelW - 8, 20,
                Component.translatable("gui.gonzotech.chalkboard.claim_discovery"), b -> claimDiscovery());
        claimButton.visible = false;
        addRenderableWidget(claimButton);

        // Reset Camera / Focus Button ("К ФОРМУЛЕ")
        resetCamButton = new ChalkButton(canvasX + canvasW - 90, canvasY + 4, 84, 16,
                Component.translatable("gui.gonzotech.chalkboard.focus_formula"), b -> resetCamera());
        addRenderableWidget(resetCamButton);

        // Clear Chalk Button ("ОЧИСТИТЬ МЕЛ")
        clearChalkButton = new ChalkButton(canvasX + canvasW - 182, canvasY + 4, 88, 16,
                Component.translatable("gui.gonzotech.chalkboard.clear_chalk"), b -> clearChalk());
        addRenderableWidget(clearChalkButton);

        updateFromNetwork();
    }

    private void resetCamera() {
        this.panX = 0;
        this.panY = 0;
        this.zoomScale = 1.0f;
    }

    private void clearChalk() {
        this.chalkStrokes.clear();
        this.freeboardExprs.clear();
        autoSave();
    }

    @Override
    public void tick() {
        super.tick();
        if (ChalkboardNetwork.CLIENT_DATA != null) {
            updateFromNetwork();
        }
    }

    private void updateFromNetwork() {
        ChalkboardNetwork.SyncDataPayload data = ChalkboardNetwork.CLIENT_DATA;
        if (data == null) return;
        ChalkboardNetwork.CLIENT_DATA = null;

        boolean indexChanged = data.currentDiscoveryIndex() != this.activeDiscoveryIndex;

        this.activeDiscoveryIndex = data.currentDiscoveryIndex();
        this.titleRu = data.titleRu();
        this.titleEn = data.titleEn();
        this.targetId = data.targetId();
        this.targetSymbol = data.targetSymbol();
        this.targetNameRu = data.targetNameRu();
        this.targetNameEn = data.targetNameEn();
        this.targetUnit = data.targetUnit();
        this.unlockedTrayTier = data.trayTier();
        this.unlockedSecrets = new HashSet<>(data.unlockedSecrets());
        this.cheatsEnabled = data.cheatsEnabled();

        if (indexChanged || expr == null) {
            Expr loaded = Serde.fromJson(data.exprJson());
            this.expr = loaded != null ? loaded : GameSolver.sandbox(0);
            this.selectedSlotId = null;
            this.panX = 0;
            this.panY = 0;
            this.zoomScale = 1.0f;
            deserializeDrawingData(data.drawingJson());
        } else if ((this.chalkStrokes == null || this.chalkStrokes.isEmpty()) && (this.freeboardExprs == null || this.freeboardExprs.isEmpty()) && data.drawingJson() != null && !data.drawingJson().isEmpty()) {
            deserializeDrawingData(data.drawingJson());
        }

        refreshTray();
        recompute();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void recompute() {
        if (expr == null) return;
        boolean isInfinite = activeDiscoveryIndex >= 16;
        analysis = Evaluator.analyze(expr, isInfinite);
        aura = Evaluator.decayedLove(expr, analysis);

        if (claimButton != null) {
            claimButton.visible = analysis.discovery;
        }
    }

    private void claimDiscovery() {
        if (expr == null) return;

        // Submit solution without clearing chalk notes — chalk drawing is persistent across discoveries
        String json = Serde.toJson(expr);
        PacketDistributor.sendToServer(new ChalkboardNetwork.SubmitPayload(activeDiscoveryIndex, json));
    }

    private void autoSave() {
        if (expr != null && activeDiscoveryIndex >= 0) {
            String json = Serde.toJson(expr);
            String drawingJson = serializeDrawingData();
            PacketDistributor.sendToServer(new ChalkboardNetwork.SaveExprPayload(activeDiscoveryIndex, json, drawingJson));
        }
    }

    @Override
    public void onClose() {
        autoSave();
        super.onClose();
    }

    // ─────────────────────────── chalk serialization ───────────────────────────

    private String serializeDrawingData() {
        JsonObject obj = new JsonObject();

        JsonArray strokesArray = new JsonArray();
        if (chalkStrokes != null) {
            for (ChalkStroke stroke : chalkStrokes) {
                JsonArray ptsArr = new JsonArray();
                for (int[] pt : stroke.points) {
                    ptsArr.add(pt[0]);
                    ptsArr.add(pt[1]);
                }
                strokesArray.add(ptsArr);
            }
        }
        obj.add("strokes", strokesArray);

        JsonArray fbArray = new JsonArray();
        if (freeboardExprs != null) {
            for (FreeboardItem item : freeboardExprs) {
                if (item.expr == null) continue;
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("x", item.x);
                itemObj.addProperty("y", item.y);
                itemObj.addProperty("expr", Serde.toJson(item.expr));
                fbArray.add(itemObj);
            }
        }
        obj.add("freeboard", fbArray);

        return obj.toString();
    }

    private void deserializeDrawingData(String json) {
        chalkStrokes.clear();
        freeboardExprs.clear();
        if (json == null || json.trim().isEmpty()) return;

        try {
            String trimmed = json.trim();
            if (trimmed.startsWith("{")) {
                JsonObject obj = JsonParser.parseString(trimmed).getAsJsonObject();
                if (obj.has("strokes")) {
                    JsonArray array = obj.getAsJsonArray("strokes");
                    for (JsonElement elem : array) {
                        if (elem.isJsonArray()) {
                            JsonArray ptArr = elem.getAsJsonArray();
                            ChalkStroke stroke = new ChalkStroke();
                            for (int i = 0; i < ptArr.size() - 1; i += 2) {
                                stroke.points.add(new int[]{ptArr.get(i).getAsInt(), ptArr.get(i + 1).getAsInt()});
                            }
                            if (!stroke.points.isEmpty()) chalkStrokes.add(stroke);
                        }
                    }
                }
                if (obj.has("freeboard")) {
                    JsonArray array = obj.getAsJsonArray("freeboard");
                    for (JsonElement elem : array) {
                        if (elem.isJsonObject()) {
                            JsonObject itemObj = elem.getAsJsonObject();
                            int x = itemObj.get("x").getAsInt();
                            int y = itemObj.get("y").getAsInt();
                            Expr e = Serde.fromJson(itemObj.get("expr").getAsString());
                            if (e != null) {
                                FreeboardItem item = new FreeboardItem(x, y, e);
                                autoEvaluateFreeboard(item);
                                freeboardExprs.add(item);
                            }
                        }
                    }
                }
            } else if (trimmed.startsWith("[")) {
                JsonArray array = JsonParser.parseString(trimmed).getAsJsonArray();
                for (JsonElement elem : array) {
                    if (elem.isJsonArray()) {
                        JsonArray ptArr = elem.getAsJsonArray();
                        ChalkStroke stroke = new ChalkStroke();
                        for (int i = 0; i < ptArr.size() - 1; i += 2) {
                            stroke.points.add(new int[]{ptArr.get(i).getAsInt(), ptArr.get(i + 1).getAsInt()});
                        }
                        if (!stroke.points.isEmpty()) chalkStrokes.add(stroke);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void autoEvaluateFreeboard(FreeboardItem item) {
        if (item == null || item.expr == null) return;

        Expr baseExpr = item.expr;
        if (baseExpr instanceof Expr.Eq eq) {
            baseExpr = eq.left();
        }

        if (baseExpr instanceof Expr.Slot || baseExpr instanceof Expr.Num) {
            item.expr = baseExpr;
            return;
        }

        DimVec netVec = evalExprVec(baseExpr);
        if (netVec != null) {
            Quantity match = null;
            for (Quantity q : Quantities.ALL) {
                if (q.vec().equals(netVec)) {
                    match = q;
                    break;
                }
            }
            if (match != null) {
                item.expr = new Expr.Eq(Expr.nid("eq"), baseExpr, new Expr.Slot(Expr.nid("s"), match.id(), false, false));
            } else {
                item.expr = baseExpr;
            }
        } else {
            item.expr = baseExpr;
        }
    }

    private static DimVec evalExprVec(Expr e) {
        if (e == null) return null;
        return switch (e) {
            case Expr.Slot s -> {
                Quantity q = Quantities.get(s.quantityId());
                yield q != null ? q.vec() : null;
            }
            case Expr.Num n -> DimVec.ZERO;
            case Expr.Pow p -> {
                DimVec b = evalExprVec(p.base());
                yield b != null ? b.scale(p.exp()) : null;
            }
            case Expr.Op o -> {
                DimVec l = evalExprVec(o.left());
                DimVec r = evalExprVec(o.right());
                if (l == null || r == null) yield null;
                if (o.op() == Expr.OpKind.DIV) {
                    yield l.sub(r);
                } else {
                    yield l.add(r);
                }
            }
            case Expr.Eq q -> evalExprVec(q.left());
        };
    }

    private static Expr removeNodeFromExpr(Expr e, String slotId) {
        if (e == null) return null;
        return switch (e) {
            case Expr.Slot s -> s.id().equals(slotId) ? null : s;
            case Expr.Num n -> e;
            case Expr.Pow p -> {
                Expr b = removeNodeFromExpr(p.base(), slotId);
                yield b != null ? Expr.Pow.of(b, p.exp()) : null;
            }
            case Expr.Op o -> {
                Expr l = removeNodeFromExpr(o.left(), slotId);
                Expr r = removeNodeFromExpr(o.right(), slotId);
                if (l == null) yield r;
                if (r == null) yield l;
                yield Expr.Op.of(o.op(), l, r);
            }
            case Expr.Eq eq -> {
                Expr l = removeNodeFromExpr(eq.left(), slotId);
                Expr r = removeNodeFromExpr(eq.right(), slotId);
                if (l == null) yield null;
                if (r == null) yield l;
                yield Expr.Eq.of(l, r);
            }
        };
    }

    // ─────────────────────────── tray ───────────────────────────

    private void refreshTray() {
        String needle = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Quantity> out = new ArrayList<>();
        for (Quantity q : Quantities.ALL) {
            // Hide pure numbers except Pi
            if (q.kind() == Quantity.Kind.NUMBER && !q.id().equals("num_pi")) continue;

            // Check if player has unlocked this quantity
            boolean unlocked = cheatsEnabled || q.tier() <= unlockedTrayTier || unlockedSecrets.contains(q.id());
            if (!unlocked) continue;

            // Category Tab filter
            if (!matchCategoryTab(q, activeCategoryTab)) continue;

            // Tier cycler filter
            if (tierFilter >= 0 && q.tier() != tierFilter) continue;

            // Weight cycler filter
            if (weightFilter >= 0 && q.weight() != weightFilter) continue;

            // Search text filter
            if (!needle.isEmpty()) {
                String hay = (q.nameRu() + " " + q.nameEn() + " " + q.symbol() + " " + q.unit() + " " + q.id())
                        .toLowerCase(Locale.ROOT);
                if (!hay.contains(needle)) continue;
            }
            out.add(q);
        }
        trayItems = out;
    }

    private boolean matchCategoryTab(Quantity q, int tab) {
        return switch (tab) {
            case 0 -> true;
            case 1 -> q.category() == Quantity.Category.SI;
            case 2 -> q.category() == Quantity.Category.MECHANICS;
            case 3 -> q.category() == Quantity.Category.EM;
            case 4 -> q.category() == Quantity.Category.THERMO;
            case 5 -> q.category() == Quantity.Category.NUCLEAR || q.category() == Quantity.Category.QUANTUM;
            case 6 -> q.category() == Quantity.Category.OPTICS;
            case 7 -> q.category() == Quantity.Category.CHEMISTRY;
            case 8 -> q.category() == Quantity.Category.TENSORS || q.category() == Quantity.Category.FIELDS;
            case 9 -> q.tier() == 4;
            case 10 -> q.tier() == 99;
            default -> true;
        };
    }

    private boolean isBlocked(Quantity q) {
        return targetId != null && targetId.equals(q.id());
    }

    // ─────────────────────────── rendering ───────────────────────────

    @Override
    public void renderMenuBackground(GuiGraphics g) {
    }

    @Override
    public void renderMenuBackground(GuiGraphics g, int x, int y, int width, int height) {
    }

    @Override
    public void renderTransparentBackground(GuiGraphics g) {
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // 1. Draw Outer Dark Void Background
        g.fill(0, 0, width, height, 0xFF121B10);

        // 2. Draw Outer Wooden Board Frame
        g.fill(guiX - 4, guiY - 4, guiX + guiW + 4, guiY + guiH + 4, 0xFF3E2723);
        outline(g, guiX - 4, guiY - 4, guiW + 8, guiH + 8, Palette.STROKE);

        // 3. Fill Main Chalkboard Surface
        g.fill(guiX, guiY, guiX + guiW, guiY + guiH, Palette.BG);
        outline(g, guiX, guiY, guiW, guiH, Palette.STROKE);

        drawHeader(g);

        List<FormulaLayout.Box> boxes = List.of();
        if (expr != null) {
            boxes = drawCanvas(g, mouseX, mouseY);
            drawScorePanel(g);
        }

        drawTray(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        drawDragGhost(g, mouseX, mouseY);
        drawTooltips(g, mouseX, mouseY, boxes);
    }

    private void drawHeader(GuiGraphics g) {
        g.fill(guiX, guiY, guiX + guiW, guiY + HEADER_H, Palette.PANEL);
        g.fill(guiX, guiY + HEADER_H - 1, guiX + guiW, guiY + HEADER_H, Palette.STROKE);
        g.drawString(font, tr("gui.gonzotech.chalkboard.title"), guiX + 8, guiY + 4, Palette.TEXT, false);

        String subtitle = discoveryTitle();
        tiny(g, subtitle, guiX + 8, guiY + 15, Palette.AMBER);

        if (cheatsEnabled) {
            tiny(g, tr("gui.gonzotech.chalkboard.cheats_on"), guiX + guiW - 220, guiY + 15, Palette.ROSE);
        }
    }

    private List<FormulaLayout.Box> drawCanvas(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(canvasX, canvasY, canvasX + canvasW, canvasY + canvasH, Palette.PANEL_SOFT);
        outline(g, canvasX, canvasY, canvasW, canvasH, Palette.STROKE);

        int centerX = canvasX + canvasW / 2 + panX;
        int centerY = canvasY + canvasH / 2 + panY;

        g.enableScissor(canvasX + 1, canvasY + 1, canvasX + canvasW - 1, canvasY + canvasH - 1);

        // 1. Draw Persistent Freehand Chalk Strokes
        drawChalkStrokes(g, centerX, centerY);

        // 1b. Render Freeboard Sandbox Chalk Items
        for (FreeboardItem fbItem : freeboardExprs) {
            drawFreeboardItem(g, fbItem, centerX, centerY, font, mouseX, mouseY);
        }

        // 2. Draw Goal and Hints
        String goal = tr("gui.gonzotech.chalkboard.goal_prefix") + " " + targetSymbol + " — " + targetName() + " [" + targetUnit + "]";
        g.drawString(font, goal, canvasX + 8, canvasY + 6, Palette.AMBER, false);

        String controlsHint = String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.controls_hint"), Math.round(zoomScale * 100));
        tiny(g, controlsHint, canvasX + 8, canvasY + canvasH - 10, Palette.TEXT_FAINT);

        // 3. Render Formula with Zoom and Pan Matrix Stack
        g.pose().pushPose();
        g.pose().translate(centerX, centerY, 0);
        g.pose().scale(zoomScale, zoomScale, 1.0f);
        g.pose().translate(-centerX, -centerY, 0);

        int fw = FormulaLayout.totalWidth(expr);
        int fh = FormulaLayout.totalHeight(expr);
        int ox = centerX - fw / 2;
        int oy = centerY - fh / 2;

        List<FormulaLayout.Box> boxes = FormulaLayout.layout(expr, ox, oy);

        for (FormulaLayout.Box box : boxes) {
            switch (box.kind()) {
                case SLOT -> drawSlot(g, box, mouseX, mouseY, centerX, centerY);
                case NUM -> drawNumChip(g, box);
                case OP -> drawOp(g, box);
                case EQ -> drawEq(g, box);
                case FRACTION_LINE -> {
                    int color = conflictColor(box.node().id());
                    int lineY = box.y() + box.h() / 2 - 1;
                    g.fill(box.x(), lineY, box.x() + box.w(), lineY + 2, color);
                }
                case POW_EXP -> tiny(g, box.text(), box.x(), box.y(), Palette.AMBER);
            }
        }

        g.pose().popPose();

        g.disableScissor();
        return boxes;
    }

    private void drawChalkStrokes(GuiGraphics g, int centerX, int centerY) {
        for (ChalkStroke stroke : chalkStrokes) {
            List<int[]> pts = stroke.points;
            for (int i = 0; i < pts.size() - 1; i++) {
                int[] p1 = pts.get(i);
                int[] p2 = pts.get(i + 1);

                int x1 = (int) (centerX + p1[0] * zoomScale);
                int y1 = (int) (centerY + p1[1] * zoomScale);
                int x2 = (int) (centerX + p2[0] * zoomScale);
                int y2 = (int) (centerY + p2[1] * zoomScale);

                drawChalkLine(g, x1, y1, x2, y2, 0xEEF8FAFC);
            }
        }
    }

    private void drawChalkLine(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int currX = x1;
        int currY = y1;

        while (true) {
            if (currX >= canvasX + 1 && currX < canvasX + canvasW - 1 &&
                currY >= canvasY + 1 && currY < canvasY + canvasH - 1) {
                g.fill(currX, currY, currX + 2, currY + 2, color);
            }
            if (currX == x2 && currY == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                currX += sx;
            }
            if (e2 < dx) {
                err += dx;
                currY += sy;
            }
        }
    }

    private void drawFreeboardItem(GuiGraphics g, FreeboardItem item, int centerX, int centerY, Font font, int mouseX, int mouseY) {
        if (item == null || item.expr == null) return;

        g.pose().pushPose();
        int screenX = centerX + (int) (item.x * zoomScale);
        int screenY = centerY + (int) (item.y * zoomScale);
        g.pose().translate(screenX, screenY, 0);
        float fbScale = zoomScale * 0.8f;
        g.pose().scale(fbScale, fbScale, 1.0f);

        List<FormulaLayout.Box> boxes = FormulaLayout.layout(item.expr, 0, 0);

        int relMx = (int) ((mouseX - centerX) / zoomScale);
        int relMy = (int) ((mouseY - centerY) / zoomScale);
        boolean isDraggingAnything = dragging || draggedFreeboardItem != null || pressedQuantity != null;

        for (FormulaLayout.Box b : boxes) {
            if (b.kind() == FormulaLayout.BoxKind.SLOT) {
                Expr.Slot s = (Expr.Slot) b.node();
                Quantity q = Quantities.get(s.quantityId());
                int bx = b.x();
                int by = b.y();
                int bw = b.w();
                int bh = b.h();

                int frameColor = 0xFFFFFFFF; // Pure white chalk frame
                int bgFill = 0x802A4823;    // Dark green chalkboard fill

                g.fill(bx, by, bx + bw, by + bh, bgFill);
                g.fill(bx, by, bx + bw, by + 1, frameColor);
                g.fill(bx, by + bh - 1, bx + bw, by + bh, frameColor);
                g.fill(bx, by, bx + 1, by + bh, frameColor);
                g.fill(bx + bw - 1, by, bx + bw, by + bh, frameColor);

                // Small white 'x' delete button in top-right corner with opaque white fill and dark green 'x'
                boolean xHover = isMouseOverFreeboardX(item, b, relMx, relMy);
                int xBg = xHover ? Palette.ROSE : 0xFFFFFFFF;
                int xFg = xHover ? 0xFFFFFFFF : 0xFF2A4823;
                g.fill(bx + bw - 8, by, bx + bw, by + 8, xBg);
                tiny(g, "x", bx + bw - 6, by + 1, xFg);

                if (q != null) {
                    int sw = font.width(q.symbol());
                    g.drawString(font, q.symbol(), bx + (bw - sw) / 2, by + 4, 0xFFFFFFFF, false);
                    tinyCentered(g, clip(qName(q), 14), bx + bw / 2, by + 18, 0xD0FFFFFF);
                    drawPureWhiteDimBars(g, bx + bw / 2 - 13, by + 28, q.vec(), 12);
                } else {
                    g.drawString(font, "?", bx + (bw - font.width("?")) / 2, by + bh / 2 - 4, 0xFFFFFFFF, false);
                }

                // Zone highlight when dragging over another freeboard slot (never on self)
                if (item != draggedFreeboardItem && isDraggingAnything && isMouseOverFreeboardBox(item, b, relMx, relMy)) {
                    double localMx = (relMx - item.x) / 0.8;
                    double localMy = (relMy - item.y) / 0.8;
                    Manipulate.Direction zone = zoneOf(b, localMx, localMy, false);
                    drawZoneHighlight(g, b, zone);
                }
            } else if (b.kind() == FormulaLayout.BoxKind.OP || b.kind() == FormulaLayout.BoxKind.EQ) {
                String symbol = b.text() != null ? b.text() : "=";
                g.drawString(font, symbol, b.x() + (b.w() - font.width(symbol)) / 2, b.y() + (b.h() - font.lineHeight) / 2, 0xFFFFFFFF, false);
            } else if (b.kind() == FormulaLayout.BoxKind.FRACTION_LINE) {
                int lineY = b.y() + b.h() / 2 - 1;
                g.fill(b.x(), lineY, b.x() + b.w(), lineY + 2, 0xFFFFFFFF);
            } else if (b.kind() == FormulaLayout.BoxKind.NUM) {
                String txt = b.text() != null ? b.text() : "1";
                g.drawString(font, txt, b.x() + (b.w() - font.width(txt)) / 2, b.y() + (b.h() - font.lineHeight) / 2, 0xFFFFFFFF, false);
            }
        }

        g.pose().popPose();
    }

    private boolean isMouseOverFreeboardX(FreeboardItem item, FormulaLayout.Box b, int relMx, int relMy) {
        double localMx = (relMx - item.x) / 0.8;
        double localMy = (relMy - item.y) / 0.8;
        return localMx >= b.x() + b.w() - 8 && localMx < b.x() + b.w() && localMy >= b.y() && localMy < b.y() + 8;
    }

    private boolean isMouseOverFreeboardBox(FreeboardItem item, FormulaLayout.Box b, int relMx, int relMy) {
        if (item == draggedFreeboardItem) return false;
        double localMx = (relMx - item.x) / 0.8;
        double localMy = (relMy - item.y) / 0.8;
        return localMx >= b.x() && localMx < b.x() + b.w() && localMy >= b.y() && localMy < b.y() + b.h();
    }

    private void drawZoneHighlight(GuiGraphics g, FormulaLayout.Box box, Manipulate.Direction zone) {
        if (zone == null) return;
        int x = box.x(), y = box.y(), w = box.w(), h = box.h();
        int zc = (zone == Manipulate.Direction.TOP || zone == Manipulate.Direction.BOTTOM)
                ? Palette.VIOLET : Palette.CYAN;
        switch (zone) {
            case LEFT -> g.fill(x, y, x + w / 4, y + h, Palette.withAlpha(zc, 120));
            case RIGHT -> g.fill(x + w - w / 4, y, x + w, y + h, Palette.withAlpha(zc, 120));
            case TOP -> g.fill(x, y, x + w, y + h / 4, Palette.withAlpha(zc, 120));
            case BOTTOM -> g.fill(x, y + h - h / 4, x + w, y + h, Palette.withAlpha(zc, 120));
            case CENTER -> g.fill(x, y, x + w, y + h, Palette.withAlpha(Palette.GREEN, 100));
        }
    }

    private void drawPureWhiteDimBars(GuiGraphics g, int x, int y, DimVec vec, int width) {
        if (vec == null) return;
        double[] dims = vec.raw();
        int px = x;
        for (double d : dims) {
            if (Math.abs(d) > 0.01) {
                int barH = Math.min(8, Math.max(2, (int) (Math.abs(d) * 2)));
                int barY = d > 0 ? y - barH : y + 1;
                g.fill(px, barY, px + 2, barY + barH, 0xFFFFFFFF);
            }
            px += 3;
        }
    }

    private int conflictColor(String nodeId) {
        if (analysis == null) return Palette.withAlpha(Palette.TEXT, 140);
        for (Analysis.Conflict c : analysis.conflicts) {
            if (c.nodeId().equals(nodeId)) return Palette.ROSE;
        }
        for (Analysis.LocalLove l : analysis.locals) {
            if (l.nodeId().equals(nodeId)) return Palette.love(l.score());
        }
        return Palette.withAlpha(Palette.TEXT, 140);
    }

    private void drawSlot(GuiGraphics g, FormulaLayout.Box box, int mouseX, int mouseY, int centerX, int centerY) {
        Expr.Slot slot = (Expr.Slot) box.node();
        Quantity q = Quantities.get(slot.quantityId());
        boolean locked = slot.locked();
        boolean selected = slot.id().equals(selectedSlotId);

        double unscaledMx = centerX + (mouseX - centerX) / zoomScale;
        double unscaledMy = centerY + (mouseY - centerY) / zoomScale;
        boolean hovered = box.contains(unscaledMx, unscaledMy);

        boolean penalised = analysis != null && (analysis.lhsExtraSlotIds.contains(slot.id()) || analysis.rhsExtraSlotIds.contains(slot.id()));

        int x = box.x(), y = box.y(), w = box.w(), h = box.h();
        int accent = q != null ? Palette.flavor(q.vec(), q.kind())
                : Palette.TEXT_FAINT;

        g.fill(x, y, x + w, y + h, q != null ? 0xF03B6334 : 0xD0274222);

        int border = locked ? Palette.AMBER : (selected ? Palette.CYAN : accent);
        if (penalised) border = Palette.ROSE;

        // Draw thick 2px solid border without fade / transparency
        if (locked || selected || penalised) {
            g.fill(x - 1, y - 1, x + w + 1, y + 1, border);
            g.fill(x - 1, y + h - 1, x + w + 1, y + h + 1, border);
            g.fill(x - 1, y - 1, x + 1, y + h + 1, border);
            g.fill(x + w - 1, y - 1, x + w + 1, y + h + 1, border);
        } else {
            outline(g, x, y, w, h, border);
        }

        if (q != null) {
            int sw = font.width(q.symbol());
            g.drawString(font, q.symbol(), x + (w - sw) / 2, y + 5, locked ? 0xFFFDE68A : accent, false);
            tinyCentered(g, clip(qName(q), 22), x + w / 2, y + 17, Palette.TEXT_DIM);
            drawDimBars(g, x + w / 2 - 13, y + 26, q.vec(), 12);
            if (analysis != null) {
                Double love = analysis.slotLove.get(slot.id());
                if (love != null) {
                    tinyCentered(g, Math.round(love) + "%", x + w / 2, y + h - 8, Palette.love(love));
                }
            }
        } else {
            int sw = font.width("?");
            g.drawString(font, "?", x + (w - sw) / 2, y + (h - 9) / 2, Palette.TEXT_FAINT, false);
            Double a = aura.get(slot.id());
            if (a != null && a > 8) {
                g.fill(x + 2, y + 2, x + 5, y + 5, Palette.love(a));
            }
        }

        if (locked) {
            String lockText = tr("gui.gonzotech.chalkboard.fixed");
            int tabW = font.width(lockText) + 6;
            // Tab aligned flush with outer 2px border edge
            g.fill(x - 1, y - 8, x - 1 + tabW, y - 1, Palette.AMBER);
            g.drawString(font, lockText, x + 2, y - 7, 0xFF000000, false);
        }
        if (penalised) {
            g.fill(x + w - 24, y - 6, x + w, y, Palette.ROSE);
            tiny(g, "-1 ОЧК", x + w - 22, y - 5, 0xFFFFFFFF);
        }

        // remove / clear affordance
        if (!locked && (q != null || slot.isAdded())) {
            boolean hot = unscaledMx >= x + w - 9 && unscaledMx < x + w && unscaledMy >= y && unscaledMy < y + 9;
            g.fill(x + w - 9, y, x + w, y + 9, hot ? Palette.ROSE : Palette.withAlpha(Palette.ROSE, 110));
            tiny(g, "x", x + w - 6, y + 1, 0xFFFFFFFF);
        }

        // directional drop zones while dragging
        boolean isDraggingAnything = dragging || draggedFreeboardItem != null || pressedQuantity != null;
        if (isDraggingAnything && hovered && !locked) {
            Manipulate.Direction zone = zoneOf(box, unscaledMx, unscaledMy, locked);
            drawZoneHighlight(g, box, zone);
            hoverSlotId = slot.id();
            hoverZone = zone;
        }
    }

    private void drawNumChip(GuiGraphics g, FormulaLayout.Box box) {
        int x = box.x(), y = box.y() + 10, w = box.w(), h = box.h() - 20;
        g.fill(x, y, x + w, y + h, 0x50FFFFFF);
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
        double s = (analysis == null || analysis.sD == null) ? -1 : analysis.sD;
        int color = s < 0 ? Palette.TEXT_DIM : Palette.love(s);
        int sw = font.width("=");
        g.drawString(font, "=", box.x() + (box.w() - sw) / 2, box.y() + 4, color, false);
    }

    private void drawScorePanel(GuiGraphics g) {
        if (analysis == null) return;
        int x = panelX, y = canvasY, w = panelW, h = canvasH;
        g.fill(x, y, x + w, y + h, Palette.PANEL);
        outline(g, x, y, w, h, Palette.STROKE);

        int cy = y + 8;
        String header = tr("gui.gonzotech.chalkboard.resonance_assessment");
        int htw = font.width(header);
        g.drawString(font, header, x + (w - htw) / 2, cy, Palette.TEXT_FAINT, false);

        // Circular Resonance Ring Gauge (2x larger diameter = 90px)
        int circleCx = x + w / 2;
        int circleCy = cy + 52;
        drawResonanceCircleGauge(g, circleCx, circleCy, analysis.scoreOr(-1));

        cy = circleCy + 50;

        // Dimensional convergence
        cy = bar(g, x + 8, cy, w - 16, tr("gui.gonzotech.chalkboard.s_d_dimension"), analysis.sD);

        // Numerical layer
        cy = bar(g, x + 8, cy, w - 16, tr("gui.gonzotech.chalkboard.s_n_numeric"), analysis.sN);

        int totalPenalty = analysis.lhsPenalty + analysis.rhsPenalty;
        if (totalPenalty > 0) {
            g.fill(x + 6, cy - 1, x + w - 6, cy + 22, Palette.withAlpha(Palette.ROSE, 40));
            g.drawString(font, String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.penalty"), totalPenalty), x + 8, cy + 2, Palette.ROSE, false);
            String penaltyDesc = "не изолировано (ЛЧ: " + analysis.lhsPenalty + ", ПЧ: " + analysis.rhsPenalty + ")";
            g.drawString(font, penaltyDesc, x + 8, cy + 12, Palette.ROSE, false);
            cy += 26;
        }

        if (!analysis.cancelledIds.isEmpty()) {
            g.drawString(font, String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.compressed"), String.join(",", analysis.cancelledIds)), x + 8, cy, Palette.CYAN, false);
            cy += 12;
        }
        if (!analysis.conflicts.isEmpty()) {
            g.drawString(font, String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.conflicts"), analysis.conflicts.size()), x + 8, cy, Palette.ROSE, false);
            cy += 12;
        }

        cy += 4;
        String lqStr = tr("gui.gonzotech.chalkboard.lq_label");
        g.drawString(font, lqStr, x + 8, cy, Palette.CYAN, false);
        cy += 12;
        if (analysis.leftVec != null) {
            drawDimBars(g, x + 8, cy, analysis.leftVec, 12);
            cy += 16;
        }

        String rqStr = tr("gui.gonzotech.chalkboard.rq_label");
        g.drawString(font, rqStr, x + 8, cy, Palette.AMBER, false);
        cy += 12;
        if (analysis.rightVec != null) {
            drawDimBars(g, x + 8, cy, analysis.rightVec, 12);
            cy += 16;
        }

        cy += 4;
        g.drawString(font, String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.slots"), analysis.filledSlots, analysis.totalSlots), x + 8, cy, Palette.TEXT_FAINT, false);
    }

    private void drawResonanceCircleGauge(GuiGraphics g, int cx, int cy, double score) {
        int rOut = 45; // 2x larger diameter (90px)
        int rIn = 39;  // 6px thickness
        double frac = score < 0 ? 0.0 : Math.max(0.0, Math.min(1.0, score / 100.0));
        int scoreColor = score < 0 ? Palette.TEXT_DIM : Palette.love(score);

        for (int dy = -rOut; dy <= rOut; dy++) {
            for (int dx = -rOut; dx <= rOut; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 >= rIn * rIn && d2 <= rOut * rOut) {
                    // Calculate angle from top (-90 degrees / -PI/2 radians)
                    double angle = Math.atan2(dy, dx) + Math.PI / 2.0;
                    if (angle < 0) angle += 2.0 * Math.PI;

                    int pixelColor;
                    if (score >= 0 && angle <= frac * 2.0 * Math.PI) {
                        pixelColor = scoreColor;
                    } else {
                        pixelColor = 0xFF2A3A26; // Dark green/grey ring background
                    }
                    g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, pixelColor);
                }
            }
        }

        // Large score number in center (text size untouched)
        String big = score < 0 ? "—" : String.valueOf(Math.round(score));
        g.pose().pushPose();
        g.pose().translate(cx, cy - 8, 0);
        g.pose().scale(1.8f, 1.8f, 1.0f);
        int tw = font.width(big);
        g.drawString(font, big, -tw / 2, 0, scoreColor, false);
        g.pose().popPose();

        // Status label below number inside circle (text size untouched)
        String status = score < 0
                ? tr("gui.gonzotech.chalkboard.resonance_incomplete")
                : (score < 20 ? tr("gui.gonzotech.chalkboard.resonance_weak")
                : (score < 50 ? tr("gui.gonzotech.chalkboard.resonance_moderate")
                : (score < 80 ? tr("gui.gonzotech.chalkboard.resonance_strong")
                : tr("gui.gonzotech.chalkboard.resonance_full"))));

        g.pose().pushPose();
        g.pose().translate(cx, cy + 10, 0);
        g.pose().scale(0.6f, 0.6f, 1.0f);
        int stw = font.width(status);
        g.drawString(font, status, -stw / 2, 0, scoreColor, false);
        g.pose().popPose();
    }

    private int bar(GuiGraphics g, int x, int y, int w, String name, Double value) {
        int col = value == null ? Palette.TEXT_FAINT : Palette.love(value);
        g.drawString(font, name, x, y, Palette.TEXT, false);
        String v = value == null ? "—" : String.format(Locale.ROOT, "%.1f", value);
        int vw = font.width(v);
        g.drawString(font, v, x + w - vw, y, col, false);

        g.fill(x, y + 11, x + w, y + 15, 0x40FFFFFF);
        if (value != null) {
            g.fill(x, y + 11, x + (int) (w * Math.max(0, Math.min(100, value)) / 100.0), y + 15, col);
        }
        return y + 20;
    }

    private void drawTray(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(guiX + 4, trayY, guiX + guiW - 4, guiY + guiH - 4, Palette.PANEL);
        g.fill(guiX + 4, trayY, guiX + guiW - 4, trayY + 1, Palette.STROKE);

        // 1. Draw Category Tabs Bar
        drawCategoryTabs(g, mouseX, mouseY);

        // 2. Filter Controls Bar
        int wx = guiX + 124;
        g.fill(wx, trayY + 23, wx + 54, trayY + 37, Palette.PANEL_SOFT);
        outline(g, wx, trayY + 23, 54, 14, Palette.STROKE);
        String wText = weightFilter < 0 ? tr("gui.gonzotech.chalkboard.weight_all") : String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.weight_val"), weightFilter);
        tiny(g, wText, wx + 4, trayY + 26, weightFilter < 0 ? Palette.TEXT_DIM : Palette.weightColor(weightFilter));

        int tx = guiX + 182;
        g.fill(tx, trayY + 23, tx + 54, trayY + 37, Palette.PANEL_SOFT);
        outline(g, tx, trayY + 23, 54, 14, Palette.STROKE);
        String tText = tierFilter < 0 ? tr("gui.gonzotech.chalkboard.tier_all") : String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.tier_val"), tierFilter);
        tiny(g, tText, tx + 4, trayY + 26, Palette.CYAN);

        tiny(g, String.format(Locale.ROOT, tr("gui.gonzotech.chalkboard.tray_available"), trayItems.size()), guiX + 242, trayY + 26, Palette.TEXT_FAINT);

        // 3. Draw Tiles Grid
        g.enableScissor(guiX + 4, tilesY, guiX + guiW - 4, guiY + guiH - 4);
        int x = guiX + 6 - trayScroll;
        for (Quantity q : trayItems) {
            if (x + TILE_W > guiX && x < guiX + guiW) drawTile(g, q, x, tilesY, mouseX, mouseY);
            x += TILE_W + 4;
        }
        g.disableScissor();
    }

    private void drawCategoryTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tx = guiX + 6;
        int ty = trayY + 3;
        int th = 16;

        for (int i = 0; i < CATEGORY_TAB_KEYS.length; i++) {
            String label = tr(CATEGORY_TAB_KEYS[i]);
            int tw = font.width(label) / 2 + 8;
            boolean active = (i == activeCategoryTab);
            boolean hovered = mouseX >= tx && mouseX < tx + tw && mouseY >= ty && mouseY < ty + th;

            int bg = active ? Palette.STROKE : (hovered ? 0xFF4F7A47 : Palette.PANEL_SOFT);
            int textCol = active ? 0xFF000000 : (hovered ? Palette.CYAN : Palette.TEXT_DIM);

            g.fill(tx, ty, tx + tw, ty + th, bg);
            outline(g, tx, ty, tw, th, active ? Palette.CYAN : Palette.STROKE);
            tiny(g, label, tx + 4, ty + 4, textCol);

            tx += tw + 3;
        }
    }

    private void drawTile(GuiGraphics g, Quantity q, int x, int y, int mouseX, int mouseY) {
        boolean blocked = isBlocked(q);
        boolean hovered = !blocked && mouseX >= x && mouseX < x + TILE_W && mouseY >= y && mouseY < y + TILE_H;
        int accent = Palette.flavor(q.vec(), q.kind());

        g.fill(x, y, x + TILE_W, y + TILE_H, hovered ? 0xFF4F7A47 : 0xFF3B6334);
        outline(g, x, y, TILE_W, TILE_H, blocked ? Palette.withAlpha(Palette.AMBER, 120)
                : Palette.withAlpha(accent, hovered ? 255 : 180));

        int sw = font.width(q.symbol());
        g.drawString(font, q.symbol(), x + (TILE_W - sw) / 2, y + 3, blocked ? Palette.TEXT_FAINT : accent, false);
        tinyCentered(g, clip(qName(q), 18), x + TILE_W / 2, y + 15, Palette.TEXT_FAINT);
        drawDimBars(g, x + TILE_W / 2 - 13, y + 25, q.vec(), 12);
        tiny(g, "T" + q.tier(), x + TILE_W - 12, y + 2, Palette.tierColor(q.tier()));

        if (blocked) {
            g.fill(x, y, x + TILE_W, y + TILE_H, 0xB0000000);
            tinyCentered(g, tr("gui.gonzotech.chalkboard.target_tile"), x + TILE_W / 2, y + 12, Palette.AMBER);
            tinyCentered(g, tr("gui.gonzotech.chalkboard.locked_tile"), x + TILE_W / 2, y + 22, Palette.TEXT_FAINT);
        }
    }

    private void drawDragGhost(GuiGraphics g, int mouseX, int mouseY) {
        if (!dragging || pressedQuantity == null) return;
        int accent = Palette.flavor(pressedQuantity.vec(), pressedQuantity.kind());
        int x = mouseX + 6, y = mouseY - 10;
        g.fill(x, y, x + 52, y + 22, 0xFF3B6334);
        outline(g, x, y, 52, 22, accent);
        g.drawString(font, pressedQuantity.symbol(), x + 4, y + 3, accent, false);
        tiny(g, clip(pressedQuantity.unit(), 14), x + 4, y + 14, Palette.TEXT_FAINT);
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
                List<Component> lines = buildQuantityTooltip(q, isEnglish(), false, false);
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
            return;
        }
        for (FormulaLayout.Box b : boxes) {
            int centerX = canvasX + canvasW / 2 + panX;
            int centerY = canvasY + canvasH / 2 + panY;
            double unscaledMx = centerX + (mouseX - centerX) / zoomScale;
            double unscaledMy = centerY + (mouseY - centerY) / zoomScale;

            if (b.kind() == FormulaLayout.BoxKind.SLOT && b.contains(unscaledMx, unscaledMy)) {
                Expr.Slot s = (Expr.Slot) b.node();
                Quantity q = Quantities.get(s.quantityId());
                boolean penalised = analysis != null && (analysis.lhsExtraSlotIds.contains(s.id()) || analysis.rhsExtraSlotIds.contains(s.id()));

                if (q != null) {
                    List<Component> lines = buildQuantityTooltip(q, isEnglish(), s.locked(), penalised);
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                } else {
                    List<Component> lines = new ArrayList<>();
                    int titleColor = Palette.brightAccent(Palette.TEXT_FAINT);
                    lines.add(Component.translatable("gui.gonzotech.chalkboard.empty_slot").setStyle(Style.EMPTY.withColor(titleColor)));
                    if (s.locked()) {
                        lines.add(Component.translatable("gui.gonzotech.chalkboard.fixed_slot_tooltip").withStyle(ChatFormatting.GRAY));
                    }
                    if (penalised) {
                        lines.add(Component.translatable("gui.gonzotech.chalkboard.penalty_slot_tooltip").withStyle(ChatFormatting.RED));
                    }
                    g.renderComponentTooltip(font, lines, mouseX, mouseY);
                }
                return;
            }
        }
    }

    // ─────────────────────────── unified GOST tooltip builder ───────────────────────────

    private List<Component> buildQuantityTooltip(Quantity q, boolean isEn, boolean locked, boolean penalised) {
        List<Component> lines = new ArrayList<>();

        int frameColor = Palette.flavor(q.vec(), q.kind());
        int titleColor = Palette.brightAccent(frameColor);

        // Title Line: <Symbol> — <Name>
        Component titleComp = Component.literal(q.symbol() + " — " + qName(q))
                .setStyle(Style.EMPTY.withColor(titleColor));
        lines.add(titleComp);

        // Line 1: Tier: X
        Component tierComp = Component.literal(tr("gui.gonzotech.chalkboard.tooltip_tier"))
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(q.tier())).setStyle(Style.EMPTY.withColor(Palette.tierColor(q.tier()))));
        lines.add(tierComp);

        // Line 2: Blank line
        lines.add(Component.empty());

        // Line 3: Type: Scalar
        String kindText = capitalize(q.kindLabel(isEn));
        Component typeComp = Component.literal(tr("gui.gonzotech.chalkboard.tooltip_type"))
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(kindText).withStyle(ChatFormatting.WHITE));
        lines.add(typeComp);

        // Line 4: Dimension: [<UnitSymbol>] — <UnitFullName>
        Component dimComp;
        if (q.id().equals("molar_rad_density")) {
            Component dimPrefixComp = Component.literal(tr("gui.gonzotech.chalkboard.tooltip_unit"))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal((isEn ? "Boltzmann " : "Больцман ") + "[").withStyle(ChatFormatting.WHITE));
            Component btzSymComp = Component.literal("Бц").setStyle(Style.EMPTY.withColor(blendedVectorColor(DimVec.of(0, 0, 0, 0, 4, 1, 0))));
            Component dimSuffixComp = Component.literal("] — " + unitFullName(q, isEn))
                    .withStyle(ChatFormatting.WHITE);
            dimComp = Component.empty().append(dimPrefixComp).append(btzSymComp).append(dimSuffixComp);
        } else {
            Component dimPrefixComp = Component.literal(tr("gui.gonzotech.chalkboard.tooltip_unit"))
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("[").withStyle(ChatFormatting.WHITE));

            Component unitSymComp = buildUnitSymbolComponent(q);
            Component dimSuffixComp = Component.literal("] — " + unitFullName(q, isEn))
                    .withStyle(ChatFormatting.WHITE);

            dimComp = Component.empty().append(dimPrefixComp).append(unitSymComp).append(dimSuffixComp);
        }
        lines.add(dimComp);

        // Canvas overlay badges
        if (locked) {
            lines.add(Component.translatable("gui.gonzotech.chalkboard.fixed_slot_tooltip").withStyle(ChatFormatting.GRAY));
        }
        if (penalised) {
            lines.add(Component.translatable("gui.gonzotech.chalkboard.penalty_slot_tooltip").withStyle(ChatFormatting.RED));
        }

        return lines;
    }

    private Component buildUnitSymbolComponent(Quantity q) {
        String u = q.unit() == null ? "" : q.unit().trim();
        if (u.isEmpty() || u.equals("1") || u.equals("безразм.")) {
            return Component.literal("1").withStyle(ChatFormatting.WHITE);
        }

        MutableComponent comp = Component.empty();
        StringBuilder curToken = new StringBuilder();

        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c == '/' || c == '·' || c == '*' || c == '(' || c == ')') {
                if (curToken.length() > 0) {
                    comp.append(colorTokenComponent(curToken.toString(), q));
                    curToken.setLength(0);
                }
                comp.append(Component.literal(String.valueOf(c)).withStyle(ChatFormatting.GRAY));
            } else {
                curToken.append(c);
            }
        }
        if (curToken.length() > 0) {
            comp.append(colorTokenComponent(curToken.toString(), q));
        }
        return comp;
    }

    private Component colorTokenComponent(String token, Quantity q) {
        int color;
        if (token.startsWith("м") && !token.startsWith("моль")) {
            color = 0xFF38BDF8; // Length - Blue
        } else if (token.startsWith("кг")) {
            color = 0xFFFB923C; // Mass - Orange
        } else if (token.startsWith("с") || token.startsWith("сек")) {
            color = 0xFFC084FC; // Time - Lilac
        } else if (token.startsWith("А")) {
            color = 0xFFFDE047; // Current - Yellow
        } else if (token.startsWith("К") && !token.startsWith("Кл")) {
            color = 0xFFFCA5A5; // Temperature - Red
        } else if (token.startsWith("моль")) {
            color = 0xFF4ADE80; // Amount - Green
        } else if (token.startsWith("кд")) {
            color = 0xFFF472B6; // Luminous - Pink
        } else if (token.startsWith("Бц")) {
            color = blendedVectorColor(DimVec.of(0, 0, 0, 0, 4, 1, 0)); // Boltzmann unit color
        } else {
            color = blendedVectorColor(q.vec()); // Derived unit blended RGB
        }
        return Component.literal(token).setStyle(Style.EMPTY.withColor(color));
    }

    private static int blendedVectorColor(DimVec vec) {
        int[] axisColors = new int[]{
                0xFF38BDF8, // L - Blue
                0xFFFB923C, // m - Orange
                0xFFC084FC, // t - Lilac
                0xFFFDE047, // I - Yellow
                0xFFFCA5A5, // T - Red
                0xFF4ADE80, // n - Green
                0xFFF472B6  // Iv - Pink
        };

        double totalWeight = 0;
        double sumR = 0, sumG = 0, sumB = 0;

        for (int i = 0; i < DimVec.SIZE; i++) {
            double w = Math.abs(vec.get(i));
            if (w > 1e-9) {
                totalWeight += w;
                int col = axisColors[i];
                sumR += w * ((col >> 16) & 0xFF);
                sumG += w * ((col >> 8) & 0xFF);
                sumB += w * (col & 0xFF);
            }
        }

        if (totalWeight < 1e-9) {
            return 0xFFFFFFFF; // White for scalar/dimensionless
        }

        int r = (int) Math.round(sumR / totalWeight);
        int g = (int) Math.round(sumG / totalWeight);
        int b = (int) Math.round(sumB / totalWeight);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private String unitFullName(Quantity q, boolean isEn) {
        String u = q.unit() == null ? "" : q.unit().trim();
        if (u.isEmpty() || u.equals("1") || u.equals("безразм.")) {
            return isEn ? "Dimensionless" : "Безразмерная величина";
        }
        return switch (u) {
            case "м" -> isEn ? "Meters" : "Метры";
            case "кг" -> isEn ? "Kilograms" : "Килограммы";
            case "с" -> isEn ? "Seconds" : "Секунды";
            case "А" -> isEn ? "Amperes" : "Амперы";
            case "К" -> isEn ? "Kelvins" : "Кельвины";
            case "моль" -> isEn ? "Moles" : "Моли";
            case "кд" -> isEn ? "Candelas" : "Канделы";
            case "м/с" -> isEn ? "Meters per second" : "Метры в секунду";
            case "м/с²" -> isEn ? "Meters per second squared" : "Метры на секунду в квадрате";
            case "м²" -> isEn ? "Square meters" : "Квадратные метры";
            case "м³" -> isEn ? "Cubic meters" : "Кубические метры";
            case "кг/м³" -> isEn ? "Kilograms per cubic meter" : "Килограммы на кубический метр";
            case "Н" -> isEn ? "Newtons" : "Ньютоны";
            case "Дж" -> isEn ? "Joules" : "Джоули";
            case "Вт" -> isEn ? "Watts" : "Ватты";
            case "Па" -> isEn ? "Pascals" : "Паскали";
            case "Кл" -> isEn ? "Coulombs" : "Кулоны";
            case "В" -> isEn ? "Volts" : "Вольты";
            case "Ф" -> isEn ? "Farads" : "Фарады";
            case "Ом" -> isEn ? "Ohms" : "Омы";
            case "См" -> isEn ? "Siemens" : "Сименсы";
            case "Вб" -> isEn ? "Webers" : "Веберы";
            case "Тл" -> isEn ? "Teslas" : "Теслы";
            case "Гн" -> isEn ? "Henries" : "Генри";
            case "Гц" -> isEn ? "Hertz" : "Герцы";
            case "Бк" -> isEn ? "Becquerels" : "Беккерели";
            case "Гр" -> isEn ? "Grays" : "Грэи";
            case "Зв" -> isEn ? "Sieverts" : "Зиверты";
            case "кат" -> isEn ? "Katals" : "Каталы";
            case "лм" -> isEn ? "Lumens" : "Люмены";
            case "лк" -> isEn ? "Lux" : "Люксы";
            case "Н·с" -> isEn ? "Newton-seconds" : "Ньютоны в секунду";
            case "рад/с" -> isEn ? "Radians per second" : "Радианы в секунду";
            case "рад/с²" -> isEn ? "Radians per second squared" : "Радиан в секунду за секунду";
            case "Н/м" -> isEn ? "Newtons per meter" : "Ньютонов на метр";
            case "Па·с" -> isEn ? "Pascal-seconds" : "Паскали в секунду";
            case "м²/с" -> isEn ? "Square meters per second" : "Квадратный метр на секунду";
            case "кг/с" -> isEn ? "Kilograms per second" : "Килограмм в секунду";
            case "м³/с" -> isEn ? "Cubic meters per second" : "Кубических метров в секунду";
            case "м³/кг" -> isEn ? "Cubic meters per kilogram" : "Кубических метров на килограмм";
            case "кг/м" -> isEn ? "Kilograms per meter" : "Килограмм на метр";
            case "кг/м²" -> isEn ? "Kilograms per square meter" : "Килограмм на метр квадратный";
            case "м⁻¹" -> isEn ? "Inverse meters" : "Обратный метр";
            case "К⁻¹" -> isEn ? "Inverse kelvins" : "Обратный кельвин";
            case "Вт/м²" -> isEn ? "Watts per square meter" : "Ватты на метр квадратный";
            case "К/м" -> isEn ? "Kelvins per meter" : "Кельвин на метр";

            case "моль·кд/(Тл·К·с³)" -> isEn ? "Mole-candelas per Tesla-Kelvin-cubic second" : "Моль-Канделл на Тесла-Кельвин в секунду кубическую";
            case "с/(Гн·м·Бц)" -> isEn ? "Seconds per Henry-meter-Boltzmann" : "Секунда на Генри-метр-Больцман";
            case "Вт·К" -> isEn ? "Watt-Kelvins" : "Ватт-Кельвин";
            case "А²/(м³·моль)" -> isEn ? "Amperes squared per cubic meter-mole" : "Ампер квадрат на метр кубический-моль";
            case "Н³/(А·К·моль)" -> isEn ? "Newtons cubed per Ampere-Kelvin-mole" : "Ньютон кубический на ампер-кельвин-моль";
            case "См²·м·с/Бк²" -> isEn ? "Siemens squared-meter-seconds per Becquerel squared" : "Сименс квадратный-метр-секунда на Беккерель квадратный";
            case "См²·м·с/Бц²" -> isEn ? "Siemens squared-meter-seconds per Boltzmann squared" : "Сименс квадратный-метр-секунда на Больцман квадратный";
            case "Дж·кд²/(м²·К·моль)" -> isEn ? "Joules-candelas squared per square meter-Kelvin-mole" : "Джоуль-кандела квадратная на метр квадратный-кельвин-моль";
            case "В³·А/(Гн·с·К)" -> isEn ? "Volts cubed-amperes per Henry-second-Kelvin" : "Вольт кубический-ампер на Генри-секунду-кельвин";
            case "В·А²·м⁵/(с·моль)" -> isEn ? "Volts-amperes squared-meters to fifth per second-mole" : "Вольт-ампер квадратный-метр в пятой степени на секунду-моль";
            case "Гн/с" -> isEn ? "Henries per second" : "Генри на секунду";
            case "Па·с/м³" -> isEn ? "Pascal-seconds per cubic meter" : "Паскаль-секунда на метр кубический";
            case "Дж/с²" -> isEn ? "Joules per second squared" : "Джоуль на секунду в квадрате";
            case "К⁴·моль" -> isEn ? "Kelvins to fourth per mole" : "Кельвин в четвёртой степени на моль";

            case "Дж/кг" -> isEn ? "Joules per kilogram" : "Джоули на килограмм";
            case "Дж/К" -> isEn ? "Joules per Kelvin" : "Джоули на Кельвин";
            case "Дж/(кг·К)" -> isEn ? "Joules per kilogram-Kelvin" : "Джоули на килограмм-Кельвин";
            case "Вт/(м·К)" -> isEn ? "Watts per meter-Kelvin" : "Ватты на метр-Кельвин";
            case "В/м" -> isEn ? "Volts per meter" : "Вольты на метр";
            case "А/м" -> isEn ? "Amperes per meter" : "Амперы на метр";
            case "Кл/м³" -> isEn ? "Coulombs per cubic meter" : "Кулоны на кубический метр";
            case "Дж/м³" -> isEn ? "Joules per cubic meter" : "Джоули на кубический метр";
            case "Дж/моль" -> isEn ? "Joules per mole" : "Джоули на моль";
            case "Дж/(моль·К)" -> isEn ? "Joules per mole-Kelvin" : "Джоули на моль-Кельвин";
            case "Вт/(м²·К⁴)" -> isEn ? "Watts per square meter-Kelvin to fourth" : "Ватты на квадратный метр-Кельвин в четвёртой степени";
            case "м³/(кг·с²)" -> isEn ? "Cubic meters per kilogram-second squared" : "Кубические метры на килограмм-секунду в квадрате";
            case "Дж·с" -> isEn ? "Joule-seconds" : "Джоуль-секунды";
            case "Кл/моль" -> isEn ? "Coulombs per mole" : "Кулоны на моль";
            case "дптр" -> isEn ? "Diopters" : "Диоптрии";
            case "ГэВ" -> isEn ? "Gigaelectronvolts" : "Гигаэлектронвольты";
            case "В·м" -> isEn ? "Volt-meters" : "Вольт-метры";
            case "А/м²" -> isEn ? "Amperes per square meter" : "Амперы на квадратный метр";
            case "Ф/м" -> isEn ? "Farads per meter" : "Фарады на метр";
            case "Гн/м" -> isEn ? "Henries per meter" : "Генри на метр";
            case "См/м" -> isEn ? "Siemens per meter" : "Сименсы на метр";
            case "Ом·м" -> isEn ? "Ohm-meters" : "Ом-метры";
            case "Кл·м" -> isEn ? "Coulomb-meters" : "Кулон-метры";
            case "А·м²" -> isEn ? "Ampere-square meters" : "Ампер-квадратные метры";
            case "Кл/м²" -> isEn ? "Coulombs per square meter" : "Кулоны на квадратный метр";
            case "Вб/м" -> isEn ? "Webers per meter" : "Веберы на метр";
            case "Гн⁻¹" -> isEn ? "Inverse henries" : "Обратный генри";
            case "м⁻²·с⁻¹" -> isEn ? "Inverse square meter-seconds" : "Обратный квадратный метр-секунда";
            case "м⁻³" -> isEn ? "Inverse cubic meters" : "Обратный кубический метр";
            case "м⁻³·с⁻¹" -> isEn ? "Inverse cubic meter-seconds" : "Обратный кубический метр-секунда";
            case "м⁻²" -> isEn ? "Inverse square meters" : "Обратный квадратный метр";
            case "с⁻¹" -> isEn ? "Inverse seconds" : "Обратная секунда";
            case "кд/м²" -> isEn ? "Candelas per square meter" : "Канделы на квадратный метр";
            case "кг/моль" -> isEn ? "Kilograms per mole" : "Килограммы на моль";
            case "м³/моль" -> isEn ? "Cubic meters per mole" : "Кубические метры на моль";
            case "моль/м³" -> isEn ? "Moles per cubic meter" : "Моли на кубический метр";
            case "моль⁻¹" -> isEn ? "Inverse moles" : "Обратный моль";
            case "рад" -> isEn ? "Radians" : "Радианы";
            case "ср" -> isEn ? "Steradians" : "Стерадианы";
            case "кг·м/с" -> isEn ? "Kilogram-meters per second" : "Килограмм-метры в секунду";
            case "Н·м" -> isEn ? "Newton-meters" : "Ньютон-метры";
            case "кг·м²" -> isEn ? "Kilogram-square meters" : "Килограмм-квадратные метры";
            default -> u;
        };
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return "";
        return str.substring(0, 1).toUpperCase(Locale.ROOT) + str.substring(1);
    }

    // ─────────────────────────── input ───────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        // Canvas interaction
        if (inCanvas(mx, my)) {
            int centerX = canvasX + canvasW / 2 + panX;
            int centerY = canvasY + canvasH / 2 + panY;

            // 1. Freehand Chalk Drawing (Alt + LMB or Shift + LMB)
            if (button == 0 && (hasAltDown() || hasShiftDown())) {
                drawingChalk = true;
                currentStroke = new ChalkStroke();
                int px = (int) ((mx - centerX) / zoomScale);
                int py = (int) ((my - centerY) / zoomScale);
                currentStroke.points.add(new int[]{px, py});
                chalkStrokes.add(currentStroke);
                return true;
            }

            // 2. Camera Panning (MMB / button 2 or RMB / button 1)
            if (button == 1 || button == 2) {
                panning = true;
                panStartX = mx;
                panStartY = my;
                panOriginX = panX;
                panOriginY = panY;
                return true;
            }

            // 2b. Check Freeboard Chalk Sandbox Item clicks
            int relMx = (int) ((mx - centerX) / zoomScale);
            int relMy = (int) ((my - centerY) / zoomScale);

            FreeboardItem hitItem = null;
            FormulaLayout.Box hitBox = null;

            for (FreeboardItem item : freeboardExprs) {
                List<FormulaLayout.Box> boxes = FormulaLayout.layout(item.expr, 0, 0);
                for (FormulaLayout.Box b : boxes) {
                    if (isMouseOverFreeboardBox(item, b, relMx, relMy)) {
                        hitItem = item;
                        hitBox = b;
                        break;
                    }
                }
                if (hitItem != null) break;
            }

            if (hitItem != null && hitBox != null && button == 0) {
                // Delete 'x' button check
                if (hitBox.kind() == FormulaLayout.BoxKind.SLOT && isMouseOverFreeboardX(hitItem, hitBox, relMx, relMy)) {
                    hitItem.expr = removeNodeFromExpr(hitItem.expr, hitBox.node().id());
                    if (hitItem.expr == null) {
                        freeboardExprs.remove(hitItem);
                    } else {
                        autoEvaluateFreeboard(hitItem);
                    }
                    autoSave();
                    return true;
                }

                boolean isAssembled = hitItem.expr instanceof Expr.Eq;

                if (isAssembled) {
                    draggedFreeboardItem = hitItem;
                    int itemW = (int) (FormulaLayout.totalWidth(hitItem.expr) * 0.8f);
                    int itemH = (int) (FormulaLayout.totalHeight(hitItem.expr) * 0.8f);
                    fbDragOffsetX = itemW / 2;
                    fbDragOffsetY = itemH / 2;
                    hitItem.x = relMx - fbDragOffsetX;
                    hitItem.y = relMy - fbDragOffsetY;
                    return true;
                } else {
                    if (hitBox.kind() == FormulaLayout.BoxKind.OP || hitBox.kind() == FormulaLayout.BoxKind.FRACTION_LINE) {
                        draggedFreeboardItem = hitItem;
                        int itemW = (int) (FormulaLayout.totalWidth(hitItem.expr) * 0.8f);
                        int itemH = (int) (FormulaLayout.totalHeight(hitItem.expr) * 0.8f);
                        fbDragOffsetX = itemW / 2;
                        fbDragOffsetY = itemH / 2;
                        hitItem.x = relMx - fbDragOffsetX;
                        hitItem.y = relMy - fbDragOffsetY;
                        return true;
                    } else if (hitBox.kind() == FormulaLayout.BoxKind.SLOT) {
                        Expr slotNode = hitBox.node();
                        if (slotNode instanceof Expr.Slot s && s.quantityId() != null) {
                            String qId = s.quantityId();
                            hitItem.expr = removeNodeFromExpr(hitItem.expr, slotNode.id());
                            if (hitItem.expr == null) {
                                freeboardExprs.remove(hitItem);
                            } else {
                                autoEvaluateFreeboard(hitItem);
                            }
                            int slotW = (int) (FormulaLayout.SLOT_W * 0.8f);
                            int slotH = (int) (FormulaLayout.SLOT_H * 0.8f);
                            fbDragOffsetX = slotW / 2;
                            fbDragOffsetY = slotH / 2;
                            draggedFreeboardItem = new FreeboardItem(relMx - fbDragOffsetX, relMy - fbDragOffsetY, Expr.Slot.of(qId));
                            freeboardExprs.add(draggedFreeboardItem);
                            autoSave();
                            return true;
                        }
                    }
                }
            }

            // 3. Canvas Formula Blocks
            if (expr != null) {
                FormulaLayout.Box box = slotBoxAt(mx, my);
                if (box != null) {
                    Expr.Slot slot = (Expr.Slot) box.node();
                    double unscaledMx = centerX + (mx - centerX) / zoomScale;
                    double unscaledMy = centerY + (my - centerY) / zoomScale;

                    boolean hotRemove = unscaledMx >= box.x() + box.w() - 9 && unscaledMx < box.x() + box.w()
                            && unscaledMy >= box.y() && unscaledMy < box.y() + 9;
                    if (hotRemove && !slot.locked()) {
                        if (slot.isAdded() && Manipulate.canRemoveNode(expr, slot.id())) {
                            expr = Manipulate.removeNode(expr, slot.id());
                        } else if (slot.quantityId() != null) {
                            expr = Manipulate.setSlotQuantity(expr, slot.id(), null);
                        }
                        selectedSlotId = null;
                        autoSave();
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
                } else if (button == 0) {
                    // Clicking empty canvas space also initiates camera pan
                    panning = true;
                    panStartX = mx;
                    panStartY = my;
                    panOriginX = panX;
                    panOriginY = panY;
                    return true;
                }
            }
        }

        // Category tab clicks
        if (my >= trayY + 3 && my < trayY + 19) {
            int tx = guiX + 6;
            for (int i = 0; i < CATEGORY_TAB_KEYS.length; i++) {
                int tw = font.width(tr(CATEGORY_TAB_KEYS[i])) / 2 + 8;
                if (mx >= tx && mx < tx + tw) {
                    activeCategoryTab = i;
                    trayScroll = 0;
                    refreshTray();
                    return true;
                }
                tx += tw + 3;
            }
        }

        // Filter Cyclers clicks
        if (my >= trayY + 23 && my < trayY + 37 && mx >= guiX + 124 && mx < guiX + 178) {
            weightFilter = weightFilter >= 3 ? -1 : weightFilter + 1;
            trayScroll = 0;
            refreshTray();
            return true;
        }
        if (my >= trayY + 23 && my < trayY + 37 && mx >= guiX + 182 && mx < guiX + 236) {
            tierFilter = nextCyclerTier(tierFilter);
            trayScroll = 0;
            refreshTray();
            return true;
        }

        // Tray tiles
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

        return false;
    }

    private int nextCyclerTier(int current) {
        for (int i = 0; i < TIER_CYCLER_VALUES.length; i++) {
            if (TIER_CYCLER_VALUES[i] == current) {
                return TIER_CYCLER_VALUES[(i + 1) % TIER_CYCLER_VALUES.length];
            }
        }
        return -1;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drawingChalk && currentStroke != null) {
            int centerX = canvasX + canvasW / 2 + panX;
            int centerY = canvasY + canvasH / 2 + panY;
            int px = (int) ((mx - centerX) / zoomScale);
            int py = (int) ((my - centerY) / zoomScale);

            List<int[]> pts = currentStroke.points;
            if (pts.isEmpty()) {
                pts.add(new int[]{px, py});
            } else {
                int[] last = pts.get(pts.size() - 1);
                int ldx = px - last[0];
                int ldy = py - last[1];
                if (ldx * ldx + ldy * ldy >= 4) {
                    pts.add(new int[]{px, py});
                }
            }
            return true;
        }
        if (draggedFreeboardItem != null) {
            int centerX = canvasX + canvasW / 2 + panX;
            int centerY = canvasY + canvasH / 2 + panY;
            int relMx = (int) ((mx - centerX) / zoomScale);
            int relMy = (int) ((my - centerY) / zoomScale);
            draggedFreeboardItem.x = relMx - fbDragOffsetX;
            draggedFreeboardItem.y = relMy - fbDragOffsetY;
            return true;
        }
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
        if (drawingChalk) {
            drawingChalk = false;
            currentStroke = null;
            autoSave();
            return true;
        }
        if (draggedFreeboardItem != null) {
            FreeboardItem item = draggedFreeboardItem;
            draggedFreeboardItem = null;

            int centerX = canvasX + canvasW / 2 + panX;
            int centerY = canvasY + canvasH / 2 + panY;
            double unscaledMx = centerX + (mx - centerX) / zoomScale;
            double unscaledMy = centerY + (my - centerY) / zoomScale;
            int relMx = (int) ((mx - centerX) / zoomScale);
            int relMy = (int) ((my - centerY) / zoomScale);

            // Extract primary quantity ID from dragged item
            String qId = null;
            if (item.expr instanceof Expr.Slot s) qId = s.quantityId();
            else if (item.expr instanceof Expr.Eq eq && eq.right() instanceof Expr.Slot rs) qId = rs.quantityId();

            // 1. Check drop over Main Equation slot
            FormulaLayout.Box mainTarget = slotBoxAt(mx, my);
            if (mainTarget != null && mainTarget.node() instanceof Expr.Slot slot && !slot.locked() && qId != null) {
                Manipulate.Direction zone = zoneOf(mainTarget, unscaledMx, unscaledMy, slot.locked());
                if (zone == Manipulate.Direction.CENTER) {
                    expr = Manipulate.setSlotQuantity(expr, slot.id(), qId);
                } else {
                    expr = Manipulate.wrapNode(expr, slot.id(), zone, qId);
                }
                freeboardExprs.remove(item);
                autoSave();
                recompute();
                return true;
            }

            // 2. Check drop over another Freeboard Item
            FreeboardItem targetFbItem = null;
            FormulaLayout.Box targetFbBox = null;
            for (FreeboardItem other : freeboardExprs) {
                if (other == item) continue;
                List<FormulaLayout.Box> boxes = FormulaLayout.layout(other.expr, 0, 0);
                for (FormulaLayout.Box b : boxes) {
                    if (isMouseOverFreeboardBox(other, b, relMx, relMy)) {
                        targetFbItem = other;
                        targetFbBox = b;
                        break;
                    }
                }
                if (targetFbItem != null) break;
            }

            if (targetFbItem != null && targetFbBox != null) {
                Expr targetNode = targetFbBox.node();
                double localMx = (relMx - targetFbItem.x) / 0.8;
                double localMy = (relMy - targetFbItem.y) / 0.8;
                Manipulate.Direction zone = zoneOf(targetFbBox, localMx, localMy, false);

                Expr itemBase = item.expr instanceof Expr.Eq eq ? eq.left() : item.expr;
                Expr targetBase = targetFbItem.expr instanceof Expr.Eq eq ? eq.left() : targetFbItem.expr;

                if (zone == Manipulate.Direction.CENTER && targetNode instanceof Expr.Slot s) {
                    if (qId != null) targetFbItem.expr = Manipulate.setSlotQuantity(targetFbItem.expr, s.id(), qId);
                } else if (zone == Manipulate.Direction.LEFT) {
                    targetFbItem.expr = Expr.Op.of(Expr.OpKind.MUL, itemBase, targetBase);
                } else if (zone == Manipulate.Direction.RIGHT) {
                    targetFbItem.expr = Expr.Op.of(Expr.OpKind.MUL, targetBase, itemBase);
                } else if (zone == Manipulate.Direction.TOP) {
                    targetFbItem.expr = Expr.Op.of(Expr.OpKind.DIV, itemBase, targetBase);
                } else if (zone == Manipulate.Direction.BOTTOM) {
                    targetFbItem.expr = Expr.Op.of(Expr.OpKind.DIV, targetBase, itemBase);
                }

                autoEvaluateFreeboard(targetFbItem);
                freeboardExprs.remove(item);
                autoSave();
                return true;
            }

            autoEvaluateFreeboard(item);
            autoSave();
            return true;
        }
        if (panning) {
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

            if (wasDragging && expr != null) {
                FormulaLayout.Box target = slotBoxAt(mx, my);
                int centerX = canvasX + canvasW / 2 + panX;
                int centerY = canvasY + canvasH / 2 + panY;
                double unscaledMx = centerX + (mx - centerX) / zoomScale;
                double unscaledMy = centerY + (my - centerY) / zoomScale;
                int relMx = (int) ((mx - centerX) / zoomScale);
                int relMy = (int) ((my - centerY) / zoomScale);

                if (target != null) {
                    Expr.Slot slot = (Expr.Slot) target.node();
                    Manipulate.Direction zone = zoneOf(target, unscaledMx, unscaledMy, slot.locked());
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
                    autoSave();
                    recompute();
                    return true;
                }

                // Check drop over a Freeboard Item
                FreeboardItem targetFbItem = null;
                FormulaLayout.Box targetFbBox = null;
                for (FreeboardItem other : freeboardExprs) {
                    List<FormulaLayout.Box> boxes = FormulaLayout.layout(other.expr, 0, 0);
                    for (FormulaLayout.Box b : boxes) {
                        if (isMouseOverFreeboardBox(other, b, relMx, relMy)) {
                            targetFbItem = other;
                            targetFbBox = b;
                            break;
                        }
                    }
                    if (targetFbItem != null) break;
                }

                if (targetFbItem != null && targetFbBox != null) {
                    Expr targetNode = targetFbBox.node();
                    double localMx = (relMx - targetFbItem.x) / 0.8;
                    double localMy = (relMy - targetFbItem.y) / 0.8;
                    Manipulate.Direction zone = zoneOf(targetFbBox, localMx, localMy, false);

                    Expr targetBase = targetFbItem.expr instanceof Expr.Eq eq ? eq.left() : targetFbItem.expr;

                    if (zone == Manipulate.Direction.CENTER && targetNode instanceof Expr.Slot s) {
                        targetFbItem.expr = Manipulate.setSlotQuantity(targetFbItem.expr, s.id(), q.id());
                    } else if (zone == Manipulate.Direction.LEFT) {
                        targetFbItem.expr = Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.of(q.id()), targetBase);
                    } else if (zone == Manipulate.Direction.RIGHT) {
                        targetFbItem.expr = Expr.Op.of(Expr.OpKind.MUL, targetBase, Expr.Slot.of(q.id()));
                    } else if (zone == Manipulate.Direction.TOP) {
                        targetFbItem.expr = Expr.Op.of(Expr.OpKind.DIV, Expr.Slot.of(q.id()), targetBase);
                    } else if (zone == Manipulate.Direction.BOTTOM) {
                        targetFbItem.expr = Expr.Op.of(Expr.OpKind.DIV, targetBase, Expr.Slot.of(q.id()));
                    }

                    autoEvaluateFreeboard(targetFbItem);
                    if (from != null) {
                        expr = Manipulate.setSlotQuantity(expr, from, null);
                        recompute();
                    }
                    autoSave();
                    return true;
                }

                if (inCanvas(mx, my)) {
                    FreeboardItem newItem = new FreeboardItem(relMx - 15, relMy - 10, Expr.Slot.of(q.id()));
                    autoEvaluateFreeboard(newItem);
                    freeboardExprs.add(newItem);
                    if (from != null) {
                        expr = Manipulate.setSlotQuantity(expr, from, null);
                        recompute();
                    }
                    autoSave();
                    return true;
                }

                if (from != null && my < trayY) {
                    expr = Manipulate.setSlotQuantity(expr, from, null);
                    autoSave();
                    recompute();
                }
                return true;
            }

            if (from == null && expr != null) {
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
                    autoSave();
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
            int max = Math.max(0, trayItems.size() * (TILE_W + 4) - (guiX + guiW - 12));
            trayScroll = (int) Math.max(0, Math.min(max, trayScroll - scrollY * 34));
            return true;
        }
        if (inCanvas(mx, my)) {
            float delta = scrollY > 0 ? 0.1f : -0.1f;
            this.zoomScale = Math.max(0.4f, Math.min(2.5f, this.zoomScale + delta));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private boolean inCanvas(double mx, double my) {
        return mx >= canvasX && mx < canvasX + canvasW && my >= canvasY && my < canvasY + canvasH;
    }

    private FormulaLayout.Box slotBoxAt(double mx, double my) {
        if (!inCanvas(mx, my) || expr == null) return null;
        int centerX = canvasX + canvasW / 2 + panX;
        int centerY = canvasY + canvasH / 2 + panY;

        double unscaledMx = centerX + (mx - centerX) / zoomScale;
        double unscaledMy = centerY + (my - centerY) / zoomScale;

        int fw = FormulaLayout.totalWidth(expr);
        int fh = FormulaLayout.totalHeight(expr);
        int ox = centerX - fw / 2;
        int oy = centerY - fh / 2;

        for (FormulaLayout.Box b : FormulaLayout.layout(expr, ox, oy)) {
            if (b.kind() == FormulaLayout.BoxKind.SLOT && b.contains(unscaledMx, unscaledMy)) return b;
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
        int idx = (int) ((mx + trayScroll - (guiX + 6)) / (TILE_W + 4));
        if (idx < 0 || idx >= trayItems.size()) return null;
        double local = (mx + trayScroll - (guiX + 6)) % (TILE_W + 4);
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
