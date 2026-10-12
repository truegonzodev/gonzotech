package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationConversionFeature;
import com.gonzotech.core.worldgen.SurfaceRuinGenerator;
import com.gonzotech.core.worldgen.SurfaceWasteGenerator;
import com.mojang.logging.LogUtils;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Материальная конверсия «Пустоши» — на каждый генерируемый чанк, независимо
 * от биома центра.
 *
 * <p>История: placed-фича конверсии с {@code count+in_square+biome} исполнялась
 * только когда случайная точка размещения попадала в desolation; в чанках с
 * центром в чужом биоме её не было в списке фич вовсе. Граница получалась
 * рваной по чанкам и коррелировала со стволами (у них тот же случайный гейт) —
 * «есть деревья → есть замена».
 *
 * <p>Конверсия вызывается из {@code applyBiomeDecoration} на HEAD — до ванильных
 * фич, чтобы стволы вставали на уже изменённый грунт. Отдельные генераторы
 * развалин и отходов вызываются на RETURN, после обычной декорации поверхности.
 * Вызов идёт для каждого чанка любого генератора; конверсия проверяет биом
 * каждой колонки, а процедурные участки строятся детерминированно, без записи
 * в соседние чанки. Любая ошибка — fail-safe для соответствующей генерации.
 */
@Mixin(ChunkGenerator.class)
public abstract class ChunkDecorationDesolationMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean DESOLATION_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean RUINS_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean WASTE_FAILURE_LOGGED = new AtomicBoolean();

    private static void logFailureOnce(AtomicBoolean logged, String message, Throwable error) {
        // The full crash report is needed to diagnose a worldgen failure. Log it
        // once per generator so repeated chunk failures cannot flood the worker log.
        if (logged.compareAndSet(false, true)) {
            LOGGER.error(message, error);
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("HEAD"))
    private void gonzotech$convertDesolationColumns(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structures, CallbackInfo ci) {
        try {
            DesolationConversionFeature.convertChunk(level, chunk.getPos());
        } catch (Throwable t) {
            logFailureOnce(DESOLATION_FAILURE_LOGGED,
                "[gonzotech] desolation: conversion failed at chunk " + chunk.getPos(), t);
        }
    }

    @Inject(method = "applyBiomeDecoration", at = @At("RETURN"))
    private void gonzotech$generateSurfaceSites(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structures, CallbackInfo ci) {
        try {
            SurfaceRuinGenerator.generateForChunk(level, chunk.getPos());
        } catch (Throwable t) {
            logFailureOnce(RUINS_FAILURE_LOGGED,
                "[gonzotech] ruins: surface generation failed at chunk " + chunk.getPos(), t);
        }
        try {
            SurfaceWasteGenerator.generateForChunk(level, chunk.getPos());
        } catch (Throwable t) {
            logFailureOnce(WASTE_FAILURE_LOGGED,
                "[gonzotech] waste: surface generation failed at chunk " + chunk.getPos(), t);
        }
    }
}
