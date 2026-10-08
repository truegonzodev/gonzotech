package com.gonzotech.core.psyche.client;

import com.gonzotech.core.item.SedativeItem;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.UUID;

/** Smooth screen dimming and reduced contrast while the sedative's relaxation is active. */
final class PsycheRelaxationClient {

    private static final float BRIGHTNESS_REDUCTION = 0.30F;
    private static final float CONTRAST_REDUCTION = 0.10F;
    private static final float MAX_FRAME_STEP_SECONDS = 0.1F;
    private static final float NANOS_PER_SECOND = 1_000_000_000.0F;

    private static float effectStrength;
    private static long lastFrameNanos;
    private static UUID trackedPlayer;

    private PsycheRelaxationClient() {
    }

    static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (minecraft.player == null) {
            reset();
            return;
        }

        UUID playerId = minecraft.player.getUUID();
        if (!playerId.equals(trackedPlayer)) {
            reset();
            trackedPlayer = playerId;
        }

        long now = System.nanoTime();
        if (lastFrameNanos != 0L) {
            float elapsedSeconds = Mth.clamp(
                    (now - lastFrameNanos) / NANOS_PER_SECOND,
                    0.0F, MAX_FRAME_STEP_SECONDS);
            float transitionSeconds = SedativeItem.FADE_TICKS / 20.0F;
            float strengthStep = elapsedSeconds / transitionSeconds;
            boolean relaxationActive = minecraft.player.hasEffect(ModEffects.RELAXATION);
            effectStrength = Mth.clamp(
                    effectStrength + (relaxationActive ? strengthStep : -strengthStep), 0.0F, 1.0F);
        }
        lastFrameNanos = now;

        if (effectStrength <= 0.0F) {
            return;
        }

        // Fade the requested filter in/out without resetting it when the effect refreshes.
        // A black 30% overlay multiplies the existing image brightness by 0.70; the
        // following 10% neutral-gray overlay pulls contrast 10% toward mid-gray.
        int brightnessAlpha = Mth.clamp(Math.round(effectStrength * BRIGHTNESS_REDUCTION * 255.0F), 0, 255);
        int contrastAlpha = Mth.clamp(Math.round(effectStrength * CONTRAST_REDUCTION * 255.0F), 0, 255);
        if (brightnessAlpha > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), brightnessAlpha << 24);
        }
        if (contrastAlpha > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                    (contrastAlpha << 24) | 0x00808080);
        }
    }

    static void reset() {
        effectStrength = 0.0F;
        lastFrameNanos = 0L;
        trackedPlayer = null;
    }
}
