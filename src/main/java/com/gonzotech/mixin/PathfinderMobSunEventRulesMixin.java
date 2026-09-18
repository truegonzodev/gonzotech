package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 — ФИНАЛЬНЫЙ гейт поверхности (v5).
 *
 * <p>Корневая причина «пропуски десятками тысяч, поверхность пуста»
 * (воронка v4.3, полигон 2026-09-18): последний инстанс-уровень ванили —
 * {@link PathfinderMob#checkSpawnRules}: {@code getWalkTargetValue(pos) >= 0}.
 * У {@code Monster} walk-target = {@code -getPathfindingCostFromLightLevels(pos)}
 * — дневная ОТКРЫТАЯ позиция даёт отрицательную цену → false. Слизь наследует
 * {@code Mob} напрямую (НЕ PathfinderMob) — потому на плоскости спавнилась она
 * одна; тьма (пещеры/ночь) даёт ≥ 0 и проходит сама. Ванильный
 * чек цел: мы добавляем ответ true ТОЛЬКО для монстров естественного спавна
 * в монстровом окне (E−1 22000 → E 14500, Оверворлд, не мирная). Семантика
 * дословно по директиве автора «трогать только поверхность»: тёмные позиции
 * проходили этот чек и до нас — изменяется поведение лишь для освещённых.
 *
 * <p>Первая половина моста — {@code MonsterSunEventSpawnMixin} (перекрывает
 * зарегистрированный предикат уровня правил). Оба слоя нужны: ваниль требует
 * оба. Кап/дистанция/плейсмент/noCollision/обструкция — ванильные.
 */
@Mixin(PathfinderMob.class)
public abstract class PathfinderMobSunEventRulesMixin {

    /** Монстровое окно: инстанс-уровень «предпочтения тьмы» отключён для естественного спавна. */
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void gonzotech$sunEventSurfaceSpawnRules(
            LevelAccessor level, EntitySpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof Monster)) {
            return; // слизни/животные/големы — ваниль (слизень наследует Mob — сюда и не попадает)
        }
        if (reason != EntitySpawnReason.NATURAL || !(level instanceof ServerLevelAccessor accessor)) {
            return; // спавнеры, подкрепления, структуры — ваниль
        }
        ServerLevel serverLevel = accessor.getLevel();
        if (serverLevel.dimension() != Level.OVERWORLD
            || serverLevel.getDifficulty() == Difficulty.PEACEFUL
            || !SunEventServer.monsterNightNow(serverLevel)) {
            return;
        }
        cir.setReturnValue(true);
    }
}
