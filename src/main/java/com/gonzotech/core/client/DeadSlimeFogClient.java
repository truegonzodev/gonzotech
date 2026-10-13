package com.gonzotech.core.client;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.blaze3d.shaders.FogShape;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Dense camera fog for dead slime; deliberately does not use the powder-snow freeze overlay. */
public final class DeadSlimeFogClient {

    public static final int FOG_COLOR = 0x7CAB6F;
    // Start fog in front of the camera, like submerged-water fog, instead of tinting only distant scenery.
    public static final float FOG_START = -8.0F;
    public static final float FOG_END = 9.0F;

    private DeadSlimeFogClient() {
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        if (!cameraInsideDeadSlime(event.getCamera())) {
            return;
        }

        event.setRed(((FOG_COLOR >> 16) & 0xFF) / 255.0F);
        event.setGreen(((FOG_COLOR >> 8) & 0xFF) / 255.0F);
        event.setBlue((FOG_COLOR & 0xFF) / 255.0F);
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!cameraInsideDeadSlime(event.getCamera())) {
            return;
        }

        event.setNearPlaneDistance(FOG_START);
        event.setFarPlaneDistance(FOG_END);
        event.setFogShape(FogShape.SPHERE);
        event.setCanceled(true);
    }

    private static boolean cameraInsideDeadSlime(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }

        var position = camera.getPosition();
        BlockPos cameraBlock = BlockPos.containing(position.x, position.y, position.z);
        return level.getBlockState(cameraBlock).is(ModBlocks.DEAD_SLIME_BLOCK.get());
    }
}
