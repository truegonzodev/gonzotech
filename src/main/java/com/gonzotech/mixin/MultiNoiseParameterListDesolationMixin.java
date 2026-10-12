package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationPresetExt;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * «Пустошь» в шумовом оверворлде — дописывание климат-точек в УЖЕ ПОСТРОЕННЫЙ
 * пресет (автор, 2026-10-12).
 *
 * <p>История: dimension-оверрайд ломает суперплоский; preset-реестр принимает
 * только {@code {"preset":...}}; mixin в {@code OverworldBiomeBuilder} и в
 * конструктор {@code MultiNoiseBiomeSourceParameterList} крашат bootstrap
 * («Unreferenced key»), т.к. lookup RegistrySetBuilder выдаёт unbound-
 * плейсхолдеры и сам факт обращения регистрирует висячую ссылку.
 *
 * <p>Поэтому точек здесь не добавляют во время сборки. Метод
 * {@link #gonzotech$addDesolationPoints} вызывается из
 * {@code DesolationPresetInject} на {@code ServerAboutToStartEvent}, когда
 * реестры полностью собраны, холдер bound, и меняется просто поле
 * {@code parameters} готового объекта {@code minecraft:overworld}. Ванильный
 * {@code findValue} (nearest-neighbor) затем сам расставляет биом: частота,
 * гладкие границы, цвета, F3, /locate, конверсия — всё из биомной сетки.
 *
 * <p>Генератор не трогаем: суперплоский (FixedBiomeSource) не использует
 * пресет → плоский; большие/расшишенный используют тот же пресет → desolation
 * есть. Fail-safe: любая ошибка — список остаётся ванильным.
 */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class MultiNoiseParameterListDesolationMixin implements DesolationPresetExt {

    @Shadow
    @Final
    @Mutable
    private Climate.ParameterList<Holder<Biome>> parameters;

    /**
     * Шесть климат-гиперкубов desolation. 0.3.194 (заявка автора: биом в 3–4
     * раза крупнее, в 2–3 раза чаще — ~1300 блоков между биомами, 16–40
     * чанков на биом): пролёты расширены ~в 1.7–2 раза против 0.3.193 и
     * добавлена холодная пара t[-0.45;0.02]. Если после живого прогона биом
     * великоват/маловат — тюнинг только здесь.
     */
    private static final Climate.ParameterPoint[] BOXES = {
        box(0.05F, 0.60F, -0.50F, -0.02F,  0.10F,  0.60F, 0.05F, 0.55F, -0.30F,  0.40F),
        box(0.05F, 0.60F, -0.02F,  0.45F,  0.30F,  0.80F, 0.25F, 0.75F, -0.60F, -0.05F),
        box(0.05F, 0.60F, -0.50F, -0.02F, -0.30F, -0.02F, 0.05F, 0.55F, -0.30F,  0.40F),
        box(0.05F, 0.60F, -0.02F,  0.45F, -0.30F, -0.02F, 0.25F, 0.75F, -0.60F, -0.05F),
        box(-0.45F, 0.02F, -0.50F, -0.02F,  0.10F,  0.60F, 0.05F, 0.55F, -0.30F,  0.40F),
        box(-0.45F, 0.02F, -0.02F,  0.45F,  0.30F,  0.80F, 0.25F, 0.75F, -0.60F, -0.05F),
    };

    @Override
    public void gonzotech$addDesolationPoints(Holder<Biome> desolation) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> live = this.parameters.values();
        try {
            // Предпочитаем дописать in-place (внутренний список обычно mutable).
            for (Climate.ParameterPoint p : BOXES) {
                live.add(Pair.of(p, desolation));
            }
        } catch (Throwable immutable) {
            // Список неизменяемый — пересобираем ParameterList целиком.
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> copy = new ArrayList<>(live);
            for (Climate.ParameterPoint p : BOXES) {
                copy.add(Pair.of(p, desolation));
            }
            this.parameters = new Climate.ParameterList<>(copy);
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
