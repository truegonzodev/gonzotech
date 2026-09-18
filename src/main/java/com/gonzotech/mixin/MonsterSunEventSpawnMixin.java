package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.gonzotech.sunevent.SunEventSpawnDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 (монстры) — ночной спавн на всём багровом периоде.
 *
 * <p>Автор (2026-09-18): «монстры должны спавниться весь багровый день,
 * весь ивент — просто не отключаем ночной спавн. С E−1 22000 до E 14500
 * такие же настройки спавна мобов, как ночью». <b>Слизней НЕ трогаем</b>
 * (автор: ванильный спавн только в болотах/слайм-чанках; категорийный гейт
 * MONSTER пускал их в любом биоме и забирал весь моб-кап — исключён явно).
 *
 * <p>Гейт здесь — в {@link SpawnPlacements#checkSpawnRules}: натуральный
 * спавн идёт {@code NaturalSpawner → isValidSpawnPostitionForType →
 * SpawnPlacements.checkSpawnRules → регистрированный предикат
 * (Monster::checkMonsterSpawnRules для Z/S/крипера/эндермена/ведьмы)}.
 * В монстровом окне (E−1 22000 → E 14500, Оверворлд, не мирная сложность)
 * отвечаем true для MONSTER типов — безусловно, как ночью. Кап, дистанция 24,
 * структуры, heightmap-плейсмент, noCollision, finalizeSpawn — ванильные.
 *
 * <p>Горение — отдельно: {@code MobSunEventBurnMixin} (E, только 4500–7500).
 * Диагностика — {@code SunEventSpawnDebug}: пропуски здесь, реальное создание
 * монстров (небо/пещера) — {@code ServerLevelMonsterSpawnCensusMixin};
 * мгновенный осмотр — {@code /gonzotech debug sunevent spawntest}.
 */
@Mixin(SpawnPlacements.class)
public abstract class MonsterSunEventSpawnMixin {

    /** Окно монстр-спавна (E−1 22000 → E 14500, Оверворлд): правилами считается пройдено. */
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private static <T extends net.minecraft.world.entity.Entity> void gonzotech$sunEventNightSpawnWindow(
            EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason,
            BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        if (reason != EntitySpawnReason.NATURAL || type.getCategory() != MobCategory.MONSTER) {
            return;
        }
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() != Level.OVERWORLD
            || serverLevel.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        if (!SunEventServer.monsterNightNow(serverLevel)) {
            SunEventSpawnDebug.onWindowClosed();
            return;
        }
        SunEventSpawnDebug.recordAttempt(serverLevel);
        if (type == EntityType.SLIME) {
            return; // автор: слизней НЕ трогаем — ванильные болота/слайм-чанки
        }
        SunEventSpawnDebug.recordPermit(serverLevel.canSeeSky(pos));
        cir.setReturnValue(true);
    }
}
