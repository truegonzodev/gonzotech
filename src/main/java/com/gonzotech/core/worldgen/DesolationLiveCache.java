package com.gonzotech.core.worldgen;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * Статика живого инжекта «Пустоши».
 *
 * <p>Холдер и точный объект источника оверворлда кэшируются обработчиком
 * LevelEvent.Load. Отслеживание по идентичности не даёт менять параметры
 * Незера и других мультишумовых измерений. Отдельно расширяется результат
 * {@code BiomeSource.possibleBiomes()}: этот набор ваниль мемоизирует ещё до
 * загрузки мира, поэтому одной подмены {@code parameters()} недостаточно.
 */
public final class DesolationLiveCache {

    private static volatile Holder<Biome> desolation;
    private static volatile BiomeSource overworldSource;
    private static volatile Climate.ParameterList<Holder<Biome>> lastOriginal;
    private static volatile Climate.ParameterList<Holder<Biome>> lastAugmented;

    private static volatile BiomeSource possibleBiomesSource;
    private static volatile Set<Holder<Biome>> possibleBiomesInput;
    private static volatile Holder<Biome> possibleBiomesDesolation;
    private static volatile Set<Holder<Biome>> possibleBiomesOutput;

    private DesolationLiveCache() {
    }

    /** Пишет bound-холдер desolation любой хук, где доступны реестры. */
    public static synchronized void cacheDesolation(Holder<Biome> holder) {
        if (desolation != holder) {
            lastOriginal = null;
            lastAugmented = null;
            clearPossibleBiomesCache();
            overworldSource = null;
            desolation = holder;
        }
    }

    /** Запоминает источник загруженного оверворлда; {@code null} сбрасывает прошлый мир. */
    public static synchronized void trackOverworldSource(BiomeSource source) {
        if (overworldSource != source) {
            lastOriginal = null;
            lastAugmented = null;
            clearPossibleBiomesCache();
            overworldSource = source;
        }
    }

    public static Holder<Biome> desolation() {
        return desolation;
    }

    /** Проверка идентичности: динамически меняем только источник оверворлда. */
    public static boolean isOverworldSource(Object source) {
        return source == overworldSource;
    }

    /**
     * Включает desolation в уже мемоизированный BiomeSource-список только у
     * текущего источника оверворлда. ChunkGenerator строит по этому списку
     * индексированный порядок placed-фич, поэтому он должен видеть тот же набор
     * биомов, который позднее вернёт {@code parameters()}.
     */
    public static Set<Holder<Biome>> includeDesolationInPossibleBiomes(
            BiomeSource source, Set<Holder<Biome>> original) {
        Holder<Biome> holder = desolation;
        if (source != overworldSource || holder == null || original.contains(holder)) {
            return original;
        }

        Set<Holder<Biome>> cached = possibleBiomesOutput;
        if (cached != null
                && possibleBiomesSource == source
                && possibleBiomesInput == original
                && possibleBiomesDesolation == holder) {
            return cached;
        }

        synchronized (DesolationLiveCache.class) {
            holder = desolation;
            if (source != overworldSource || holder == null || original.contains(holder)) {
                return original;
            }
            cached = possibleBiomesOutput;
            if (cached != null
                    && possibleBiomesSource == source
                    && possibleBiomesInput == original
                    && possibleBiomesDesolation == holder) {
                return cached;
            }

            Set<Holder<Biome>> augmented = new HashSet<>(original);
            augmented.add(holder);
            Set<Holder<Biome>> result = Set.copyOf(augmented);
            possibleBiomesSource = source;
            possibleBiomesInput = original;
            possibleBiomesDesolation = holder;
            possibleBiomesOutput = result;
            return result;
        }
    }

    /** Кэш по идентичности: расширенный список строится один раз на исходный. */
    public static Climate.ParameterList<Holder<Biome>> augmentedFor(
            Climate.ParameterList<Holder<Biome>> original) {
        return original == lastOriginal ? lastAugmented : null;
    }

    public static void remember(Climate.ParameterList<Holder<Biome>> original,
                                Climate.ParameterList<Holder<Biome>> augmented) {
        lastOriginal = original;
        lastAugmented = augmented;
    }

    private static void clearPossibleBiomesCache() {
        // Публикуем null первым, чтобы читатель не принял старый результат за актуальный.
        possibleBiomesOutput = null;
        possibleBiomesSource = null;
        possibleBiomesInput = null;
        possibleBiomesDesolation = null;
    }
}
