package com.gonzotech.mixin.client;

import com.gonzotech.space.client.SpaceSkyEffects;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * РАЗВЯЗКА ОСВЕЩЕНИЯ ДНЯ/НОЧИ ОТ ВАНИЛЬНОГО ВРЕМЕНИ (Патч #7, часть 3).
 *
 * <p>Задача: затемнить «день» в космических мирах (Луна −70%, Европа −88%; Марс
 * НЕ трогать) и привязать фактическую яркость мира к ПОЛОЖЕНИЮ СОЛНЦА, а не к
 * ванильному 24000-тиковому дню. Тогда на Луне ({@code cycleDays=60}) свет идёт
 * длинным циклом (~30 суток свет / ~30 тьма), а не обычными сутками.
 *
 * <p><b>Почему именно так, а не попиксельно.</b> В 1.21.4 лайтмап переписан на
 * GPU ({@code LightmapTextureManager} без {@code NativeImage}-полей), поэтому
 * прежний попиксельный mixin падал ({@code @Shadow lightPixels not located}).
 * Публичный neoforge-хук {@code adjustLightmapColors} в 1.21.4 удалён. Осталась
 * ОДНА безопасная точка: вся дневная яркость лайтмапа берётся из
 * {@code ClientLevel.getSkyDarken(float)} (диапазон {@code [0.2, 1.0]}). Мы
 * подменяем её результат для нужных миров — без {@code @Shadow}, без обращения к
 * GPU/пиксельным полям, значит краша быть не может.
 *
 * <p>Целевой метод {@code getSkyDarken(float)} ПЕРЕОПРЕДЕЛЁН в самом
 * {@link ClientLevel} (не только унаследован от {@code Level}), поэтому
 * {@code @Inject} по нему находит цель. Имя стабильно много версий и совпадает в
 * Mojmap (NeoForge использует Mojmap и в dev, и в проде).
 *
 * <p>Марс и все НЕ-космические миры остаются на ванильном освещении: их
 * {@link SpaceSkyEffects#overridesSkyLight()} == {@code false} (или у мира вовсе
 * не наш скайбокс) → инъекция ничего не меняет.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSkyDarkenMixin {

    @Inject(method = "getSkyDarken(F)F", at = @At("HEAD"), cancellable = true)
    private void gonzotech$decoupleSkyLight(float partialTick,
                                            CallbackInfoReturnable<Float> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        DimensionSpecialEffects effects = self.effects();
        if (effects instanceof SpaceSkyEffects space && space.overridesSkyLight()) {
            cir.setReturnValue(space.computeSkyDarken(self, partialTick));
        }
    }
}
