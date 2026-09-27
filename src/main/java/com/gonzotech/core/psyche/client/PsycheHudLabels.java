package com.gonzotech.core.psyche.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * Подписи HUD-шкал «психики» (значение {@code > 10%}): слева для левого столбца,
 * справа для правого.
 *
 * <p>0.3.31 (автор 27.09.2026): подписи рисуются ОТДЕЛЬНО от шкал и
 * регистрируются ДО кризисной дымки ({@code PsycheCrisisClient}), поэтому дымка
 * накрывает текст подписей, но не сами шкалы — те {@link PsycheHud} рисует
 * уже поверх дымки. Геометрия — общий источник {@link PsycheHud#specs}.</p>
 */
public final class PsycheHudLabels {

    private PsycheHudLabels() {
    }

    @SubscribeEvent
    public static void onRenderGui(final RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return; // подписи — часть HUD, прячутся вместе с ним

        GuiGraphics g = event.getGuiGraphics();
        for (PsycheHud.BarSpec bar : PsycheHud.specs(mc)) {
            if (bar.value() <= PsycheHud.LABEL_THRESHOLD) {
                continue;
            }
            Component label = Component.translatable(bar.labelKey());
            int tw = mc.font.width(label);
            int ty = bar.y() + (PsycheHud.BAR_H - mc.font.lineHeight) / 2; // на 1px выше прежнего
            int tx = bar.labelLeft()
                    ? (bar.x() - PsycheHud.LABEL_PAD - tw)
                    : (bar.x() + PsycheHud.BAR_W + PsycheHud.LABEL_PAD);
            g.drawString(mc.font, label, tx, ty, bar.color(), true);
        }
    }
}
