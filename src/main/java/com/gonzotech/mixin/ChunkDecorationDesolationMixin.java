package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationConversionFeature;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.ChunkAccess;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.StructureManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Материальная конверсия «Пустоши» — на КАЖДЫЙ генерируемый чанк, независимо
 * от биома центра (автор, 2026-10-12).
 *
 * <p>История: placed-фича конверсии с {@code count+in_square+biome} исполнялась
 * только когда случайная точка размещения попадала в desolation; в чанках с
 * центром в чужом биоме её не было в списке фич вовсе. Граница получалась
 * рваной по чанкам и коррелировала со стволами (у них тот же случайный гейт) —
 * «есть деревья → есть замена».
 *
 * <p>Теперь конверсия вызывается из {@code applyBiomeDecoration} на HEAD —
 * до всех ванильных и наших фич, так что стволы встают уже на мёртвый грунт.
 * Вызов идёт для каждого чанка любого генератора (шумовые, суперплоский,
 * одиночный биом); внутри {@code convertChunk} каждая колонка проверяется на
 * СВОЙ биом, поэтому граница следует шумовой форме биома, а запись — только в
 * свой чанк (порядок генерации соседей не важен). В чужих биомах и измерениях —
 * дешёвый no-op. Любая ошибка — fail-safe: чанк остаётся ванильным.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkDecorationDesolationMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void gonzotech$convertDesolationColumns(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structures, CallbackInfo ci) {
        try {
            DesolationConversionFeature.convertChunk(level, chunk.getPos());
        } catch (Throwable t) {
            LOGGER.warn("[gonzotech] desolation: конверсия чанка не выполнена: {}",
                String.valueOf(t));
        }
    }
}
