package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.gonzotech.sunevent.SunEventSpawnDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 — зонд на последний шаг перед рождением (диагностика v4.3).
 *
 * <p>{@code Mob#finalizeSpawn} в натуральном цикле вызывается непосредственно
 * перед {@code addFreshEntityWithPassengers}. Инъекция в базовый метод ловит
 * и переопределённые версии (Zombie/AbstractSkeleton вызывают super). Сходство
 * воронки: валидно → PositionCheck → финализация → создано — сравнение карт
 * показывает, попадает ли моб с валидной небесной позицией хотя бы до
 * финализации; если финализация есть, а создания нет — это уже аномалия
 * самого цикла, а не фильтра.
 */
@Mixin(Mob.class)
public abstract class MobFinalizeSpawnCensusMixin {

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void gonzotech$sunEventFinalizeCensus(
            ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
            SpawnGroupData spawnGroupData, CallbackInfoReturnable<SpawnGroupData> cir) {
        if (reason != EntitySpawnReason.NATURAL) {
            return;
        }
        Mob self = (Mob) (Object) this;
        if (self.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() != Level.OVERWORLD
            || serverLevel.getDifficulty() == Difficulty.PEACEFUL
            || !SunEventServer.monsterNightNow(serverLevel)) {
            return;
        }
        SunEventSpawnDebug.recordFinalize(self.getType(), serverLevel.canSeeSky(self.blockPosition()));
    }
}
