package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
 * такие же настройки спавна мобов, как ночью». <b>Слизней НЕ трогаем</b>
 * (автор: ванильный спавн только болотах/слайм-чанках; категорийный гейт
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
 * Диагностика: агрегатный лог раз в игровую минуту (1200 тиков) со счётчиками
 * пропущенных типов; мгновенный тест — {@code /gonzotech debug sunevent spawntest}.
 */
@Mixin(SpawnPlacements.class)
public abstract class MonsterSunEventSpawnMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Троттл агрегатного лога (gameTime последнего сброса). */
    private static long gonzotech$lastLogReset = -1L;
    /** Счётчики пропущенных спавн-проверок по типам за окно лога. */
    private static final Map<ResourceLocation, Integer> gonzotech$logCounts = new LinkedHashMap<>();

    /** Окно монстр-спавна (E−1 22000 → E 14500, Оверворлд): правилами считается пройдено. */
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private static <T extends net.minecraft.world.entity.Entity> void gonzotech$sunEventNightSpawnWindow(
            EntityType<T> type, ServerLevelAccessor level, EntitySpawnReason reason,
            BlockPos pos, RandomSource random, CallbackInfoReturnable<Boolean> cir) {
        if (reason != EntitySpawnReason.NATURAL || type.getCategory() != MobCategory.MONSTER) {
            return;
        }
        if (type == EntityType.SLIME) {
            return; // автор: слизней НЕ трогаем — ванильные болота/слайм-чанки
        }
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() != Level.OVERWORLD
            || serverLevel.getDifficulty() == Difficulty.PEACEFUL
            || !SunEventServer.monsterNightNow(serverLevel)) {
            return;
        }
        cir.setReturnValue(true);

        // Агрегатный лог: кого реально пускаем (раз в игровую минуту).
        gonzotech$logCounts.merge(BuiltInRegistries.ENTITY_TYPE.getKey(type), 1, Integer::sum);
        if (serverLevel.getGameTime() - gonzotech$lastLogReset >= 1200) {
            gonzotech$lastLogReset = serverLevel.getGameTime();
            LOGGER.info("[Gonzo Tech] Суневет: спавн-окно активно, пропущено за минуту: {} (время {})",
                gonzotech$logCounts, serverLevel.getDayTime() % 24000L);
            gonzotech$logCounts.clear();
        }
    }
}
