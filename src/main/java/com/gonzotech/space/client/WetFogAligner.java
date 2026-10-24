package com.gonzotech.space.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 0.3.287 (автор, п.1–4): в Оверворлде при осадках туман — точное продолжение
 * неба купола, а не «заметно более тёмная полоса».
 *
 * <p>Корень: vanilla FogRenderer доделывает цвет ПОСЛЕ нашего скай-эффекта —
 * тянет его к {@code level.getSkyColor} на световой коэффициент (~12 % днём,
 * до ~23 % в сумерках) и затем множит на ванильную «дождевую» темноту
 * (красный/зелёный до −50 %, синий до −40 %) и гром (до −50 %). Купол ни одной
 * из этих операций не получает, поэтому при дожде/снеге граница горизонта
 * темнее неба, а при смене погоды туман выглядит «неизменённым ванильным».
 *
 * <p>Миксин не нужен: NeoForge постит {@link ViewportEvent.ComputeFogColor} в
 * {@code ClientHooks.getFogColor} — ПОСЛЕ всех мультипликаторов, прямо перед
 * установкой цвета — поэтому сюда пишется точный цвет горизонта неба из общего
 * {@code skyShellColors}. Правка нарастает/угасает вместе с силой осадков
 * (коэффициент 4×wet, полная сила с 25 % дождя): на входе/выходе режима
 * граница не «щёлкает», а сухой туман остаётся ровно ванильно-согласованным
 * (автор: без дождя граница «как надо»).
 *
 * <p>Гейты: только Оверворлд; под водой — своя среда (пакет 0.3.281);
 * космические измерения — как в 0.3.286, только через DSE-колбэк (там свои
 * правила и просьба автора их не касалась).
 */
public final class WetFogAligner {

    private WetFogAligner() {
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (!(level.effects() instanceof SpaceSkyEffects sky)) {
            return;
        }
        Camera camera = event.getCamera();
        if (camera == null || camera.getFluidInCamera() != FogType.NONE) {
            return; // под жижей/водой — цвет среды, не неба
        }
        float partialTick = (float) event.getPartialTick();
        float wet = level.getRainLevel(partialTick);
        if (wet <= 0.001F || !sky.wetFogModeActive(level)) {
            return;
        }
        int horizon = sky.skyShellColors(level, partialTick)[1];
        float k = Mth.clamp(wet * 4.0F, 0.0F, 1.0F);
        event.setRed(Mth.clamp(align(event.getRed(), k, ((horizon >>> 16) & 0xFF) / 255.0F), 0.0F, 1.0F));
        event.setGreen(Mth.clamp(align(event.getGreen(), k, ((horizon >>> 8) & 0xFF) / 255.0F), 0.0F, 1.0F));
        event.setBlue(Mth.clamp(align(event.getBlue(), k, (horizon & 0xFF) / 255.0F), 0.0F, 1.0F));
    }

    /** Текущее значение → цвет неба с весом k (0 = ванильный туман, 1 = точный). */
    private static float align(float current, float k, float target) {
        return current + (target - current) * k;
    }
}
