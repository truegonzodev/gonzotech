package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 (монстры) — горение на солнце в багровый день E.
 *
 * <p>Автор: в багровый день монстры горят <b>ТОЛЬКО в полдень</b> — ванильное
 * окно тика 4500–7500 (истощённое красное солнце «жжёт» только в зените).
 * Всё горение монстров (зомби/скелеты/утопленники/фантомы…) идёт через
 * {@link Mob#isSunBurnTick} (там же живёт логика шлемов: Zombie/AbstractSkeleton
 * зовут его из aiStep), поэтому одна HEAD-инъекция покрывает всех.
 *
 * <p>В день E вне окна возвращаем false. В окне — отдаём ванили (canSeeSky,
 * дождь, шлем, частичный свет-значение) → поджог естественный. В обычные дни
 * и в багровую ночь инъекция молчит, ваниль отрабатывает сама.
 *
 * <p>Игрока не трогаем (isSunBurnTick — про Mob'ов, не про игрока).
 * Спавн — отдельно: {@code MonsterSunEventSpawnMixin}.
 */
@Mixin(Mob.class)
public abstract class MobSunEventBurnMixin {

    /** Полуденное окно горения в день E (автор): тики суток 4500–7500. */
    private static final long BURN_WINDOW_MIN = 4500L;
    private static final long BURN_WINDOW_MAX = 7500L;

    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void gonzotech$sunEventNoonBurnOnly(CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        if (!(self.level() instanceof ServerLevel serverLevel)
            || serverLevel.dimension() != Level.OVERWORLD) {
            return;
        }
        if (!SunEventServer.eventDayNow(serverLevel)) {
            return; // обычный день — ваниль
        }
        long timeOfDay = serverLevel.getDayTime() % 24000L;
        if (timeOfDay < BURN_WINDOW_MIN || timeOfDay > BURN_WINDOW_MAX) {
            cir.setReturnValue(false); // багровое солнце не жжёт вне полудня
        }
        // в окне 4500–7500 — ванильная проверка (canSeeSky/дождь/шлем/яркость)
    }
}
