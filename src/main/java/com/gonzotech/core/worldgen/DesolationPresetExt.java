package com.gonzotech.core.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * Мост в mixin {@code MultiNoiseParameterListDesolationMixin}: дописывание
 * климат-точек «Пустоши» в уже построенный {@code Climate.ParameterList}
 * пресета оверворлда (автор, 2026-10-12).
 *
 * <p>Вызывается ПОСЛЕ полной сборки реестров (ServerAboutToStartEvent), когда
 * холдеры bound и никакого RegistrySetBuilder-трекинга «висячих» ссылок уже
 * нет — в отличие от конструктора списка, где lookup выдаёт unbound-
 * плейсхолдеры и крашится «Unreferenced key» (крахи 0.3.190/0.3.191).
 */
public interface DesolationPresetExt {

    /** Дописать 4 климат-точки desolation в список параметров этого пресета. */
    void gonzotech$addDesolationPoints(Holder<Biome> desolation);
}
