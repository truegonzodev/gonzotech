package com.gonzotech.core.worldgen;

import com.gonzotech.core.ore.OreDefinition.Host;
import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Пост-обработка чанка при генерации мира: после того как ванильные фичи
 * создали кальцит (геоды), глину и капельник, для КАЖДОГО блока проверяется
 * шанс замены:
 * <ul>
 *   <li>кальцит  → каменная? нет — кальцитовая кальциевая руда (10%)</li>
 *   <li>глина   → каменная алюминиевая руда (3%)</li>
 *   <li>камень, касающийся гранью dripstone_block → каменная цинковая руда (1%)</li>
 * </ul>
 * Вызывается в biome modifier на шаге {@code top_layer_modification} — самом
 * позднем из стандартных шагов генерации, поэтому все источники (геоды,
 * глиняные острова/пруды, сталактиты капельника) уже на месте.
 * <p>
 * Заменяется именно блок, а не «кластер»: шанс применяется независимо к
 * каждому подходящему блоку чанка, как и описано в задании.
 */
public class MineralReplacementFeature extends Feature<NoneFeatureConfiguration> {

    /** Кальцит → кальцитовая кальциевая руда. */
    private static final float CALCITE_CHANCE = 0.10F;
    /** Глина → каменная алюминиевая руда. */
    private static final float CLAY_CHANCE = 0.03F;
    /** Камень у dripstone_block → каменная цинковая руда. */
    private static final float STONE_AT_DRIPSTONE_CHANCE = 0.01F;

    public MineralReplacementFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        ChunkPos chunkPos = new ChunkPos(context.origin());
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                // Кальцит/глина/камень живут не выше поверхности, поэтому сканируем
                // только до heightmap+1 — так не тратим ~90% итераций на воздух.
                int topY = Math.min(maxY, level.getHeight(Heightmap.Types.WORLD_SURFACE, chunkPos.getMinBlockX() + x, chunkPos.getMinBlockZ() + z) + 1);
                for (int y = minY; y < topY; y++) {
                    BlockPos pos = new BlockPos(chunkPos.getMinBlockX() + x, y, chunkPos.getMinBlockZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.CALCITE)) {
                        if (random.nextFloat() < CALCITE_CHANCE) {
                            level.setBlock(pos, calciteCalciumOre(), 2);
                        }
                    } else if (state.is(Blocks.CLAY)) {
                        if (random.nextFloat() < CLAY_CHANCE) {
                            level.setBlock(pos, stoneAluminumOre(), 2);
                        }
                    } else if (state.is(Blocks.STONE)) {
                        if (hasDripstoneNeighbor(level, pos) && random.nextFloat() < STONE_AT_DRIPSTONE_CHANCE) {
                            level.setBlock(pos, stoneZincOre(), 2);
                        }
                    }
                }
            }
        }
        return true;
    }

    private static boolean hasDripstoneNeighbor(WorldGenLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(Blocks.DRIPSTONE_BLOCK)) {
                return true;
            }
        }
        return false;
    }

    private static BlockState calciteCalciumOre() {
        return ModBlocks.ORE_BLOCKS.get("calcium").get(Host.CALCITE).get().defaultBlockState();
    }

    private static BlockState stoneAluminumOre() {
        return ModBlocks.ORE_BLOCKS.get("aluminum").get(Host.STONE).get().defaultBlockState();
    }

    private static BlockState stoneZincOre() {
        return ModBlocks.ORE_BLOCKS.get("zinc").get(Host.STONE).get().defaultBlockState();
    }
}