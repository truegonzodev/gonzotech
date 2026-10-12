package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationLiveCache;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Синхронизирует мемоизированный набор биомов с поздним инжектом Пустоши.
 *
 * <p>На открытии сохранения {@code ChunkGenerator.validate()} заранее
 * мемоизирует {@code possibleBiomes()} и порядок placed-фич. Затем загрузчик
 * мира добавляет Пустошь в источник биомов. Если не дополнить уже закэшированный
 * набор и не сбросить порядок фич, декорация нового чанка получает индекс -1
 * для placed-фичи Пустоши и падает в ванильном {@code applyBiomeDecoration}.
 * Возвращаем объединённый набор только для идентичного источника Overworld;
 * обработчик LevelEvent.Load после этого вызывает
 * {@code refreshFeaturesPerStep()} у его генератора.
 */
@Mixin(BiomeSource.class)
public abstract class BiomeSourcePossibleBiomesDesolationMixin {

    @Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
    private void gonzotech$includeDesolationInPossibleBiomes(
            CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
        try {
            BiomeSource source = (BiomeSource) (Object) this;
            Set<Holder<Biome>> original = cir.getReturnValue();
            Set<Holder<Biome>> augmented =
                DesolationLiveCache.includeDesolationInPossibleBiomes(source, original);
            if (augmented != original) {
                cir.setReturnValue(augmented);
            }
        } catch (Throwable t) {
            // Fail-safe: при любой ошибке сохраняем ванильный набор.
        }
    }
}
