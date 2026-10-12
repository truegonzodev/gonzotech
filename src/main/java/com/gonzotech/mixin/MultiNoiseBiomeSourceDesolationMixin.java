package com.gonzotech.mixin;

import com.gonzotech.core.worldgen.DesolationBiomeInject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Десоляция в оверворлде — подмена биома на этапе генерации (автор, 2026-10-12).
 *
 * <p>Перехватываем {@link MultiNoiseBiomeSource#getNoiseBiome(Climate.TargetPoint)}
 * (через него идёт и int-перегрузка — она делегирует сюда). Если климат-точка
 * попадает в один из четырёх гиперкубов десоляции
 * ({@link DesolationBiomeInject#BOXES}) — возвращаем её вместо ванильного
 * биома. Генератор/пресет не трогаем, поэтому суперплоский и прочие типы мира
 * остаются собой.
 *
 * <p>Fail-safe: если холдер биома ещё не захвачен или что-то пошло не так —
 * НЕ отменяем возврат, остаётся ванильный биом. Краш невозможен; худший
 * исход — десоляция не появится.
 *
 * <p>Диагностика: одноразовые логи «hook вызван» и «первая инъекция» — по ним
 * в логе видно, какая ступень сработала, а какая молчит.
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceDesolationMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
        method = "getNoiseBiome(Lnet/minecraft/world/level/biome/Climate$TargetPoint;)Lnet/minecraft/core/Holder;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void gonzotech$injectDesolation(Climate.TargetPoint target,
                                            CallbackInfoReturnable<Holder<Biome>> cir) {
        try {
            if (DesolationBiomeInject.HOOK_LOGGED.compareAndSet(false, true)) {
                LOGGER.info("[gonzotech] desolation hook: первый вызов "
                    + "getNoiseBiome(TargetPoint) — mixin работает");
            }
            Holder<Biome> desolation = DesolationBiomeInject.get();
            if (desolation == null) {
                return; // холдер не захвачен — ваниль
            }
            for (Climate.ParameterPoint box : DesolationBiomeInject.BOXES) {
                if (gonzotech$matches(box, target)) {
                    if (DesolationBiomeInject.INJECT_LOGGED.compareAndSet(false, true)) {
                        LOGGER.info("[gonzotech] desolation: первая инъекция биома "
                            + "в шумовой оверворлд");
                    }
                    cir.setReturnValue(desolation);
                    return;
                }
            }
        } catch (Throwable ignored) {
            // fail-safe: оставляем ванильный биом
        }
    }

    /** Попадает ли климат-точка в гиперкуб (глубина намеренно не проверяется). */
    private static boolean gonzotech$matches(Climate.ParameterPoint box, Climate.TargetPoint t) {
        return gonzotech$within(box.temperature(), t.temperature())
            && gonzotech$within(box.humidity(), t.humidity())
            && gonzotech$within(box.continentalness(), t.continentalness())
            && gonzotech$within(box.erosion(), t.erosion())
            && gonzotech$within(box.weirdness(), t.weirdness());
    }

    private static boolean gonzotech$within(Climate.Parameter range, long value) {
        return range.min() <= value && value <= range.max();
    }
}
