package com.gonzotech.core.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

/**
 * Статика живого инжекта «Пустоши» (автор, 2026-10-12).
 *
 * <p>Вынесена из mixin-класса отдельно: mixin'ам запрещены non-private
 * static-методы (краш bootstrap'а 0.3.197: «contains non-private static
 * method»), а кэш нужен и обработчику событий (пишет холдер, когда доступны
 * реестры), и {@code LiveBiomeSourceDesolationMixin} (читает его в перехвате
 * возврата {@code parameters()}).
 */
public final class DesolationLiveCache {

    private static volatile Holder<Biome> desolation;
    private static Climate.ParameterList<Holder<Biome>> lastOriginal;
    private static Climate.ParameterList<Holder<Biome>> lastAugmented;

    private DesolationLiveCache() {
    }

    /** Пишет bound-холдер desolation любой хук, где доступны реестры. */
    public static void cacheDesolation(Holder<Biome> holder) {
        desolation = holder;
    }

    public static Holder<Biome> desolation() {
        return desolation;
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
}
