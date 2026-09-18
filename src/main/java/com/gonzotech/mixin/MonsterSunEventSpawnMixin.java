package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Суневеты фаза 4 (монстры) — спавн в багровый день E.
 *
 * <p>Автор: в багровый день монстры <b>спавнятся днём как в темноте</b>
 * (Оверворлд, только день E). Цепочка 1.21.4 (сверено по декомпилу):
 * {@code NaturalSpawner.spawnCategoryForPosition → isValidSpawnPostitionForType →
 * SpawnPlacements.checkSpawnRules → регистрант Monster::checkMonsterSpawnRules
 * (Z/S/C/крипер/эндермен/ведьма) → isDarkEnoughToSpawn}.
 * {@code ignoresLightRequirements(NATURAL)} = false → туда мы и вклиниваемся
 * HEAD→true. В обычные дни и багровую ночь — ваниль (инъекция молчит).
 *
 * <p>Игрока не трогаем. Горение — отдельно: {@code MobSunEventBurnMixin}.
 * Диагностика: троттл-лог (раз в 20 с игрового времени) при срабатывании
 * в день E + дебаг-команда {code /gonzotech debug sunevent spawntest}.
 */
@Mixin(Monster.class)
public abstract class MonsterSunEventSpawnMixin {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Троттл диагностического лога (уникальный gameTime последней строки). */
    private static long gonzotech$lastLog = -1L;

    /** День E, Оверворлд: света-гейт считается всегда пройденным («как в темноте»). */
    @Inject(method = "isDarkEnoughToSpawn", at = @At("HEAD"), cancellable = true)
    private static void gonzotech$sunEventDaylightSpawn(ServerLevelAccessor level, BlockPos pos,
                                                        RandomSource random,
                                                        CallbackInfoReturnable<Boolean> cir) {
        ServerLevel serverLevel = level.getLevel();
        if (serverLevel.dimension() == Level.OVERWORLD && SunEventServer.eventDayNow(serverLevel)) {
            cir.setReturnValue(true);
            if (serverLevel.getGameTime() - gonzotech$lastLog >= 400) {
                gonzotech$lastLog = serverLevel.getGameTime();
                LOGGER.info("[Gonzo Tech] Суневет: isDarkEnoughToSpawn → true (день E) @ {} time={}",
                    pos, serverLevel.getDayTime() % 24000L);
            }
        }
    }
}
