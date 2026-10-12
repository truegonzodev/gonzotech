package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationPresetExt;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Дозаправка климат-точек в УЖЕ ПОСТРОЕННЫЙ источник биома (автор, 2026-10-12).
 *
 * <p>Зачем: на ПЕРВОМ создании мира датапак-реестры готовы уже к
 * ServerAboutToStartEvent, и мутации записи реестра хватает. На ПЕРЕЗАХОДЕ
 * (интегрированный сервер) источник может быть построен из level.dat/пресета
 * в момент, когда мутация записи ещё не прошла, — тогда /locate пишет «не
 * удалось найти биом поблизости», стоя в нём, а новые чанки генерируются без
 * desolation (F3 при этом показывает биом из сохранённых данных чанка).
 *
 * <p>Поэтому на {@code LevelEvent.Load} (сразу после постройки ServerLevel,
 * реестры гарантированно готовы) {@code DesolationPresetInject} берёт живой
 * {@code MultiNoiseBiomeSource} генератора оверворлда и дописывает точки прямо
 * в его поле {@code parameters} — /locate (findNearestBiome3d) и генерация
 * новых чанков читают именно его. Идемпотентно: если точки уже есть (первое
 * создание), {@link DesolationPresetExt#contains} даёт no-op. Fail-safe: любая
 * ошибка — источник остаётся ванильным.
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class LiveBiomeSourceDesolationMixin implements DesolationPresetExt {

    @Shadow
    @Final
    @Mutable
    private Climate.ParameterList<Holder<Biome>> parameters;

    @Override
    public void gonzotech$addDesolationPoints(Holder<Biome> desolation) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> live = this.parameters.values();
        if (DesolationPresetExt.contains(live)) {
            return; // уже добавлено другим хуком
        }
        try {
            for (Climate.ParameterPoint p : DesolationPresetExt.BOXES) {
                live.add(Pair.of(p, desolation));
            }
        } catch (Throwable immutable) {
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> copy = new ArrayList<>(live);
            for (Climate.ParameterPoint p : DesolationPresetExt.BOXES) {
                copy.add(Pair.of(p, desolation));
            }
            this.parameters = new Climate.ParameterList<>(copy);
        }
    }
}
