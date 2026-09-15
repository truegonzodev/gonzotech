package com.gonzotech.machines.nuclear;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Вероятностная тепловая опасность ядерных блоков.
 * <p>
 * Каждый игровой тик с {@code gameTime % 20 == 0} (раз в секунду) при GTH выше
 * порога машина бросает два независимых кубика на 3×3×3 объём вокруг себя:
 * <ul>
 *   <li>5% — на одном случайном горючем блоке разгорается огонь;</li>
 *   <li>3% — один случайный незащищённый блок плавится в расплавленный
 *       кориум (топка) или в лаву (вольфрамовый абсорбер).</li>
 * </ul>
 * Проверка самих порогов остаётся за вызывающим (BlockEntity машины).
 */
public final class ThermalHazards {

    /** Шанс возгорания одного блока в проценте за секунду. */
    public static final int IGNITION_CHANCE_PERCENT = 5;
    /** Шанс плавления одного блока в проценте за секунду. */
    public static final int MELT_CHANCE_PERCENT = 3;

    private ThermalHazards() {
    }

    /**
     * 5% в секунду: один случайный горюемый блок в 3×3×3 вокруг центра
     * загорается (огонь ставится поверх него, как от зажигалки).
     */
    public static void maybeIgniteAround(ServerLevel level, BlockPos center) {
        if (level.getGameTime() % 20L != 0L || level.random.nextInt(100) >= IGNITION_CHANCE_PERCENT) return;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos target : cubeAround(center)) {
            if (target.equals(center)) continue;
            if (!level.isLoaded(target)) continue;
            BlockState state = level.getBlockState(target);
            BlockPos fire = target.above();
            if (state.ignitedByLava() && level.getBlockState(fire).isAir()
                && BaseFireBlock.canBePlacedAt(level, fire, Direction.UP)) {
                candidates.add(target);
            }
        }
        if (candidates.isEmpty()) return;
        BlockPos base = candidates.get(level.random.nextInt(candidates.size()));
        level.setBlock(base.above(), BaseFireBlock.getState(level, base), Block.UPDATE_ALL);
    }

    /**
     * 3% в секунду: один случайный незащищённый блок в 3×3×3 вокруг центра
     * становится источником расплавленного кориума.
     *
     * @param includeCenter допускает плавление самого центрального блока
     *                      (топка становится кандидатом после непрерывного
     *                      перегрева — см. {@code NuclearDefs.NUCLEAR_FIREBOX_SELF_MELT_GRACE_TICKS})
     */
    public static void maybeMeltToCorium(ServerLevel level, BlockPos center, boolean includeCenter) {
        maybeMeltAround(level, center, ModBlocks.MOLTEN_CORIUM.get().defaultBlockState(), includeCenter);
    }

    /** 3% в секунду: то же, но в источник ванильной лавы (вольфрамовый абсорбер). */
    public static void maybeMeltToLava(ServerLevel level, BlockPos center) {
        maybeMeltAround(level, center, Blocks.LAVA.defaultBlockState(), false);
    }

    private static void maybeMeltAround(ServerLevel level, BlockPos center, BlockState replacement, boolean includeCenter) {
        if (level.getGameTime() % 20L != 0L || level.random.nextInt(100) >= MELT_CHANCE_PERCENT) return;
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos target : cubeAround(center)) {
            if (!includeCenter && target.equals(center)) continue;
            if (!level.isLoaded(target)) continue;
            BlockState state = level.getBlockState(target);
            // Пустоту плавить нечего: воздух, пещерный/пустотный воздух,
            // структурная пустота и всё из списка исключений.
            if (state.isAir() || isMeltProtected(state)) continue;
            candidates.add(target);
        }
        if (candidates.isEmpty()) return;
        level.setBlock(candidates.get(level.random.nextInt(candidates.size())), replacement, Block.UPDATE_ALL);
    }

    /** 27 позиций куба 3×3×3, центрированного на машине. */
    private static List<BlockPos> cubeAround(BlockPos center) {
        List<BlockPos> result = new ArrayList<>(27);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    result.add(center.offset(dx, dy, dz));
                }
            }
        }
        return result;
    }

    /**
     * Материалы, не плавящиеся в кориум/лаву: обсидиановые, служебные
     * блоки целостности мира, суперплотный лёд, вольфрамовый абсорбер,
     * долговечный бетон. (Сама ядерная топка сюда НЕ входит — она плавится
     * после непрерывного перегрева, см. {@code NuclearFireboxBlockEntity}.)
     */
    private static boolean isMeltProtected(BlockState state) {
        if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.CRYING_OBSIDIAN)
            || state.is(ModBlocks.CRIMSON_OBSIDIAN.get()) || state.is(Blocks.BEDROCK)
            || state.is(Blocks.BARRIER) || state.is(Blocks.COMMAND_BLOCK)
            || state.is(Blocks.CHAIN_COMMAND_BLOCK) || state.is(Blocks.REPEATING_COMMAND_BLOCK)
            || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID)
            || state.is(Blocks.JIGSAW) || state.is(Blocks.LIGHT)
            || state.is(Blocks.END_PORTAL) || state.is(Blocks.END_GATEWAY)
            || state.is(ModBlocks.SUPERDENSE_ICE.get())
            || state.is(ModBlocks.DURABLE_CONCRETE.get())
            || state.is(ModBlocks.TUNGSTEN_ABSORBER.get())) {
            return true;
        }
        // VR-20 и стеллит регистрируются через общую карту металлических блоков.
        return state.getBlock() == ModBlocks.METAL_BLOCKS.get("vr20_block").get()
            || state.getBlock() == ModBlocks.METAL_BLOCKS.get("stellite_block").get();
    }

    /** Касание перегретого блока поджигает игрока на четыре секунды. */
    public static void igniteTouchingPlayer(Entity entity) {
        if (entity instanceof Player && !entity.fireImmune()) {
            entity.igniteForSeconds(4.0F);
        }
    }
}
