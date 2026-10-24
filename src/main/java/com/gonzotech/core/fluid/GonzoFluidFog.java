package com.gonzotech.core.fluid;

import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

import java.util.Set;

/**
 * «Подводная» самоидентификация гонзо-жидкостей (0.3.281, автор 24.10).
 *
 * <p>Ваниль 1.21.4 решает, «под водой» ли камера, исключительно по тегам
 * {@code minecraft:water}/{@code lava} и блоку порошкового снега
 * ({@code Camera#getFluidInCamera}); NeoForge 21.4 не даёт API для своего
 * типа тумана (проверено: {@code getFogType} нет, профильный issue закрыт
 * без реализации). Тег {@code #minecraft:water} на наши жидкости дал бы
 * «правильный» FogType ценой всей игровой физики: утопление
 * ({@code LivingEntity:422}), плавание и рывки ({@code Player:351/753}),
 * тушение огня ({@code Entity:776}), лодки и дельфины ({@code Entity:1318}),
 * черепаший шлем ({@code Player:322}) — все эти проверки читают тот же тег.
 *
 * <p>Поэтому единственный минимальный рычаг — сам канал {@code getFluidInCamera}:
 * клиентский миксин {@code CameraGonzoFluidFogMixin} возвращает {@code WATER}
 * только когда камера реально погружена в нашу жидкость (тот же критерий по
 * камере, что у {@code ClientHooks.getFogColor}: {@code y < blockY + height}).
 * Потребителей у метода ровно три, все — рендер ( FogRenderer: слои тумана,
 * GameRenderer: подводный FOV, LevelRenderer: скай-пасс), геймплей не
 * затрагивается. Сторонние туман-менеджеры (Simple Fog Control и т.п.)
 * видят штатную воду и больше не сбрасывают дымку вместе с terrain-fog.
 */
public final class GonzoFluidFog {

    /** Лень: реестр замораживается раньше первого кадра, строить раньше нельзя. */
    private static volatile Set<Fluid> fogged;

    private GonzoFluidFog() {
    }

    /** Надлежит ли жидкость под «подводным» поведением тумана/оверлеев. */
    public static boolean isFoggedFluid(FluidState state) {
        if (state == null || state.isEmpty()) {
            return false;
        }
        Set<Fluid> local = fogged;
        if (local == null) {
            synchronized (GonzoFluidFog.class) {
                local = fogged;
                if (local == null) {
                    local = Set.of(
                            ModFluids.MOLTEN_CORIUM.get(), ModFluids.FLOWING_MOLTEN_CORIUM.get(),
                            ModFluids.ETHANOL.get(), ModFluids.FLOWING_ETHANOL.get(),
                            ModFluids.FORMALDEHYDE.get(), ModFluids.FLOWING_FORMALDEHYDE.get(),
                            ModFluids.SULFURIC_ACID.get(), ModFluids.FLOWING_SULFURIC_ACID.get(),
                            ModFluids.DISTILLATE.get(), ModFluids.FLOWING_DISTILLATE.get(),
                            ModFluids.MASH.get(), ModFluids.FLOWING_MASH.get(),
                            ModFluids.WORT.get(), ModFluids.FLOWING_WORT.get());
                    fogged = local;
                }
            }
        }
        return local.contains(state.getType());
    }
}
