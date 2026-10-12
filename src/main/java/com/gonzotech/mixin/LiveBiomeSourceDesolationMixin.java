package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationPresetExt;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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
 * <p>Холдер desolation кэшируется статикой из {@code DesolationPresetInject}
 * (туда он попадает из любого хука, где доступны реестры). Идемпотентно:
 * {@link DesolationPresetExt#contains} — если точки уже есть (первое создание
 * мира, мутация записи реестра), подмена не выполняется. Быстро: расширенный
 * список строится один раз на исходный объект (кэш по идентичности).
 * Fail-safe: любая ошибка/нет холдера — возврат остаётся ванильным.
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class LiveBiomeSourceDesolationMixin {

    @Unique
    private static Holder<Biome> gonzotech$cachedDesolation;

    @Unique
    private static Climate.ParameterList<Holder<Biome>> gonzotech$lastOriginal;

    @Unique
    private static Climate.ParameterList<Holder<Biome>> gonzotech$lastAugmented;

    /** Вызывается из DesolationPresetInject, когда реестры доступны. */
    public static void gonzotech$cacheDesolation(Holder<Biome> desolation) {
        gonzotech$cachedDesolation = desolation;
    }

    @Inject(method = "parameters", at = @At("RETURN"), cancellable = true)
    private void gonzotech$augmentParameters(
            CallbackInfoReturnable<Climate.ParameterList<Holder<Biome>>> cir) {
        try {
            Holder<Biome> des = gonzotech$cachedDesolation;
            if (des == null) {
                return; // реестры ещё не дали холдер — ванильный возврат
            }
            Climate.ParameterList<Holder<Biome>> original = cir.getReturnValue();
            if (original == gonzotech$lastOriginal) {
                cir.setReturnValue(gonzotech$lastAugmented);
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
            gonzotech$lastOriginal = original;
            gonzotech$lastAugmented = augmented;
            cir.setReturnValue(augmented);
        } catch (Throwable t) {
            // fail-safe: источник остаётся ванильным
        }
    }
}
