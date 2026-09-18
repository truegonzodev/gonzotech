package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.gonzotech.sunevent.SunEventSpawnDebug;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 — зонд на NeoForge PositionCheck (диагностика v4.3).
 *
 * <p>{@code EventHooks.checkSpawnPosition} — единственное место между
 * «позиция валидна» и {@code finalizeSpawn}, где в ход вмешиваются
 * ивент-слушатели ({@code MobSpawnEvent.PositionCheck} может DENY). Служебно
 * считаем ок/отказ по типам с раскладкой небо/тень: если небесные позиции
 * уходят в {@code отказ} — блокер найден дословно (и чинится слушателем,
 * форсирующим ALLOW в монстровом окне — это и будет финальный фикс под
 * правило автора «не трогать пещеры/shaded, менять только поверхность»).
 */
@Mixin(EventHooks.class)
public abstract class NeoforgePositionCheckCensusMixin {

    @Inject(method = "checkSpawnPosition", at = @At("TAIL"))
    private static void gonzotech$sunEventPositionCheckCensus(
            Mob mob, ServerLevelAccessor level, EntitySpawnReason reason,
            CallbackInfoReturnable<Boolean> cir) {
        if (reason != EntitySpawnReason.NATURAL
            || mob.getType().getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() != Level.OVERWORLD
            || serverLevel.getDifficulty() == Difficulty.PEACEFUL
            || !SunEventServer.monsterNightNow(serverLevel)) {
            return;
        }
        SunEventSpawnDebug.recordPositionCheck(
            mob.getType(), serverLevel.canSeeSky(mob.blockPosition()), cir.getReturnValue());
    }
}
