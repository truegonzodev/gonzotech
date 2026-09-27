package com.gonzotech.core.psyche.client;

import com.gonzotech.core.psyche.PlayerPsyche;
import com.gonzotech.core.psyche.PsycheNetwork;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD-шкалы «психики»: три слева от хотбара и три справа. Каждая шкала —
 * «сэндвич»:
 * <ul>
 *   <li><b>Z0</b>: {@code scale_bg.png} (81×8) — фон под рамкой;</li>
 *   <li><b>Z1</b>: наполнение — чистый рендер {@code g.fill} цветом шкалы;</li>
 *   <li><b>Z2</b>: {@code scale_frame.png} (81×8) — рамка поверх всего.</li>
 * </ul>
 *
 * <p>Левые (сверху вниз): Зависимость, Стресс, Экзистенциальный кризис.
 * Правые (сверху вниз): Облучение, УФ излучение, Химическое заражение.</p>
 *
 * <p>Название шкалы (text render) появляется, когда её значение {@code > 10%}:
 * у левых — слева от шкалы, у правых — справа. Подписи с 0.3.31 рисует
 * отдельный обработчик {@link PsycheHudLabels}, зарегистрированный ДО кризисной
 * дымки: дымка накрывает подписи, но НЕ сами шкалы (шкалы рисуются после неё).</p>
 *
 * <p>Видимость: Зависимость/Стресс/Кризис и Химическое заражение видны всегда;
 * Облучение — только пока в руке дозиметр; УФ излучение — только пока в руке
 * УФ-радиометр ({@code isShown}). Значения 0..1000 = 0..100%.</p>
 *
 * <p>Зависимость, стресс и кризис приходят по сети в ОЧКАХ ({@code 0..1_000_000}) —
 * перевод в тысячные делает {@link PlayerPsyche#pointsToPermille(int)}.</p>
 */
public final class PsycheHud {

    private PsycheHud() {
    }

    static final int BAR_W = 81;
    static final int BAR_H = 8;
    private static final int GAP = 2;
    private static final int STEP = BAR_H + GAP;
    private static final int FILL_INSET = 1;

    /** Порог показа названия шкалы (10% = 100 тысячных). */
    static final int LABEL_THRESHOLD = 100;
    static final int LABEL_PAD = 4; // отступ надписи от шкалы

    private static final ResourceLocation TEX_BG =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/scale_bg.png");
    private static final ResourceLocation TEX_FRAME =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "textures/gui/scale_frame.png");

    // Цвета заливки (ARGB).
    static final int COLOR_ADDICTION = 0xFFD62F15; // #d62f15
    static final int COLOR_STRESS = 0xFF3E71B3;    // #3e71b3
    static final int COLOR_CRISIS = 0xFF6A6370;    // #6a6370
    static final int COLOR_RADIATION = 0xFF8DF542; // #8df542
    static final int COLOR_UV = 0xFF7300FF;        // #7300ff
    static final int COLOR_CHEMICAL = 0xFFD90286;  // #d90286

    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        GuiGraphics g = event.getGuiGraphics();
        for (BarSpec bar : specs(mc)) {
            drawBarVisual(g, bar);
        }
    }

    /** Спецификации видимых шкал: общий источник и для шкал, и для подписей {@link PsycheHudLabels}. */
    static List<BarSpec> specs(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) return List.of();

        PsycheNetwork.PsycheDataPayload data = PsycheNetwork.CLIENT_DATA;
        // Зависимость, стресс и кризис хранятся В ОЧКАХ (1 000 000 = 100 %) —
        // бары рисуются в тысячных, перевод делает PlayerPsyche.pointsToPermille.
        int addiction = data != null ? PlayerPsyche.pointsToPermille(data.addiction()) : 0;
        int stress = data != null ? PlayerPsyche.pointsToPermille(data.stress()) : 0;
        int crisis = data != null ? PlayerPsyche.pointsToPermille(data.crisis()) : 0;
        int radiation = data != null ? data.radiation() : 0;
        int uv = data != null ? data.uv() : 0;
        int chemical = data != null ? data.chemical() : 0;

        // isShown: измерительные шкалы видны только с прибором в руке. Телифон (автор 22.09)
        // совмещает приборы, поэтому с ним видно и облучение, и УФ.
        boolean telifon = isHolding(player, ModItems.TELIFON.get());
        boolean showRadiation = telifon || isHolding(player, ModItems.DOSIMETER.get());
        boolean showUv = telifon || isHolding(player, ModItems.UV_METER.get());

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int hotbarLeft = (screenW - 182) / 2;
        int hotbarRight = hotbarLeft + 182;

        // Нижняя из трёх шкал выровнена по низу хотбара; вверх — с шагом STEP.
        int bottomBarY = screenH - 4 - BAR_H;
        int topBarY = bottomBarY - 2 * STEP;

        List<BarSpec> bars = new ArrayList<>(6);
        // ── Левый столбец (сдвинут на 2px ближе к хотбару: −4 → −2) ──
        int leftX = hotbarLeft - BAR_W - 2;
        if (leftX < 2) leftX = 2;
        bars.add(new BarSpec(leftX, topBarY, addiction, COLOR_ADDICTION,
                "hud.gonzotech.psyche.addiction", true));
        bars.add(new BarSpec(leftX, topBarY + STEP, stress, COLOR_STRESS,
                "hud.gonzotech.psyche.stress", true));
        bars.add(new BarSpec(leftX, topBarY + 2 * STEP, crisis, COLOR_CRISIS,
                "hud.gonzotech.psyche.crisis", true));

        // ── Правый столбец ──
        int rightX = hotbarRight + 2;
        if (rightX + BAR_W > screenW - 2) rightX = screenW - 2 - BAR_W;
        if (showRadiation) {
            bars.add(new BarSpec(rightX, topBarY, radiation, COLOR_RADIATION,
                    "hud.gonzotech.psyche.radiation", false));
        }
        if (showUv) {
            bars.add(new BarSpec(rightX, topBarY + STEP, uv, COLOR_UV,
                    "hud.gonzotech.psyche.uv", false));
        }
        // Химическое заражение видно всегда.
        bars.add(new BarSpec(rightX, topBarY + 2 * STEP, chemical, COLOR_CHEMICAL,
                "hud.gonzotech.psyche.chemical", false));
        return bars;
    }

    private static boolean isHolding(Player player, net.minecraft.world.item.Item item) {
        return player.getMainHandItem().is(item) || player.getOffhandItem().is(item);
    }

    /** Один «сэндвич» без подписи: bg → наполнение → рамка. */
    private static void drawBarVisual(GuiGraphics g, BarSpec bar) {
        int x = bar.x();
        int y = bar.y();

        // Z0: фон.
        blit(g, TEX_BG, x, y);

        // Z1: наполнение (чистый рендер).
        int innerW = BAR_W - 2 * FILL_INSET;
        int innerH = BAR_H - 2 * FILL_INSET;
        int filled = Math.round(clamp01(bar.value() / (float) PlayerPsyche.MAX) * innerW);
        if (filled > 0) {
            int fx = x + FILL_INSET;
            int fy = y + FILL_INSET;
            g.fill(fx, fy, fx + filled, fy + innerH, bar.color());
        }

        // Z2: рамка поверх.
        blit(g, TEX_FRAME, x, y);
    }

    private static void blit(GuiGraphics g, ResourceLocation tex, int x, int y) {
        g.blit(RenderType::guiTextured, tex, x, y, 0f, 0f, BAR_W, BAR_H, BAR_W, BAR_H);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    /** Геометрия и данные одной шкалы (значение 0..1000 тысячных). */
    record BarSpec(int x, int y, int value, int color, String labelKey, boolean labelLeft) {
    }
}
