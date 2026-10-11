package com.gonzotech.core.client.onboard;

import com.gonzotech.GonzoTechMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран-приветствие первого запуска: экскурс в мод и честный список допущений —
 * влияние на генерацию мира, негарантированное совпадение сидов, изменённые
 * лут-таблицы сундуков, кастомный скайбокс и возможные конфликты с шейдерами.
 *
 * <p>Вызывается автоматически при первом запуске и по кнопке «О моде» из
 * главного меню. Текст собирается из lang-ключа
 * {@code gui.gonzotech.onboarding.body} (переносы строк — {@code \n}).
 * Окно оформлено накладной рамкой {@code onboarding_frame.png} (912×445).
 */
public final class GonzoOnboardingScreen extends Screen {

    /** Плейсхолдер рамки окна (912×445 текстелей, ×2 на мониторе). */
    private static final ResourceLocation FRAME =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/gui/onboarding_frame.png");
    private static final int FRAME_W = 912;
    private static final int FRAME_H = 445;
    private static final int LINE_SPACING = 2;

    public GonzoOnboardingScreen() {
        super(Component.translatable("gui.gonzotech.onboarding.title"));
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 160;
        int bottom = Math.min(this.height, Math.max(0, (this.height - FRAME_H) / 2) + FRAME_H);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.gonzotech.onboarding.ok"),
                button -> this.onClose())
            .bounds(this.width / 2 - buttonWidth / 2, bottom - 28, buttonWidth, 20)
            .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.drawPanel(graphics);
        for (Renderable renderable : this.renderables) {
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        int left = Math.max(0, (this.width - FRAME_W) / 2);
        int top = Math.max(0, (this.height - FRAME_H) / 2);
        int right = Math.min(this.width, left + FRAME_W);
        int bottom = Math.min(this.height, top + FRAME_H);
        // Тёмная подложка для читаемости текста (серые полосы убраны — их заменяет рамка).
        graphics.fill(left, top, right, bottom, 0xCC_101010);

        int textWidth = right - left - 24;
        int y = top + 10;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y, 0xFF_FFD650);
        y += 14;

        String raw = Component.translatable("gui.gonzotech.onboarding.body").getString();
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : raw.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add(Component.literal(" ").getVisualOrderText());
                continue;
            }
            lines.addAll(this.font.split(Component.literal(paragraph), textWidth));
        }
        for (FormattedCharSequence line : lines) {
            if (y > bottom - 12) break;
            graphics.drawString(this.font, line, left + 12, y, 0xFF_D7D7D7, false);
            y += this.font.lineHeight + LINE_SPACING;
        }

        // Рамка поверх (плейсхолдер 912×445, 1:1).
        graphics.blit(RenderType::guiTextured, FRAME, left, top, 0.0F, 0.0F, FRAME_W, FRAME_H, FRAME_W, FRAME_H);
    }

    @Override
    public void onClose() {
        OnboardingClient.markOnboarded();
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
