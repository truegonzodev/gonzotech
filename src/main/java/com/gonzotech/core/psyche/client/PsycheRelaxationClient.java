package com.gonzotech.core.psyche.client;

import com.gonzotech.core.item.SedativeItem;
import com.gonzotech.core.registry.ModEffects;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import org.slf4j.Logger;

import java.util.Set;

/** Applies the sedative's post-processing filter to the completed frame. */
final class PsycheRelaxationClient {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation POST_EFFECT =
            ResourceLocation.fromNamespaceAndPath("gonzotech", "relaxation");
    private static boolean unavailable;
    private static boolean errorLogged;

    private PsycheRelaxationClient() {
    }

    static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (unavailable || minecraft.player == null) {
            return;
        }

        MobEffectInstance relaxation = minecraft.player.getEffect(ModEffects.RELAXATION);
        if (relaxation == null) {
            return;
        }

        // The status effect is 6000 ticks. Ramp over its first and last 100 ticks (5 seconds).
        float elapsed = SedativeItem.RELAXATION_DURATION_TICKS - relaxation.getDuration();
        float fadeIn = Mth.clamp(elapsed / SedativeItem.FADE_TICKS, 0.0F, 1.0F);
        float fadeOut = Mth.clamp(relaxation.getDuration() / (float) SedativeItem.FADE_TICKS, 0.0F, 1.0F);
        float strength = Math.min(fadeIn, fadeOut);

        try {
            // Flush the GUI batch first so the filter covers HUD and screens as well as the world.
            graphics.flush();
            PostChain postChain = minecraft.getShaderManager().getPostChain(
                    POST_EFFECT, Set.of(PostChain.MAIN_TARGET_ID));
            postChain.setUniform("EffectStrength", strength);
            postChain.process(minecraft.getMainRenderTarget(), GraphicsResourceAllocator.UNPOOLED);
        } catch (RuntimeException exception) {
            unavailable = true;
            if (!errorLogged) {
                errorLogged = true;
                LOGGER.error("Unable to render the Gonzo Tech relaxation post-effect", exception);
            }
        }
    }
}
