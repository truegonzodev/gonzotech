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

    /** Четыре климат-гиперкуба desolation (те же, что в удалённом dimension-оверрайде). */
    private static final Climate.ParameterPoint[] BOXES = {
        box(0.15F, 0.50F, -0.35F, -0.05F,  0.20F,  0.50F, 0.15F, 0.45F, -0.15F,  0.25F),
        box(0.15F, 0.50F,  0.00F,  0.25F,  0.40F,  0.70F, 0.35F, 0.65F, -0.45F, -0.15F),
        box(0.15F, 0.50F, -0.35F, -0.05F, -0.19F, -0.11F, 0.15F, 0.45F, -0.15F,  0.25F),
        box(0.15F, 0.50F,  0.00F,  0.25F, -0.19F, -0.11F, 0.35F, 0.65F, -0.45F, -0.15F),
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
