package com.gonzotech.core.client;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Туман и мутность камеры в мёртвой жиже (0.3.280, разбор автора).
 *
 * <p>Раньше дымка была чистым terrain-fog: обработчик события земного тумана
 * отменял его и подсовывал свою сферу near=0/far=2 — из-за этого
 * сторонние туман-модификаторы выключали жижу вместе с обычным туманом, а
 * «наведение на слой неба» давало те самые просветы. Это и есть корень всех
 * прежних патчей dead_slime fog: terrain-fog и underwater-fog — разные слои,
 * их нельзя лечить друг другом.
 *
 * <p>Сейчас — по подводному образцу:
 * <ul>
 *   <li>цвет мутки ставится через {@code ComputeFogColor} (тот же канал, через
 *       который жидкости красит {@code modifyFogColor}) — оба слоя, без
 *       отмены и без подмены геометрии;</li>
 *   <li>плотность погружения даёт экранная зелёная дымка по образцу
 *       crisis_screen_effect/седатива: максимум 11 % непрозрачности, рост
 *       +1 %/тик при погружении, симметричный fade-out при выходе;
 *       погружение считается по КАМЕРЕ (как и туман), не по ногам;</li>
 *   <li>отмены земного тумана больше нет вовсе — terrain-fog удалён.</li>
 * </ul>
 */
public final class DeadSlimeFogClient {

    /** Потолок дымки — 11 % (автор 24.10), шаг — 1 % в тик (и туда, и обратно). */
    private static final int VEIL_MAX_PERCENT = 11;
    /** Резервный цвет дымки, если кадр застал вне блока (затухание после выхода). */
    private static final int FALLBACK_VEIL_RGB = 0x558B2F;

    /** Текущая плотность дымки в процентах (0..VEIL_MAX_PERCENT). */
    private static int veilPercent = 0;
    /** RGB-цвет материала жижи, снятый при последнем погружении. */
    private static int veilRgb = FALLBACK_VEIL_RGB;

    private DeadSlimeFogClient() {
    }

    /** Сессийная гигиена: при выходе дымка не должна переехать в следующий мир. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        veilPercent = 0;
        veilRgb = FALLBACK_VEIL_RGB;
    }

    // ─────────────────────────── туман: цвет, не геометрия ───────────────────────────

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

    // ─────────────────────────── тик: ramp ±1 %/тик по камере ───────────────────────────

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused()) {
            return;
        }
        if (cameraInsideDeadSlime(mc.gameRenderer.getMainCamera())) {
            veilPercent = Math.min(VEIL_MAX_PERCENT, veilPercent + 1);
            // Цвет снимаем со стека блока под камерой, чтобы дымка совпадала с
            // туманом одного и того же материала.
            BlockPos cameraPos = mc.gameRenderer.getMainCamera().getBlockPosition();
            veilRgb = mc.gameRenderer.getMainCamera().getBlockAtCamera()
                    .getMapColor(level, cameraPos).col;
        } else if (veilPercent > 0) {
            veilPercent--;
        }
    }

    // ─────────────────────────── экран: 11 % зелёная дымка ───────────────────────────

    /**
     * Рисуем на том же финальном GUI-проходе, что и кризисный оверлей:
     * F1 прячет HUD, а муть среды — состояние, не элемент интерфейса.
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (veilPercent <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        float alpha = Mth.clamp(veilPercent / 100.0F, 0.0F, 1.0F);
        int argb = ((int) (alpha * 255.0F) << 24) | (veilRgb & 0xFFFFFF);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), argb);
    }

    private static boolean cameraInsideDeadSlime(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && camera.getBlockAtCamera().is(ModBlocks.DEAD_SLIME_BLOCK.get());
    }
}
