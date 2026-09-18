package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 (монстры) — спавн в багровый день E.
 *
 * <p>Автор: в багровый день монстры <b>спавнятся днём как в темноте</b>
 * (Оверворлд, только день E). Ванильные монстры ({@code checkMonsterSpawnRules}:
 * зомби/скелеты/пауки/ведьмы/эндермены…) все проходят через статический
 * {@link Monster#isDarkEnoughToSpawn}: небо-жаль 15, блок-свет, локальная
 * яркость. В день E возвращаем «темно» безусловно — чистая семантика
 * «как в темноте». В обычные дни и в багровой ночи — ванильная логика
 * (инъекция ничего не возвращает, ваниль отрабатывает сама).
 *
 * <p>Игрока не трогаем (спавн-проверки — это про сущностей-монстров).
 * Горение — отдельно: {@code MobSunEventBurnMixin} (только полдень 4500–7500).
 */
@Mixin(Monster.class)
public abstract class MonsterSunEventSpawnMixin {

    /** День E, Оверворлд: света-гейт считается всегда пройденным («как в темноте»). */
    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void gonzotech$sunEventDaylightSpawn(ServerLevelAccessor level, BlockPos pos,
                                                        RandomSource random,
                                                        CallbackInfoReturnable<Boolean> cir) {
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() == Level.OVERWORLD && SunEventServer.eventDayNow(serverLevel)) {
            cir.setReturnValue(true);
        }
    }
}
