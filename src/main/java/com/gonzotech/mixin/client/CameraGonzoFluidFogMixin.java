package com.gonzotech.mixin.client;

import com.gonzotech.core.fluid.GonzoFluidFog;
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
 * (FogRenderer/водная ветка, которую сторонние туман-менеджеры не трогают),
 * подводный FOV (GameRenderer) и скай-пасс (LevelRenderer). Возвращаем
 * {@link FogType#WATER} при погружении по КАМЕРЕ (не по ногам) в любую из
 * семи гонзо-жидкостей; критерий поверхности совпадает с ванильным
 * {@code y < blockY + fluidHeight} и с гейтом {@code ClientHooks.getFogColor},
 * так что цвет и радиусы (modifyFogColor/modifyFogRender) накладываются
 * своим стандартным порядком. Теги {@code #minecraft:water} не трогаем —
 * иначе вместе с туманом приехали бы утопление, тушение огня и плавательная
 * физика (см. javadoc GonzoFluidFog).
 */
@Mixin(Camera.class)
public abstract class CameraGonzoFluidFogMixin {

    @Inject(method = "getFluidInCamera", at = @At("HEAD"), cancellable = true)
    private void gonzotech$gonzoFluidReadsAsWater(CallbackInfoReturnable<FogType> cir) {
        Camera self = (Camera) (Object) this;
        Entity viewer = self.getEntity();
        if (viewer == null) {
            return;
        }
        Level level = viewer.level();
        BlockPos cameraBlock = self.getBlockPosition();
        FluidState state = level.getFluidState(cameraBlock);
        if (!GonzoFluidFog.isFoggedFluid(state)) {
            return;
        }
        float surfaceY = cameraBlock.getY() + state.getHeight(level, cameraBlock);
        if (self.getPosition().y < (double) surfaceY) {
            cir.setReturnValue(FogType.WATER);
        }
    }
}
