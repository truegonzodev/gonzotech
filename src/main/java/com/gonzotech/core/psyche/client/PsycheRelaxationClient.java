package com.gonzotech.core.psyche.client;

import com.gonzotech.core.item.SedativeItem;
import com.gonzotech.core.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.UUID;

/** Smooth, translucent white screen haze while the sedative's relaxation is active. */
final class PsycheRelaxationClient {

    private static final float MAX_ALPHA = 0.34F;
    private static final float MAX_FRAME_STEP_SECONDS = 0.1F;
    private static final float NANOS_PER_SECOND = 1_000_000_000.0F;

    private static float alpha;
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
            float alphaStep = MAX_ALPHA * elapsedSeconds / transitionSeconds;
            boolean relaxationActive = minecraft.player.hasEffect(ModEffects.RELAXATION);
            alpha = Mth.clamp(alpha + (relaxationActive ? alphaStep : -alphaStep), 0.0F, MAX_ALPHA);
        }
        lastFrameNanos = now;

        if (alpha <= 0.0F) {
            return;
        }

        // Keep the animation state locally and move toward its target at a fixed rate:
        // applying/refreshing the status effect cannot restart or flash the overlay.
        int alphaChannel = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(),
                (alphaChannel << 24) | 0x00FFFFFF);
    }

    static void reset() {
        alpha = 0.0F;
        lastFrameNanos = 0L;
        trackedPlayer = null;
    }
}
