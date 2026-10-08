package com.gonzotech.core.client;

import com.gonzotech.GonzoTechMod;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Client-only impact-frame sequence for a fully charged greatsword release. */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID, value = Dist.CLIENT)
public final class GreatswordImpactClient {

    // Emulate the requested 2-frame / 1-frame phases at 60 Hz, independent of the actual FPS.
    private static final long BRIGHTNESS_PHASE_NANOS = 33_333_333L;
    private static final long NEGATIVE_PHASE_NANOS = 16_666_667L;
    private static final int UNFREEZE_TICK = 3;
    private static final int FADE_TICKS = 3;

    private static Phase phase = Phase.IDLE;
    private static Player owner;
    private static int ticksSinceRelease;
    private static int fadeElapsedTicks;
    private static long flashPhaseStartNanos;
    private static boolean thawAfterImpactFrame;
    private static boolean frozenFrameCaptured;

    private GreatswordImpactClient() {
    }

    /** Starts only for the local player; the release callback already checked full charge. */
    public static void begin(Player releasingPlayer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (releasingPlayer == null || minecraft.player != releasingPlayer || minecraft.level == null) {
            return;
        }

        owner = releasingPlayer;
        ticksSinceRelease = 0;
        fadeElapsedTicks = 0;
        flashPhaseStartNanos = 0L;
        thawAfterImpactFrame = false;
        frozenFrameCaptured = false;
        phase = Phase.WAITING_FOR_FIRST_TICK;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (phase == Phase.IDLE) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.player != owner) {
            clearSequence();
            return;
        }

        ticksSinceRelease++;
        switch (phase) {
            case WAITING_FOR_FIRST_TICK -> {
                if (ticksSinceRelease >= 1) {
                    phase = Phase.BRIGHTNESS_FLASH;
                    flashPhaseStartNanos = System.nanoTime();
                }
            }
            case FROZEN -> {
                if (ticksSinceRelease >= UNFREEZE_TICK) {
                    beginFadeOut(0);
                }
            }
            case BRIGHTNESS_FLASH, NEGATIVE_FLASH, IMPACT_FLASH -> {
                if (ticksSinceRelease >= UNFREEZE_TICK) {
                    // At very low render rates, honor the engine's tick-three deadline rather
                    // than holding a late snapshot beyond it. Capture the impact image if the
                    // renderer gets a frame on tick three; otherwise fall back to the live view.
                    if (phase != Phase.IMPACT_FLASH) {
                        phase = Phase.IMPACT_FLASH;
                        flashPhaseStartNanos = System.nanoTime();
                    }
                    thawAfterImpactFrame = true;
                    if (ticksSinceRelease > UNFREEZE_TICK && !frozenFrameCaptured) {
                        beginFadeOut(ticksSinceRelease - UNFREEZE_TICK);
                    }
                }
            }
            case FADING_OUT -> {
                fadeElapsedTicks++;
                if (fadeElapsedTicks >= FADE_TICKS) {
                    clearSequence();
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (phase == Phase.IDLE || phase == Phase.WAITING_FOR_FIRST_TICK) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.player != owner) {
            clearSequence();
            return;
        }

        // Flush buffered HUD vertices before the raw full-screen shader touches the main target.
        event.getGuiGraphics().flush();
        RenderTarget screen = minecraft.getMainRenderTarget();
        long now = System.nanoTime();

        switch (phase) {
            case BRIGHTNESS_FLASH -> {
                if (!GreatswordImpactRenderer.applyFilter(screen,
                    GreatswordImpactRenderer.Filter.BRIGHTNESS, 1.0F)) {
                    clearSequence();
                    return;
                }
                if (now - flashPhaseStartNanos >= BRIGHTNESS_PHASE_NANOS) {
                    phase = Phase.NEGATIVE_FLASH;
                    flashPhaseStartNanos = now;
                }
            }
            case NEGATIVE_FLASH -> {
                if (!GreatswordImpactRenderer.applyFilter(screen,
                    GreatswordImpactRenderer.Filter.NEGATIVE_HARD_CONTRAST, 1.0F)) {
                    clearSequence();
                    return;
                }
                if (now - flashPhaseStartNanos >= NEGATIVE_PHASE_NANOS) {
                    phase = Phase.IMPACT_FLASH;
                }
            }
            case IMPACT_FLASH -> {
                if (!GreatswordImpactRenderer.applyFilter(screen,
                    GreatswordImpactRenderer.Filter.INTENSE_IMPACT, 1.0F)
                    || !GreatswordImpactRenderer.captureFrozenFrame(screen)) {
                    clearSequence();
                    return;
                }

                frozenFrameCaptured = true;
                if (thawAfterImpactFrame || ticksSinceRelease >= UNFREEZE_TICK) {
                    // Leave this final overexposed frame on screen for the current refresh;
                    // the next render starts the tick-synchronized live-camera handoff.
                    beginFadeOut(0);
                } else {
                    phase = Phase.FROZEN;
                }
            }
            case FROZEN -> {
                if (!GreatswordImpactRenderer.drawFrozenFrame(screen)) {
                    clearSequence();
                }
            }
            case FADING_OUT -> {
                float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                float progress = fadeProgress(partialTick);
                float liveBlend = smoothstep(progress);
                float brightnessIntensity = 1.0F - liveBlend;
                boolean rendered = frozenFrameCaptured
                    ? GreatswordImpactRenderer.renderThawTransition(screen, liveBlend, brightnessIntensity)
                    : GreatswordImpactRenderer.applyFilter(screen,
                        GreatswordImpactRenderer.Filter.FADE_BRIGHTNESS, brightnessIntensity);
                if (!rendered) {
                    clearSequence();
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (phase != Phase.FADING_OUT) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (event.getPlayer() != owner || event.getPlayer() != minecraft.player) return;

        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float brightnessIntensity = 1.0F - smoothstep(fadeProgress(partialTick));
        // -30% FOV gives the requested zoom; other modifiers (sprinting, effects) are preserved.
        float zoomFactor = 1.0F - 0.30F * brightnessIntensity;
        event.setNewFovModifier(event.getNewFovModifier() * zoomFactor);
    }

    private static void beginFadeOut(int elapsedTicks) {
        phase = Phase.FADING_OUT;
        fadeElapsedTicks = Math.max(0, Math.min(FADE_TICKS - 1, elapsedTicks));
    }

    private static float fadeProgress(float partialTick) {
        float clampedPartial = Math.max(0.0F, Math.min(1.0F, partialTick));
        return Math.max(0.0F, Math.min(1.0F,
            (fadeElapsedTicks + clampedPartial) / (float) FADE_TICKS));
    }

    /** Smoothstep ease-in-out for the three-tick handoff. */
    private static float smoothstep(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }

    private static void clearSequence() {
        phase = Phase.IDLE;
        owner = null;
        ticksSinceRelease = 0;
        fadeElapsedTicks = 0;
        flashPhaseStartNanos = 0L;
        thawAfterImpactFrame = false;
        frozenFrameCaptured = false;
    }

    private enum Phase {
        IDLE,
        WAITING_FOR_FIRST_TICK,
        BRIGHTNESS_FLASH,
        NEGATIVE_FLASH,
        IMPACT_FLASH,
        FROZEN,
        FADING_OUT
    }
}
