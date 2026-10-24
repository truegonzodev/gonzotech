package com.gonzotech.core.client;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Туман-окраска мёртвой жижи (0.3.280; 0.3.285 — экранный вейл удалён по
 * указанию автора: «старая экранная дымка жижи 0..11 % убрать»).
 *
 * <p>Плотность погружения теперь целиком на совести спрайтового оверлея
 * {@code dead_slime_overlay} из {@code FluidScreenOverlays} (нарастание
 * 1 %/тик, угасание ускоряющейся кривой 1,2,3… %/тик, плавный follow альфы)
 * и POWDER_SNOW-среды (миксин 282): её ближняя муть и даёт «замурованность».
 * Здесь остаётся единственный вклад — окраска обоих слоёв тумана в map color
 * материала жижи через {@code ComputeFogColor} (тот же канал, каким жидкости
 * красит {@code modifyFogColor}); отмен земного тумана нет с 0.3.280.
 */
public final class DeadSlimeFogClient {

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

    private static boolean cameraInsideDeadSlime(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && camera.getBlockAtCamera().is(ModBlocks.DEAD_SLIME_BLOCK.get());
    }
}
