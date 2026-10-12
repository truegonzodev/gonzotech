package com.gonzotech.mixin;

import com.gonzotech.GonzoTechMod;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * «Пустошь» в шумовом оверворлде — добавление климат-точек в пресет
 * (автор, 2026-10-12; взамен dimension-оверрайда, preset-реестра и
 * подмены getNoiseBiome).
 *
 * <p>Биомная сетка чанка, цвета неба/травы/воды, F3 и /locate берут биом из
 * {@code MultiNoiseBiomeSource}, чей пресет оверворлда — это
 * {@link MultiNoiseBiomeSourceParameterList} с {@code Climate.ParameterList}.
 * Дописываем в этот список 4 климат-точки desolation — дальше ванильный
 * nearest-neighbor ({@code findValue}) сам расставит биом с правильной
 * частотой и гладкими границами. Генератор не трогаем: суперплоский
 * (FixedBiomeSource) остаётся плоским; большие/расширенный используют тот же
 * пресет → desolation появляется и там.
 *
 * <p>Почему не крашится (в отличие от mixin в OverworldBiomeBuilder):
 * конструктор списка вызывается и во время ванильного bootstrap'а, где
 * модовых биомов нет. Берём биом МЯГКО — {@code holderGetter.get(key)}:
 * пусто → молча пропускаем, ни одной «висячей» ссылки не возникает. В слое
 * датапака биом существует → точки добавляются.
 *
 * <p>Оверворлд отличаем по наличию {@code minecraft:plains} в списке (в
 * nether-пресете его нет). Fail-safe: любая ошибка → список не меняется.
 */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class MultiNoiseParameterListDesolationMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    @Mutable
    private Climate.ParameterList<Holder<Biome>> parameters;

    /** Ключ биома «Пустошь» (data/gonzotech/worldgen/biome/desolation.json). */
    private static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    /** Четыре климат-гиперкуба desolation (те же, что в удалённом dimension-оверрайде). */
    private static final Climate.ParameterPoint[] BOXES = {
        box(0.15F, 0.50F, -0.35F, -0.05F,  0.20F,  0.50F, 0.15F, 0.45F, -0.15F,  0.25F),
        box(0.15F, 0.50F,  0.00F,  0.25F,  0.40F,  0.70F, 0.35F, 0.65F, -0.45F, -0.15F),
        box(0.15F, 0.50F, -0.35F, -0.05F, -0.19F, -0.11F, 0.15F, 0.45F, -0.15F,  0.25F),
        box(0.15F, 0.50F,  0.00F,  0.25F, -0.19F, -0.11F, 0.35F, 0.65F, -0.45F, -0.15F),
    };

    @Inject(
        method = "<init>(Lnet/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList$Preset;Lnet/minecraft/core/HolderGetter;)V",
        at = @At("RETURN")
    )
    private void gonzotech$appendDesolation(MultiNoiseBiomeSourceParameterList.Preset preset,
                                            HolderGetter<Biome> biomes,
                                            CallbackInfo ci) {
        try {
            // Только оверворлд: в его списке всегда есть plains, в nether — нет.
            boolean overworld = false;
            for (Pair<Climate.ParameterPoint, Holder<Biome>> e : this.parameters.values()) {
                if (e.getSecond().is(Biomes.PLAINS)) {
                    overworld = true;
                    break;
                }
            }
            if (!overworld) {
                return;
            }
            // Мягкий lookup: во время ванильного bootstrap'а биома ещё нет —
            // пропускаем, не создавая «висячих» ссылок (причина краха 0.3.185).
            Optional<Holder.Reference<Biome>> des = biomes.get(DESOLATION);
            if (des.isEmpty()) {
                return;
            }
            Holder<Biome> holder = des.get();
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> live = this.parameters.values();
            try {
                for (Climate.ParameterPoint p : BOXES) {
                    live.add(Pair.of(p, holder));
                }
            } catch (Throwable immutable) {
                // Список неизменяемый — пересобираем ParameterList целиком.
                List<Pair<Climate.ParameterPoint, Holder<Biome>>> copy = new ArrayList<>(live);
                for (Climate.ParameterPoint p : BOXES) {
                    copy.add(Pair.of(p, holder));
                }
                this.parameters = new Climate.ParameterList<>(copy);
            }
            LOGGER.info("[gonzotech] desolation: +4 климат-точки в пресет оверворлда");
        } catch (Throwable ignored) {
            // fail-safe: пресет остаётся ванильным
        }
    }

    private static Climate.ParameterPoint box(float t0, float t1, float h0, float h1,
                                              float c0, float c1, float e0, float e1,
                                              float w0, float w1) {
        return new Climate.ParameterPoint(
            Climate.Parameter.span(t0, t1),
            Climate.Parameter.span(h0, h1),
            Climate.Parameter.span(c0, c1),
            Climate.Parameter.span(e0, e1),
            Climate.Parameter.point(0.0F), // depth — как в исходном dimension-файле
            Climate.Parameter.span(w0, w1),
            0L);
    }
}
