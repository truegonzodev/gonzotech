package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Суневеты фаза 3 — «дождь = снег» (окно E−1..E+1, только Оверворлд).
 *
 * <p>Ванильная укладка снега — {@code ServerLevel.tickPrecipitation}: по
 * heightmap {@link Heightmap.Types#MOTION_BLOCKING} (только открытая поверхность,
 * не пещеры), слоями до 8, с гейт-правилом биомов {@link Biome#shouldSnow}
 * (снег «идёт» только в холодных биомах). В окне мы:
 * <ol>
 *   <li>открываем гейт — снег во ВСЕХ биомах (redirect shouldSnow);</li>
 *   <li>ПРАВИЛО АВТОРА: снег ложится только на землю из {@code #minecraft:solid}
 *       и только в ПУСТУЮ ячейку — ванильный setBlockAndUpdate бездумно «съел»
 *       бы факелы/цветки и положил снег поверх воды (HEAD-cancel таких точек);</li>
 *   <li>плотность укладки ×8 (1/48 → 1/6 на бросок);</li>
 *   <li>гроза ×10 (автор: старт ×10, только день E): молнии в 10 раз чаще,
 *       а «пауза до грома» в 10 раз короче.</li>
 * </ol>
 *
 * <p>Таяние не трогаем — ванильные снежные слои тают сами от света (факелы
 * защищают базу), температуры биомов не меняются.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSunEventSnowMixin {

    /** Ванил: 1/48 на бросок (игрок × чанк × тик) — редкий снег. */
    private static final int SNOW_DENSITY_VANILLA = 48;
    /** В окне: 1/6 — заметное снежное одеяло. */
    private static final int SNOW_DENSITY_EVENT = 6;
    /** Ванил: молния при грозе 1/100000 (чанк × тик). */
    private static final int BOLT_CHANCE_VANILLA = 100000;
    /** День E: 1/10000 — гроза ощутимо. */
    private static final int BOLT_CHANCE_EVENT = 10000;
    /** День E: «пауза до грома» /10 → гром стартует ×10 чаще. */
    private static final int THUNDER_DELAY_DIVISOR = 10;

    /**
     * Правило автора: снег только на {@code #minecraft:solid} и только в пустую
     * ячейку. Вне окна ванильное поведение не трогаем.
     */
    @Inject(method = "tickPrecipitation", at = @At("HEAD"))
    private void gonzotech$sunEventProtectGround(BlockPos pos, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!SunEventServer.snowWindowDay(level) || !level.isRaining()) {
            return;
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        BlockState surfaceState = level.getBlockState(surface);
        if (surfaceState.is(Blocks.SNOW)) {
            return; // на слое — обычное наращивание (ванил сделает)
        }
        boolean emptySpot = surfaceState.isAir();
        boolean solidGround = level.getBlockState(surface.below()).is(BlockTags.SOLID);
        if (!emptySpot || !solidGround) {
            // Факел/цветок/вода и т.п. — не трогаем, точка пропускается.
            ci.cancel();
        }
    }

    /** В окне снег идёт во всех биомах (ванил: только холодные). */
    @Redirect(method = "tickPrecipitation",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean gonzotech$sunEventSnowAllBiomes(Biome biome, Level level, BlockPos pos) {
        return biome.shouldSnow(level, pos) || SunEventServer.snowWindowDay(level);
    }

    /** В окне снег кладётся ×8 плотнее. */
    @ModifyConstant(method = "tickChunk", constant = @Constant(intValue = SNOW_DENSITY_VANILLA))
    private int gonzotech$sunEventSnowDensity(int original) {
        return SunEventServer.snowWindowDay((Level) (Object) this) ? SNOW_DENSITY_EVENT : original;
    }

    /** День E: молнии ×10 чаще (автор: гроза ×K, старт ×10). */
    @ModifyConstant(method = "tickChunk", constant = @Constant(intValue = BOLT_CHANCE_VANILLA))
    private int gonzotech$sunEventBolts(int original) {
        return SunEventServer.eventDayNow((ServerLevel) (Object) this) ? BOLT_CHANCE_EVENT : original;
    }

    /**
     * День E: «пауза до грома» в 10 раз короче (THUNDER_DELAY — 2-й вызов
     * IntProvider.sample в advanceWeatherCycle после THUNDER_DURATION).
     */
    @Redirect(method = "advanceWeatherCycle",
        at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/util/valueproviders/IntProvider;sample(Lnet/minecraft/util/RandomSource;)I"))
    private int gonzotech$sunEventThunderDelay(int original) {
        if (!SunEventServer.eventDayNow((ServerLevel) (Object) this)) {
            return original;
        }
        return Math.max(100, original / THUNDER_DELAY_DIVISOR);
    }
}
