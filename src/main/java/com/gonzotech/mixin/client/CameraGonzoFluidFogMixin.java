package com.gonzotech.mixin.client;

import com.gonzotech.core.fluid.GonzoFluidFog;
import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Камера в гонзо-жидкости = штатное подводное состояние (0.3.281).
 *
 * <p>Единственный не-инвазивный рычаг «чтобы сама игра понимала»:
 * {@code Camera#getFluidInCamera()} решает всё трио — слой тумана
 * (FogRenderer/ветка среды, которую сторонние туман-менеджеры не трогают),
 * подводный FOV (GameRenderer) и скай-пасс (LevelRenderer). Возвращаем среде
 * её ванильный {@link FogType}: WATER/LAVA для шести жидкостей и кориума,
 * POWDER_SNOW для мёртвой жижи; критерий погружения по КАМЕРЕ (не по ногам)
 * совпадает с ванильным
 * {@code y < blockY + fluidHeight} и с гейтом {@code ClientHooks.getFogColor},
 * так что цвет и радиусы (modifyFogColor/modifyFogRender) накладываются
 * своим стандартным порядком. Теги {@code #minecraft:water} не трогаем —
 * иначе вместе с туманом приехали бы утопление, тушение огня и плавательная
 * физика (см. javadoc GonzoFluidFog).
 */
@Mixin(Camera.class)
public abstract class CameraGonzoFluidFogMixin {

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void gonzotech$mediumSelfIdentification(CallbackInfoReturnable<FogType> cir) {
        Camera self = (Camera) (Object) this;
        Entity viewer = self.getEntity();
        if (viewer == null) {
            return;
        }
        Level level = viewer.level();
        BlockPos cameraBlock = self.getBlockPosition();
        // Мёртвая жижа — блок, не жидкость: среда порошкового снега (0.3.282,
        // автор). Ванильный POWDER_SNOW-пакет: плотный ближний туман (геометрию
        // даёт сам FogRenderer), небо и скай-пасс выключены, оверлей воды не
        // лезет, SFC-подобные туман-менеджеры веткой не трогают. Цвет мути
        // по-прежнему наш (ComputeFogColor красит в map color жижи).
        if (level.getBlockState(cameraBlock).is(ModBlocks.DEAD_SLIME_BLOCK.get())) {
            cir.setReturnValue(FogType.POWDER_SNOW);
            return;
        }
        FluidState state = level.getFluidState(cameraBlock);
        FogType medium = GonzoFluidFog.fluidMedium(state);
        if (medium == null) {
            return;
        }
        float surfaceY = cameraBlock.getY() + state.getHeight(level, cameraBlock);
        if (self.getPosition().y < (double) surfaceY) {
            cir.setReturnValue(medium);
        }
    }
}
