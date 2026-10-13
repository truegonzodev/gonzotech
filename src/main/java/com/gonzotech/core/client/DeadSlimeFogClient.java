package com.gonzotech.core.client;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Depth-based green fog while the camera is inside dead slime. */
public final class DeadSlimeFogClient {

    // Match the vanilla powder-snow fog interval instead of guessing a new range.
    private static final float FOG_START = 0.0F;
    private static final float FOG_END = 2.0F;

    private DeadSlimeFogClient() {
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Camera camera = event.getCamera();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !cameraInsideDeadSlime(camera)) {
            return;
        }

        // Dead slime inherits the slime block's grass-green map color; use that material color directly.
        BlockPos cameraPos = camera.getBlockPosition();
        int color = camera.getBlockAtCamera().getMapColor(level, cameraPos).col;
        event.setRed(((color >> 16) & 0xFF) / 255.0F);
        event.setGreen(((color >> 8) & 0xFF) / 255.0F);
        event.setBlue((color & 0xFF) / 255.0F);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!cameraInsideDeadSlime(event.getCamera())) {
            return;
        }

        // These are world-space fog parameters, matching vanilla powder snow; no GUI/freeze overlay is drawn.
        event.setNearPlaneDistance(FOG_START);
        event.setFarPlaneDistance(FOG_END);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    private static boolean cameraInsideDeadSlime(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && camera.getBlockAtCamera().is(ModBlocks.DEAD_SLIME_BLOCK.get());
    }
}
