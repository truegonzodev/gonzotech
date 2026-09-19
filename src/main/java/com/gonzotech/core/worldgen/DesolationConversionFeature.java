package com.gonzotech.core.worldgen;

import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Дезоляция — пост-обработка чанка биома (автор, 2026-09-19): материальная
 * «выжженность» на ванильном рельефе.
 *
 * <p>Ходит ОДИН раз на чанк (шаг VEGETAL_DECORATION, первой в списке — после
 * всех руд, включая добавленные biome_modifier'ом из тега is_overworld, и
 * после всех водоёмов) по правилам:
 * <ul>
 *   <li>{@code water (source/flowing), y≥50} → {@code dead_slime_block}
 *       (затонувшие пруды/озёра; глубокие аквиферы не трогаем);</li>
 *   <li>{@code grass_block, dirt, coarse/rooted dirt} → {@code dead_dirt};</li>
 *   <li>{@code sand} → {@code dead_sand};</li>
 *   <li>{@code stone, andesite, granite} → {@code dead_stone} с градиентом:
 *       y≥60 всегда, в полосе [50;60) — вероятность (y−49)/11, глубже 50 —
 *       никогда. Диорит и глубинный сланец НЕ задеты (диорит — сознательно,
 *       автор: «включения диорита оставить» + наши кальцитовые жилы);</li>
 *   <li>Руды Gonzo: если у металла есть DEEPSLATE-хост — ЛЮБОЙ блок этой
 *       руды (host STONE/CALCITE) на всей глубине → его deepslate-вариант;
 *       если DEEPSLATE-хоста нет — блок руды → {@code dead_stone}
 *       (в этом биоме такой руды нет). Ванильные руды не трогаем.</li>
 * </ul>
 *
 * <p>Запись только в свой чанк (правило позиционное, соседи не нужны —
 * безопасная зона 3×3 не требуется).
 */
public class DesolationConversionFeature extends Feature<NoneFeatureConfiguration> {

    public DesolationConversionFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Лениво: блок руды любого host → куда конвертировать (deepslate-вариант или dead_stone). */
    private static Map<Block, Block> oreRemap;

    private static Map<Block, Block> oreRemap() {
        if (oreRemap == null) {
            Map<Block, Block> map = new HashMap<>();
            for (OreDefinition ore : OreDefinition.ALL) {
                Block deepslateVariant = ore.hosts().contains(OreDefinition.Host.DEEPSLATE)
                    ? ModBlocks.ORE_BLOCKS.get(ore.id()).get(OreDefinition.Host.DEEPSLATE).get()
                    : null;
                for (OreDefinition.Host host : ore.hosts()) {
                    if (host == OreDefinition.Host.DEEPSLATE || host == OreDefinition.Host.NETHER) {
                        continue; // deepslate остаётся собой; nether-хостов в оверворлде нет
                    }
                    Block from = ModBlocks.ORE_BLOCKS.get(ore.id()).get(host).get();
                    map.put(from, deepslateVariant != null ? deepslateVariant : ModBlocks.DEAD_STONE.get());
                }
            }
            oreRemap = map;
        }
        return oreRemap;
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        int chunkMinX = (origin.getX() >> 4) << 4;
        int chunkMinZ = (origin.getZ() >> 4) << 4;
        Map<Block, Block> remap = oreRemap();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = chunkMinX + dx;
                int z = chunkMinZ + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                int minY = level.getMinBuildHeight() + 1;
                for (int y = top; y >= minY; y--) {
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    Block block = state.getBlock();

                    // Руды — на ЛЮБОЙ глубине.
                    Block oreTarget = remap.get(block);
                    if (oreTarget != null) {
                        level.setBlock(pos, oreTarget.defaultBlockState(), 3);
                        changed = true;
                        continue;
                    }

                    // Материальная выжженность — только поверхностная полоса y≥50.
                    if (y < 50) {
                        continue;
                    }
                    if (state.getFluidState().getType() == Fluids.WATER
                        || state.getFluidState().getType() == Fluids.FLOWING_WATER) {
                        level.setBlock(pos, ModBlocks.DEAD_SLIME_BLOCK.get().defaultBlockState(), 3);
                        changed = true;
                    } else if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT
                        || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT) {
                        level.setBlock(pos, ModBlocks.DEAD_DIRT.get().defaultBlockState(), 3);
                        changed = true;
                    } else if (block == Blocks.SAND) {
                        level.setBlock(pos, ModBlocks.DEAD_SAND.get().defaultBlockState(), 3);
                        changed = true;
                    } else if (block == Blocks.STONE || block == Blocks.ANDESITE
                        || block == Blocks.GRANITE) {
                        if (y >= 60 || y >= 50 && random.nextInt(11) < y - 49) {
                            // 60+: всегда; 50–59: (y−49)/11 (0.09 … 0.91) — градиент спада.
                            level.setBlock(pos, ModBlocks.DEAD_STONE.get().defaultBlockState(), 3);
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }
}
