package com.gonzotech.core.client.onboard;

import com.gonzotech.GonzoTechMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Квадратная кнопка «О моде» в главном меню (рядом с «Одиночная игра»).
 * Рисует собственную PNG-иконку {@code textures/gui/onboarding_button.png}
 * вместо текста; по клику открывает {@link GonzoOnboardingScreen}.
 */
public final class GonzoInfoButton extends AbstractWidget {

    private static final ResourceLocation ICON =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/gui/onboarding_button.png");

    public GonzoInfoButton(int x, int y, int size, OnPress onPress) {
        super(x, y, size, size, Component.translatable("gui.gonzotech.onboarding.button.tooltip"));
        this.onPress = onPress;
    }

    /** Действие клика — открывается онбординг-экран. */
    public interface OnPress {
        void onPress(GonzoInfoButton button);
    }

    private final OnPress onPress;

    @Override
    public void onClick(double mouseX, double mouseY) {
        super.onClick(mouseX, mouseY);
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();
        // Простая подложка: рамка ярче при наведении.
        int edge = this.isHoveredOrFocused() ? 0xFF_FFFFFF : 0xFF_8B8B8B;
        graphics.fill(x, y, x + w, y + 1, edge);
        graphics.fill(x, y + h - 1, x + w, y + h, edge);
        graphics.fill(x, y, x + 1, y + h, edge);
        graphics.fill(x + w - 1, y, x + w, y + h, edge);
        int inset = Math.max(2, (w - 16) / 2 + 1);
        graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, ICON,
            x + inset / 2 + 1, y + inset / 2 + 1, 0.0F, 0.0F, 16, 16, 16, 16);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }
}
