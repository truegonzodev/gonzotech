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

    private static final int UNFREEZE_TICK = 3;
    private static final int FADE_TICKS = 3;

    private static Phase phase = Phase.IDLE;
    private static Player owner;
    private static int ticksSinceRelease;
    private static int brightFramesRemaining;
    private static int fadeElapsedTicks;
    private static float fadeIntensity;

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
        brightFramesRemaining = 2;
        fadeElapsedTicks = 0;
        fadeIntensity = 0.0F;
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
                    brightFramesRemaining = 2;
                }
            }
            case FROZEN -> {
                if (ticksSinceRelease >= UNFREEZE_TICK) {
                    beginFadeOut();
                }
            }
            case FADING_OUT -> advanceFadeOut();
            default -> {
                // The three flash stages are advanced by rendered frames, not ticks.
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

        switch (phase) {
            case BRIGHTNESS_FLASH -> {
                if (!GreatswordImpactRenderer.applyFilter(screen,
                    GreatswordImpactRenderer.Filter.BRIGHTNESS, 1.0F)) {
                    clearSequence();
                    return;
                }
                if (--brightFramesRemaining <= 0) {
                    phase = Phase.NEGATIVE_FLASH;
                }
            }
            case NEGATIVE_FLASH -> {
                if (!GreatswordImpactRenderer.applyFilter(screen,
                    GreatswordImpactRenderer.Filter.NEGATIVE_HARD_CONTRAST, 1.0F)) {
                    clearSequence();
                    return;
                }
                phase = Phase.IMPACT_FLASH;
            }
            case IMPACT_FLASH -> {
                if (!GreatswordImpactRenderer.applyFilter(screen,
                    GreatswordImpactRenderer.Filter.INTENSE_IMPACT, 1.0F)
                    || !GreatswordImpactRenderer.captureFrozenFrame(screen)) {
                    clearSequence();
                    return;
                }

                // If an unusually slow renderer reaches tick three before all four flash frames,
                // show the final flash but do not extend the hold beyond the requested deadline.
                if (ticksSinceRelease >= UNFREEZE_TICK) {
                    beginFadeOut();
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
                if (fadeIntensity > 0.0F
                    && !GreatswordImpactRenderer.applyFilter(screen,
                        GreatswordImpactRenderer.Filter.FADE_BRIGHTNESS, fadeIntensity)) {
                    clearSequence();
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFovModifier(ComputeFovModifierEvent event) {
        if (phase != Phase.FADING_OUT || fadeIntensity <= 0.0F) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (event.getPlayer() != owner || event.getPlayer() != minecraft.player) return;

        // Multiply the already-computed modifier, preserving sprinting and other FOV effects.
        float zoomFactor = 1.0F - 0.30F * fadeIntensity;
        event.setNewFovModifier(event.getNewFovModifier() * zoomFactor);
    }

    private static void beginFadeOut() {
        phase = Phase.FADING_OUT;
        fadeElapsedTicks = 0;
        fadeIntensity = 1.0F;
    }

    private static void advanceFadeOut() {
        fadeElapsedTicks++;
        float progress = Math.min(1.0F, fadeElapsedTicks / (float) FADE_TICKS);
        // Ease-in: the return starts gently, then accelerates toward normal at the end.
        fadeIntensity = 1.0F - progress * progress;
        if (fadeElapsedTicks >= FADE_TICKS) {
            clearSequence();
        }
    }

    private static void clearSequence() {
        phase = Phase.IDLE;
        owner = null;
        ticksSinceRelease = 0;
        brightFramesRemaining = 0;
        fadeElapsedTicks = 0;
        fadeIntensity = 0.0F;
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
