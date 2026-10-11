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
    private static final ResourceLocation PANEL =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/gui/onboarding_panel.png");
    private static final ResourceLocation PANEL =
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "textures/gui/onboarding_panel.png");

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
        // Обводка: чёрная в обычном состоянии, белая при наведении/выборе.
        int edge = this.isHoveredOrFocused() ? 0xFF_FFFFFF : 0xFF_000000;
        graphics.fill(x, y, x + w, y + 1, edge);
        graphics.fill(x, y + h - 1, x + w, y + h, edge);
        graphics.fill(x, y, x + 1, y + h, edge);
        graphics.fill(x + w - 1, y, x + w, y + h, edge);
        // Внутренность кнопки (18×18): заливка-панель, поверх — иконка.
        int inner = Math.max(0, Math.min(w, h) - 2);
        graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, PANEL,
            x + 1, y + 1, 0.0F, 0.0F, inner, inner, 18, 18);
        graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, ICON,
            x + 1, y + 1, 0.0F, 0.0F, inner, inner, 18, 18);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        this.defaultButtonNarrationText(narration);
    }
}
