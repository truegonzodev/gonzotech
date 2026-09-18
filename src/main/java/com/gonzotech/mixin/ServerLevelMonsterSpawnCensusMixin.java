package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.gonzotech.sunevent.SunEventSpawnDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 — перепись РЕАЛЬНО созданных монстров (диагностика).
 *
 * <p>Пары со {@code MonsterSunEventSpawnMixin}: тот считает проверки, пропущенные
 * гейтом, а этот — монстров, фактически добавленных в мир
 * ({@code ServerLevel#addFreshEntity} вызывается у каждого натурального спавна,
 * включая пак finalize'нутых), с раскладкой «видит небо / пещера». Если пропуски
 * идут сотнями, а «на поверхности» — ноль, дыра между гейтом и addFreshEntity;
 * если «на поверхности» растёт, а автор их в мире не находит — вопрос наблюдения
 * (спектатор, деспавн за 128 при полёте).
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMonsterSpawnCensusMixin {

    @Inject(method = "addFreshEntity", at = @At("HEAD"))
    private void gonzotech$sunEventMonsterCensus(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != Level.OVERWORLD || !SunEventServer.monsterNightNow(level)) {
            return;
        }
        SunEventSpawnDebug.recordSpawn(entity.getType(), level.canSeeSky(entity.blockPosition()));
    }
}
