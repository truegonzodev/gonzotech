package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.gonzotech.sunevent.SunEventSpawnDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 — локализатор утечки (диагностика v4.2).
 *
 * <p>Данные v4.1 (автор, 2026-09-18 22:01–22:05): «пропущено небо 673/694» при
 * «создано на поверхности 0», тогда как «под землёй» идёт ночным темпом
 * (116/235 за минуту). Аудит пост-гейта показал: света там нет НИГДЕ
 * ({@code Mob.checkSpawnRules}=true, обструкция — жидкость/коллизии, NeoForge
 * PositionCheck — DEFAULT без света). Локализуем ШАГ утечки вторым измерителем:
 * {@code isValidSpawnPostitionForType} возвращает
 * {@code checkSpawnRules(наш гейт) → noCollision(spawnAABB)} — считаем его
 * return-true с раскладкой небо/тень:
 * <ul>
 *   <li>валидных ≈ пропускам → AABB чист, утечка ниже (SpawnState.canSpawn /
 *   PositionCheck / finalizeSpawn);
 *   <li>валидных ≈ 0 на небе → утечка = {@code noCollision(getSpawnAABB)}.
 * </ul>
 *
 * <p>Пары с {@code MonsterSunEventSpawnMixin} (permits) и
 * {@code ServerLevelMonsterSpawnCensusMixin} (созданные).
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerPositionValidMixin {

    @Inject(method = "isValidSpawnPostitionForType", at = @At("RETURN"))
    private static void gonzotech$sunEventPositionValidCensus(
            ServerLevel level, MobCategory category, StructureManager structureManager,
            ChunkGenerator generator, MobSpawnSettings.SpawnerData spawnerData,
            BlockPos.MutableBlockPos pos, double distSqr, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || category != MobCategory.MONSTER) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD || !SunEventServer.monsterNightNow(level)) {
            return;
        }
        SunEventSpawnDebug.recordValid(spawnerData.type(), level.canSeeSky(pos));
    }
}
