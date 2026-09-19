package com.gonzotech.core.worldgen;

import com.gonzotech.core.registry.ModBlocks;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Мёртвые деревья дезоляции (автор, 2026-09-19): голые стволы {@code dead_log}
 * без кроны и листвы вообще, 4–9 блоков высотой, строго вертикально (AXIS=y).
 * Ставится на поверхностный грунт биома (наши мёртвые почвы; ванильные —
 * страховка на случай смены порядка генерации). Число попыток на чанк задаёт
 * placed_feature (реже лесных деревьев Forest).
 */
public class DeadTrunkFeature extends Feature<NoneFeatureConfiguration> {

    public DeadTrunkFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();

        // Гарантированный триггер материальной конверсии чанка биома
        // (placed-фича конверсии недетерминированно молчит; стволы — точно исполняются).
        DesolationConversionFeature.convertChunk(level, new net.minecraft.world.level.ChunkPos(origin));

        int groundX = origin.getX();
        int groundZ = origin.getZ();
        int groundY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, groundX, groundZ) - 1;
        BlockPos ground = new BlockPos(groundX, groundY, groundZ);

        Block soil = level.getBlockState(ground).getBlock();
        if (soil != ModBlocks.DEAD_DIRT.get() && soil != ModBlocks.DEAD_SAND.get()
            && soil != ModBlocks.DEAD_STONE.get()
            && soil != Blocks.GRASS_BLOCK && soil != Blocks.DIRT && soil != Blocks.SAND
            && soil != Blocks.STONE && soil != Blocks.ANDESITE && soil != Blocks.GRANITE) {
            return false;
        }

        int height = 4 + random.nextInt(6); // 4..9
        BlockPos.MutableBlockPos trunk = ground.mutable();
        for (int dy = 1; dy <= height; dy++) {
            trunk.setY(groundY + dy);
            if (trunk.getY() >= level.getMaxY()) {
                break;
            }
            if (!level.getBlockState(trunk).isAir()) {
                break; // уперлось в другой объект — ствол обрезается естественно
            }
            level.setBlock(trunk, ModBlocks.DEAD_LOG.get().defaultBlockState(), 3);
        }
        return true;
    }
}
