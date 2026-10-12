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
 * пресет → плоский; большие/расширенный используют тот же пресет → desolation
 * есть. Fail-safe: любая ошибка — список остаётся ванильным. Идемпотентно
 * (повторный вызов — no-op), боксы общие в {@link DesolationPresetExt}.
 */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class MultiNoiseParameterListDesolationMixin implements DesolationPresetExt {

    @Shadow
    @Final
    @Mutable
    private Climate.ParameterList<Holder<Biome>> parameters;

    @Override
    public void gonzotech$addDesolationPoints(Holder<Biome> desolation) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> live = this.parameters.values();
        if (DesolationPresetExt.contains(live)) {
            return; // уже добавлено (повторный хук/перезапуск хуков)
        }
        try {
            // Предпочитаем дописать in-place (внутренний список обычно mutable).
            for (Climate.ParameterPoint p : DesolationPresetExt.BOXES) {
                live.add(Pair.of(p, desolation));
            }
        } catch (Throwable immutable) {
            // Список неизменяемый — пересобираем ParameterList целиком.
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> copy = new ArrayList<>(live);
            for (Climate.ParameterPoint p : DesolationPresetExt.BOXES) {
                copy.add(Pair.of(p, desolation));
            }
            this.parameters = new Climate.ParameterList<>(copy);
        }
    }
}
