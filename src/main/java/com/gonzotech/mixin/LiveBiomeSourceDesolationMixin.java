package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationLiveCache;
import com.gonzotech.core.worldgen.DesolationPresetExt;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Дозаправка климат-точек в ЖИВОЙ источник биома оверворлда (автор, 2026-10-12).
 *
 * <p>Зачем: на ПЕРЕЗАХОДЕ интегрированного сервера к ServerAboutToStartEvent
 * датапак-реестры ещё не готовы, и источник мог построиться без наших точек:
 * /locate пишет «не удалось найти биом поблизости» стоя в нём, а новые чанки
 * генерируются ванильными (F3 показывает биом из сохранённых данных чанка).
 *
 * <p>Как: в 1.21.4 у {@code MultiNoiseBiomeSource} нет теневого поля со
 * списком — хранение на {@code Either} (пресет/прямой список), а список
 * отдаёт метод {@code parameters()} (проверено по свежим worldgen-модам под
 * 1.21.4: они shadow'ят именно {@code parameters()}). Поэтому перехватываем
 * ВОЗВРАТ {@code parameters()} и подменяем список расширенным: /locate
 * (findNearestBiome3d) и генерация чанков читают именно этот возврат.
 *
 * <p>Холдер desolation и кэш по идентичности живут в
 * {@link DesolationLiveCache} (mixin'ам запрещены статик-члены без private —
 * краш 0.3.197). Идемпотентно: {@link DesolationPresetExt#contains} — если
 * точки уже есть (мутация записи реестра сработала), подмена не выполняется.
 * Fail-safe: любая ошибка/нет холдера — возврат остаётся ванильным.
 * В этом классе НЕТ static-членов (требование mixin).
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class LiveBiomeSourceDesolationMixin {

    @Inject(method = "parameters", at = @At("RETURN"), cancellable = true)
    private void gonzotech$augmentParameters(
            CallbackInfoReturnable<Climate.ParameterList<Holder<Biome>>> cir) {
        try {
            Holder<Biome> des = DesolationLiveCache.desolation();
            if (des == null) {
                return; // реестры ещё не дали холдер — ванильный возврат
            }
            Climate.ParameterList<Holder<Biome>> original = cir.getReturnValue();
            Climate.ParameterList<Holder<Biome>> cached =
                DesolationLiveCache.augmentedFor(original);
            if (cached != null) {
                cir.setReturnValue(cached);
                return;
            }
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> values = original.values();
            if (DesolationPresetExt.contains(values)) {
                return; // точки уже есть (мутация записи реестра сработала)
            }
            List<Pair<Climate.ParameterPoint, Holder<Biome>>> copy = new ArrayList<>(values);
            for (Climate.ParameterPoint p : DesolationPresetExt.BOXES) {
                copy.add(Pair.of(p, des));
            }
            Climate.ParameterList<Holder<Biome>> augmented = new Climate.ParameterList<>(copy);
            DesolationLiveCache.remember(original, augmented);
            cir.setReturnValue(augmented);
        } catch (Throwable t) {
            // fail-safe: источник остаётся ванильным
        }
    }
}
