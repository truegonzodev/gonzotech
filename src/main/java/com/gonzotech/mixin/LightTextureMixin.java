package com.gonzotech.mixin;

import com.gonzotech.space.SpaceDimensions;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Фаза 4 — затемнение освещения космических измерений (по фидбэку #6).
 *
 * <p>Требование пользователя: на Луне дневной свет ослаблен на 70%, на Европе —
 * на 88%, Марс НЕ трогаем. Плюс отдельно (через {@code ambient_light=0.0} в
 * dimension_type) пещеры этих миров становятся тёмными, как в Оверворлде — раньше
 * они «светились» из-за ambient_light 0.05/0.1.
 *
 * <p>Реализация: ванильная {@link LightTexture#updateLightTexture(float)} каждый
 * кадр (когда нужно) перестраивает 16×16 карту освещения (X=блочный свет,
 * Y=небесный свет) и заливает её в {@code lightTexture.upload()}. Мы вклиниваемся
 * РОВНО ПЕРЕД этим {@code upload()} и умножаем RGB каждого пикселя на множитель
 * измерения. Инъекция именно на INVOKE upload() (а не TAIL) критична: тело метода
 * целиком внутри {@code if (this.updateLightTexture)}, поэтому наш код срабатывает
 * ТОЛЬКО когда карта реально пересобрана заново (иначе множитель применялся бы
 * повторно к уже затемнённым пикселям и мир чернел бы с каждым кадром).
 *
 * <p>Множитель применяется ко всей карте (и небесный, и блочный свет). Для тёмных
 * миров это ожидаемо: и дневная поверхность, и свет факелов чуть приглушены —
 * атмосфера «мёртвого» космического тела. Пещеры и так уходят в 0 из-за
 * {@code ambient_light=0.0}, множитель их не «поднимает».
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    /** Множитель яркости Луны: −70% → 0.30. */
    private static final float MOON_FACTOR = 0.30F;
    /** Множитель яркости Европы: −88% → 0.12. */
    private static final float EUROPA_FACTOR = 0.12F;

    @Shadow @Final private NativeImage lightPixels;

    @Inject(
        method = "updateLightTexture",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void gonzotech$dimAlienLight(float partialTick, CallbackInfo ci) {
        float factor = gonzotech$brightnessFactor();
        if (factor >= 1.0F || lightPixels == null) {
            return;
        }
        int w = lightPixels.getWidth();
        int h = lightPixels.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = lightPixels.getPixelRGBA(x, y);
                // NativeImage RGBA упакован как 0xAABBGGRR (альфа — старший байт).
                // Масштабируем три младших байта (цветовые каналы), альфу храним.
                int a = c & 0xFF000000;
                int b0 = Math.min(255, (int) ((c & 0xFF) * factor));          // R
                int b1 = Math.min(255, (int) (((c >> 8) & 0xFF) * factor));   // G
                int b2 = Math.min(255, (int) (((c >> 16) & 0xFF) * factor));  // B
                lightPixels.setPixelRGBA(x, y, a | (b2 << 16) | (b1 << 8) | b0);
            }
        }
    }

    /** Множитель яркости для текущего измерения игрока (1.0 = без изменений). */
    private static float gonzotech$brightnessFactor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 1.0F;
        }
        ResourceKey<Level> dim = mc.level.dimension();
        if (dim == SpaceDimensions.MOON) {
            return MOON_FACTOR;
        }
        if (dim == SpaceDimensions.EUROPA) {
            return EUROPA_FACTOR;
        }
        return 1.0F; // Марс и все прочие миры — без изменений
    }
}
