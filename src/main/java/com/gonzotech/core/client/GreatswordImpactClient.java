package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Timed client-side hit-stop-free impact sequence for a fully charged greatsword release. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class GreatswordImpactClient {

    private static final long WHITE_FLASH_START_NANOS = 41_000_000L;
    private static final long WHITE_FLASH_DURATION_NANOS = 33_000_000L;
    private static final long BLACK_FLASH_DURATION_NANOS = 18_000_000L;
    private static final long TRACER_PHASE_DURATION_NANOS = 70_000_000L;
    private static final long NEGATIVE_GRAIN_PHASE_DURATION_NANOS = 35_000_000L;

    private static final long WHITE_FLASH_END_NANOS = WHITE_FLASH_START_NANOS + WHITE_FLASH_DURATION_NANOS;
    private static final long BLACK_FLASH_END_NANOS = WHITE_FLASH_END_NANOS + BLACK_FLASH_DURATION_NANOS;
    private static final long TRACER_PHASE_END_NANOS = BLACK_FLASH_END_NANOS + TRACER_PHASE_DURATION_NANOS;
    private static final long POST_IMPACT_START_NANOS = TRACER_PHASE_END_NANOS + NEGATIVE_GRAIN_PHASE_DURATION_NANOS;

    private static final long SHAKE_DURATION_NANOS = 300_000_000L;
    private static final long BRIGHTNESS_CONTRAST_FADE_NANOS = 730_000_000L;
    private static final long FOV_FADE_NANOS = 1_000_000_000L;
    private static final long SEQUENCE_DURATION_NANOS = POST_IMPACT_START_NANOS + FOV_FADE_NANOS;
    private static final float NANOS_TO_SECONDS = 1.0E-9F;
    private static final float TWO_PI = (float) (Math.PI * 2.0D);

    private static Player owner;
    private static long releaseTimeNanos;
    private static boolean sequenceActive;

    private GreatswordImpactClient() {
    }

    /** Starts the shake immediately on release; the first flash is deliberately delayed 41 ms. */
    public static void begin(Player releasingPlayer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (releasingPlayer == null || minecraft.player != releasingPlayer || minecraft.level == null) {
            return;
        }

        owner = releasingPlayer;
        releaseTimeNanos = System.nanoTime();
        sequenceActive = true;
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActiveFor(minecraft) || event.getCamera().getEntity() != owner) return;

        long elapsedNanos = elapsedSinceRelease();
        if (elapsedNanos < 0L || elapsedNanos >= SHAKE_DURATION_NANOS) return;

        float elapsedSeconds = elapsedNanos * NANOS_TO_SECONDS;
        float envelope = remainingStrength(elapsedNanos, SHAKE_DURATION_NANOS);
        float yawJolt = (float) Math.sin(elapsedSeconds * TWO_PI * 29.0F + 0.9F)
            + 0.45F * (float) Math.sin(elapsedSeconds * TWO_PI * 47.0F + 0.2F);
        float pitchJolt = 0.78F * (float) Math.sin(elapsedSeconds * TWO_PI * 33.0F + 1.4F)
            + 0.36F * (float) Math.sin(elapsedSeconds * TWO_PI * 51.0F + 0.5F);
        float rollJolt = 1.45F * (float) Math.sin(elapsedSeconds * TWO_PI * 25.0F + 0.6F)
            + 0.72F * (float) Math.sin(elapsedSeconds * TWO_PI * 39.0F + 1.8F);

        event.setYaw(event.getYaw() + yawJolt * envelope);
        event.setPitch(event.getPitch() + pitchJolt * envelope);
        event.setRoll(event.getRoll() + rollJolt * envelope);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActiveFor(minecraft)) return;

        long elapsedNanos = elapsedSinceRelease();
        if (elapsedNanos < 0L) {
            clearSequence();
            return;
        }
        if (elapsedNanos >= SEQUENCE_DURATION_NANOS) {
            clearSequence();
            return;
        }
        if (elapsedNanos < WHITE_FLASH_START_NANOS) return;

        GreatswordImpactRenderer.Filter filter;
        float intensity = 1.0F;
        if (elapsedNanos < WHITE_FLASH_END_NANOS) {
            filter = GreatswordImpactRenderer.Filter.FULL_WHITE;
        } else if (elapsedNanos < BLACK_FLASH_END_NANOS) {
            filter = GreatswordImpactRenderer.Filter.FULL_BLACK;
        } else if (elapsedNanos < TRACER_PHASE_END_NANOS) {
            filter = GreatswordImpactRenderer.Filter.BLACK_AND_WHITE_TRACERS;
        } else if (elapsedNanos < POST_IMPACT_START_NANOS) {
            filter = GreatswordImpactRenderer.Filter.NEGATIVE_GRAIN_HDR;
        } else {
            long postImpactElapsed = elapsedNanos - POST_IMPACT_START_NANOS;
            if (postImpactElapsed >= BRIGHTNESS_CONTRAST_FADE_NANOS) return;
            filter = GreatswordImpactRenderer.Filter.POST_IMPACT;
            intensity = remainingStrength(postImpactElapsed, BRIGHTNESS_CONTRAST_FADE_NANOS);
        }

        // Flush HUD vertices before the full-screen shader touches the main target.
        event.getGuiGraphics().flush();
        RenderTarget screen = minecraft.getMainRenderTarget();
        float effectTime = elapsedNanos * NANOS_TO_SECONDS;
        if (!GreatswordImpactRenderer.applyFilter(screen, filter, intensity, effectTime)) {
            clearSequence();
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isActiveFor(minecraft) || event.getPlayer() != owner || event.getPlayer() != minecraft.player) return;

        long postImpactElapsed = elapsedSinceRelease() - POST_IMPACT_START_NANOS;
        if (postImpactElapsed < 0L || postImpactElapsed >= FOV_FADE_NANOS) return;

        float zoomStrength = remainingStrength(postImpactElapsed, FOV_FADE_NANOS);
        // -40% means the modifier immediately drops to 0.60, then eases back to normal.
        event.setNewFovModifier(event.getNewFovModifier() * (1.0F - 0.40F * zoomStrength));
    }

    /** Ease-out curve: strong response immediately, then progressively slower recovery. */
    private static float remainingStrength(long elapsedNanos, long durationNanos) {
        float progress = Math.max(0.0F, Math.min(1.0F, elapsedNanos / (float) durationNanos));
        float remaining = 1.0F - progress;
        return remaining * remaining * remaining;
    }

    private static long elapsedSinceRelease() {
        return System.nanoTime() - releaseTimeNanos;
    }

    private static boolean isActiveFor(Minecraft minecraft) {
        if (!sequenceActive) return false;
        if (minecraft.level == null || minecraft.player == null || minecraft.player != owner) {
            clearSequence();
            return false;
        }
        return true;
    }

    private static void clearSequence() {
        owner = null;
        releaseTimeNanos = 0L;
        sequenceActive = false;
    }
}
