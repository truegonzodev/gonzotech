package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 (монстры) — ночной спавн на всём багровом периоде.
 *
 * <p>Автор (2026-09-18): «монстры должны спавниться весь багровый день,
 * весь ивент — просто не отключаем ночной спавн. С E−1 22000 до E 14500
 * такие же настройки спавна мобов, как ночью».
 *
 * <p>Диагностика ванильной цепочки 1.21.4 (декомпил): натуральный спавн
 * идёт {@code NaturalSpawner.spawnCategoryForPosition → isValidSpawnPostitionForType →
 * SpawnPlacements.checkSpawnRules → зарегистрированный предикат
 * (Monster::checkMonsterSpawnRules для Z/S/C/крипера/эндермена/ведьмы)}.
 * Гейт здесь — в {@link SpawnPlacements#checkSpawnRules}: та же дорога,
 * по которой проходят слизни (light-независимый предикат, спавнятся всегда).
 * Заменяем ответ для категории MONSTER + NATURAL в монстровом окне своим
 * true — безусловно, как ночью (в окне свет-гейта нет вовсе).
 * Кап монстров, структуры, heightmap-плейсмент, noCollision — по-прежнему
 * ванильные («настройки спавна мобов как ночью» — без читов).
 *
 * <p>Горение — отдельно: {@code MobSunEventBurnMixin} (E, только 4500–7500).
 */
@Mixin(SpawnPlacements.class)
public abstract class MonsterSunEventSpawnMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Троттл диагностического лога (gameTime последней строки). */
    private static long gonzotech$lastLog = -1L;

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
            || serverLevel.getDifficulty() == Difficulty.PEACEFUL
            || !SunEventServer.monsterNightNow(serverLevel)) {
            return;
        }
        cir.setReturnValue(true);
        if (serverLevel.getGameTime() - gonzotech$lastLog >= 400) {
            gonzotech$lastLog = serverLevel.getGameTime();
            LOGGER.info("[Gonzo Tech] Суневет: монстр-спавн пропущен (окно) {} @ {} время={}",
                BuiltInRegistries.ENTITY_TYPE.getKey(type), pos, serverLevel.getDayTime() % 24000L);
        }
    }
}
