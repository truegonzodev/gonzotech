package com.gonzotech.core.client.onboard;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
 */
public final class GonzoOnboardingScreen extends Screen {

    private static final int PANEL_MARGIN = 24;
    private static final int LINE_SPACING = 2;

    public GonzoOnboardingScreen() {
        super(Component.translatable("gui.gonzotech.onboarding.title"));
    }

    @Override
    protected void init() {
        super.init();
        int buttonWidth = 160;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.gonzotech.onboarding.ok"),
                button -> this.onClose())
            .bounds(this.width / 2 - buttonWidth / 2, this.height - 32, buttonWidth, 20)
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
        int left = PANEL_MARGIN;
        int right = this.width - PANEL_MARGIN;
        int top = PANEL_MARGIN;
        int bottom = this.height - 40;
        graphics.fill(left, top, right, bottom, 0xCC_101010);
        graphics.fill(left, top, right, top + 1, 0xFF_555555);
        graphics.fill(left, bottom - 1, right, bottom, 0xFF_555555);

        int textWidth = right - left - 24;
        int y = top + 10;
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y, 0xFF_FFD650);
        y += 14;

        String raw = Component.translatable("gui.gonzotech.onboarding.body").getString();
        List<net.minecraft.util.FormattedCharSequence> lines = new ArrayList<>();
        for (String paragraph : raw.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add(Component.literal(" ").getVisualOrderText());
                continue;
            }
            lines.addAll(this.font.split(Component.literal(paragraph), textWidth));
        }
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            if (y > bottom - 12) break;
            graphics.drawString(this.font, line, left + 12, y, 0xFF_D7D7D7, false);
            y += this.font.lineHeight + LINE_SPACING;
        }
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
