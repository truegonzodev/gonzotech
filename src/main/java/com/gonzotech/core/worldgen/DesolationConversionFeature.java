package com.gonzotech.core.worldgen;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
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
 *   <li>Руды (y≥50, ЛЮБЫЕ каменные — и ванильные, и Gonzo) — автор, второе
 *       задание: «на высоте 50+ любая каменная руда заменяется на»:
 *       если есть deepslate-вариант → на него (deepslate_iron, copper…);
 *       если сланцевого варианта НЕТ (напр., алюминий) → {@code dead_stone}.
 *       Ниже y=50 руды не трогаем совсем.</li>
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
            // Ванильные каменные руды — у всех есть deepslate-вариант.
            map.put(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE);
            map.put(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE);
            map.put(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);
            map.put(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE);
            map.put(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE);
            map.put(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE);
            map.put(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE);
            map.put(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE);
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

    /** Чанки, где конверсия уже прошла (идемпотентный триггер из стволов/плейсмодифаера). */
    private static final java.util.Set<Long> doneChunks = new java.util.HashSet<>();

    /** Наш биом — конверсия идёт только в колонках, где биом — он сам. */
    private static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    /**
     * Материальная конверсия чанка биома. Вызывается и из placed-фичи, и из
     * DeadTrunkFeature (гарантированная точка исполнения, count=4 подряд) —
     * дедупликация по ChunkPos: повтор в чанке — мгновенный no-op.
     *
     * <p>ВНИМАНИЕ: конвертируются ТОЛЬКО колонки, чей биом = desolation
     * (проверка по WG-хайтмапу поверхности). Граница получает органическую
     * шумовую форму биома (кварт-ячейка ~4 блока), а не квадрат чанка 16×16.
     */
    public static void convertChunk(WorldGenLevel level, ChunkPos chunkPos) {
        if (!doneChunks.add(chunkPos.toLong())) {
            return; // чанк уже обработан (стволы x4 + возможный placed-вызов)
        }
        RandomSource random = level.getRandom();
        int minY = level.getMinY();
        int maxY = level.getMaxY();
        Map<Block, Block> remap = oreRemap();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getMinBlockX() + x;
                int worldZ = chunkPos.getMinBlockZ() + z;
                // Органические границы: конвертируем колонку, только если её биом — наш.
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, worldX, worldZ) - 1;
                if (!level.getBiome(new BlockPos(worldX, surfaceY, worldZ)).is(DESOLATION)) {
                    continue;
                }
                // Сканируем всю колонку сверху вниз.
                for (int y = maxY - 1; y >= minY; y--) {
                    BlockPos pos = new BlockPos(worldX, y, worldZ);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    Block block = state.getBlock();

                    // Материальная выжженность — только поверхностная полоса y≥50
                    // (руды — там же: автор, второй заход).
                    if (y < 50) {
                        break; // дальше по оси y столбцы только глубже — выходим из колонки
                    }

                    // Руды: есть deepslate-вариант → он; нет (алюминий и пр.) → dead_stone.
                    Block oreTarget = remap.get(block);
                    if (oreTarget != null) {
                        level.setBlock(pos, oreTarget.defaultBlockState(), 2);
                        continue;
                    }

                    if (state.getFluidState().getType() == Fluids.WATER
                        || state.getFluidState().getType() == Fluids.FLOWING_WATER) {
                        level.setBlock(pos, ModBlocks.DEAD_SLIME_BLOCK.get().defaultBlockState(), 2);
                    } else if (block == Blocks.GRASS_BLOCK || block == Blocks.DIRT
                        || block == Blocks.COARSE_DIRT || block == Blocks.ROOTED_DIRT) {
                        level.setBlock(pos, ModBlocks.DEAD_DIRT.get().defaultBlockState(), 2);
                    } else if (block == Blocks.SAND) {
                        level.setBlock(pos, ModBlocks.DEAD_SAND.get().defaultBlockState(), 2);
                    } else if (block == Blocks.STONE || block == Blocks.ANDESITE
                        || block == Blocks.GRANITE) {
                        if (y >= 60 || y >= 50 && random.nextInt(11) < y - 49) {
                            // 60+: всегда; 50–59: (y−49)/11 (0.09 … 0.91) — градиент спада.
                            level.setBlock(pos, ModBlocks.DEAD_STONE.get().defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        convertChunk(context.level(), new ChunkPos(context.origin()));
        return true;
    }
}
