package com.gonzotech.mixin;

import com.gonzotech.sunevent.SunEventServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
 *   <li>снег укладывается в пустую ячейку (либо наращивает снежный слой) только
 *       над полным блоком и не над жидкостью; неполные формы (полублоки,
 *       дорожки, ступени, панели и другие частичные блоки) не подходят.</li>
 *   <li>плотность укладки — ВАНИЛЬНАЯ (1/48 на бросок): автор прогнал ×8 →
 *       ×2 → ×1, вернули ванильные снежные шапки («ниче нового»).</li>
 *   <li>гроза ×5 (автор: старт ×10 → смягчено до ×5, только день E): молнии
 *       в 5 раз чаще, а «пауза до грома» в 5 раз короче.</li>
 * </ol>
 *
 * <p>Таяние не трогаем — ванильные снежные слои тают сами от света (факелы
 * защищают базу), температуры биомов не меняются.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSunEventSnowMixin {

    /** Укладка снега в окне идёт с ВАНИЛЬНОЙ плотностью 1/48 (автор: ×1, «ванильные шапки»). */
    /** Ванил: молния при грозе 1/100000 (чанк × тик). */
    private static final int BOLT_CHANCE_VANILLA = 100000;
    /** День E: 1/20000 — гроза ощутимо (автор: ×5). */
    private static final int BOLT_CHANCE_EVENT = 20000;
    /** День E: «пауза до грома» /5 → гром стартует ×5 чаще (автор: ×5). */
    private static final int THUNDER_DELAY_DIVISOR = 5;

    /**
     * В окне снег укладывается только в пустую ячейку либо наращивает уже
     * существующий снежный слой. В обоих случаях опора под ячейкой должна иметь
     * полную коллизионную форму, а жидкость под ней исключает укладку.
     * Вне окна ванильное поведение не меняется.
     */
    @Inject(method = "tickPrecipitation", at = @At("HEAD"), cancellable = true)
    private void gonzotech$sunEventProtectGround(BlockPos pos, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!SunEventServer.snowWindowDay(level) || !level.isRaining()) {
            return;
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
        BlockState surfaceState = level.getBlockState(surface);
        if (!surfaceState.isAir() && !surfaceState.is(Blocks.SNOW)) {
            // Занятая ячейка (факел, цветок и т. п.) не заменяется снегом.
            ci.cancel();
            return;
        }

        BlockPos supportPos = surface.below();
        BlockState supportState = level.getBlockState(supportPos);
        if (!supportState.getFluidState().isEmpty()) {
            // MOTION_BLOCKING включает жидкости, но снег на жидкость не укладывается.
            ci.cancel();
            return;
        }
        if (!supportState.isCollisionShapeFullBlock(level, supportPos)) {
            // Проверка формы, а не списка исключений: полублоки, ступени,
            // дорожки, панели и другие неполные блоки не подходят как опора.
            ci.cancel();
        }
    }

    /** В окне снег идёт во всех биомах (ванил: только холодные). */
    @Redirect(method = "tickPrecipitation",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean gonzotech$sunEventSnowAllBiomes(Biome biome, LevelReader level, BlockPos pos) {
        return biome.shouldSnow(level, pos) || SunEventServer.snowWindowDay((Level) level);
    }

    /** День E: молнии ×5 чаще (автор: гроза ×K — смягчено до ×5). Только Оверворлд. */
    @ModifyConstant(method = "tickChunk", constant = @Constant(intValue = BOLT_CHANCE_VANILLA))
    private int gonzotech$sunEventBolts(int original) {
        ServerLevel level = (ServerLevel) (Object) this;
        return level.dimension() == Level.OVERWORLD
            && SunEventServer.eventDayNow(level) ? BOLT_CHANCE_EVENT : original;
    }

    /**
     * День E: «пауза до грома» в 5 раз короче (THUNDER_DELAY — 2-й вызов
     * IntProvider.sample в advanceWeatherCycle после THUNDER_DURATION).
     * Сигнатура @Redirect на INVOKE = (receiver, аргументы) — без «original».
     */
    @Redirect(method = "advanceWeatherCycle",
        at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/util/valueproviders/IntProvider;sample(Lnet/minecraft/util/RandomSource;)I"))
    private int gonzotech$sunEventThunderDelay(IntProvider provider, RandomSource random) {
        int original = provider.sample(random);
        ServerLevel level = (ServerLevel) (Object) this;
        if (level.dimension() != Level.OVERWORLD || !SunEventServer.eventDayNow(level)) {
            return original;
        }
        return Math.max(100, original / THUNDER_DELAY_DIVISOR);
    }
}
