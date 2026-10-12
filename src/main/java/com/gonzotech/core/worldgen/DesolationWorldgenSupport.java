package com.gonzotech.core.worldgen;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Surface/noise helpers shared by the procedural ruin and waste features. */
final class DesolationWorldgenSupport {

    static final int NO_SURFACE = Integer.MIN_VALUE;

    private DesolationWorldgenSupport() {
    }

    /**
     * Returns the uppermost natural terrain block at a dry surface position.
     * Submerged columns and ocean/river biomes are rejected so callers cannot
     * accidentally turn cave floors or ocean beds into feature sites.
     */
    static int findGroundY(WorldGenLevel level, int x, int z) {
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        if (topY <= level.getMinY() || topY >= level.getMaxY()) {
            return NO_SURFACE;
        }

        BlockPos topPos = new BlockPos(x, topY, z);
        BlockState topState = level.getBlockState(topPos);
        if (!topState.getFluidState().isEmpty()) {
            return NO_SURFACE;
        }
        if (isIce(topState)) {
            int iceFloor = Math.max(level.getMinY(), topY - 8);
            for (int y = topY - 1; y >= iceFloor; y--) {
                BlockState below = level.getBlockState(new BlockPos(x, y, z));
                if (!below.getFluidState().isEmpty()) {
                    return NO_SURFACE;
                }
                if (isNaturalTerrain(below)) {
                    break;
                }
            }
        }

        int searchFloor = Math.max(level.getMinY(), topY - 24);
        for (int y = topY; y >= searchFloor; y--) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (!state.getFluidState().isEmpty()) {
                return NO_SURFACE;
            }
            if (isSurfaceDecoration(state)) {
                int baseY = y;
                while (baseY > searchFloor
                        && isSurfaceDecoration(level.getBlockState(new BlockPos(x, baseY - 1, z)))) {
                    baseY--;
                }
                if (isNaturalTerrain(level.getBlockState(new BlockPos(x, baseY - 1, z)))) {
                    int bowlDepth = bowlDepthFromMarker(level, x, baseY, z);
                    int surfaceY = bowlDepth > 0 ? baseY + bowlDepth : baseY;
                    return isDrySurfaceBiome(level, x, surfaceY, z) ? surfaceY : NO_SURFACE;
                }
            }
            if (isNaturalTerrain(state)) {
                return isDrySurfaceBiome(level, x, y, z) ? y : NO_SURFACE;
            }
            if (isSurfaceCover(state)) {
                continue;
            }
            // A building, cave wall, or other non-terrain obstruction: don't
            // bury a procedural site inside/above it.
            return NO_SURFACE;
        }
        return NO_SURFACE;
    }

    private static boolean isDrySurfaceBiome(WorldGenLevel level, int x, int y, int z) {
        BlockPos surface = new BlockPos(x, y, z);
        return !level.getBiome(surface).is(BiomeTags.IS_OCEAN)
            && !level.getBiome(surface).is(BiomeTags.IS_RIVER);
    }

    static boolean isNaturalTerrain(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(BlockTags.DIRT)
            || state.is(BlockTags.SAND)
            || state.is(BlockTags.TERRACOTTA)
            || state.is(BlockTags.BASE_STONE_OVERWORLD)
            || state.is(BlockTags.BASE_STONE_NETHER)
            || state.is(BlockTags.SNOW)
            || state.is(Blocks.GRAVEL)
            || state.is(Blocks.END_STONE)
            || state.is(Blocks.NETHERRACK)
            || state.is(Blocks.SOUL_SAND)
            || state.is(Blocks.SOUL_SOIL)
            || state.is(Blocks.BASALT)
            || state.is(Blocks.BLACKSTONE)
            || state.is(Blocks.CALCITE)
            || state.is(Blocks.DRIPSTONE_BLOCK)
            || state.is(Blocks.PACKED_ICE)
            || state.is(Blocks.BLUE_ICE)
            || state.is(Blocks.ICE)
            || state.is(Blocks.MAGMA_BLOCK)
            || state.is(Blocks.MYCELIUM)
            || state.is(Blocks.PODZOL)
            || state.is(Blocks.MOSS_BLOCK)
            || state.is(Blocks.MUD)
            || isModTerrain(state);
    }

    private static boolean isModTerrain(BlockState state) {
        return state.is(ModBlocks.DEAD_DIRT.get())
            || state.is(ModBlocks.DEAD_SAND.get())
            || state.is(ModBlocks.DEAD_STONE.get())
            || state.is(ModBlocks.LUNAR_DIRT.get())
            || state.is(ModBlocks.LUNAR_STONE.get())
            || state.is(ModBlocks.RICH_LUNAR_STONE.get())
            || state.is(ModBlocks.LUNAR_SAND.get())
            || state.is(ModBlocks.MARTIAN_STONE.get())
            || state.is(ModBlocks.RICH_MARTIAN_STONE.get())
            || state.is(ModBlocks.MARTIAN_DIRT.get())
            || state.is(ModBlocks.SUPERDENSE_ICE.get())
            || state.is(ModBlocks.EUROPAN_ICE.get())
            || state.is(ModBlocks.METEOR.get());
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE)
            || state.is(Blocks.PACKED_ICE)
            || state.is(Blocks.BLUE_ICE)
            || state.is(Blocks.FROSTED_ICE);
    }

    private static boolean isSurfaceDecoration(BlockState state) {
        return state.is(ModBlocks.SILICON_CACHE.get())
            || state.is(ModBlocks.WEATHERED_PLATING.get())
            || state.is(ModBlocks.DEBRIS.get())
            || state.is(ModBlocks.WEATHERED_DEBRIS.get())
            || state.is(ModBlocks.MECHANISMS.get())
            || state.is(ModBlocks.WEATHERED_MECHANISMS.get())
            || state.is(ModBlocks.PLASTIC_WASTE.get());
    }

    private static int bowlDepthFromMarker(WorldGenLevel level, int x, int baseY, int z) {
        BlockState core = level.getBlockState(new BlockPos(x, baseY, z));
        if (!core.is(ModBlocks.WEATHERED_MECHANISMS.get())
                && !core.is(ModBlocks.PLASTIC_WASTE.get())) {
            return 0;
        }
        for (int depth = 1; depth <= 3; depth++) {
            int markerY = baseY - depth - 1;
            if (markerY >= level.getMinY()
                    && level.getBlockState(new BlockPos(x, markerY, z))
                        .is(ModBlocks.SILICON_CACHE.get())) {
                return depth;
            }
        }
        return 0;
    }

    private static boolean isSurfaceCover(BlockState state) {
        return state.isAir()
            || state.canBeReplaced()
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.LOGS)
            || state.is(BlockTags.SNOW)
            || state.is(ModBlocks.WASTE_BARREL.get());
    }

    static boolean isSurfaceSupport(BlockState state) {
        return isNaturalTerrain(state) || isSurfaceDecoration(state);
    }

    static boolean canPlaceOnTopOfTerrain(BlockState state) {
        return state.isAir()
            || state.canBeReplaced()
            || state.is(BlockTags.LEAVES)
            || state.is(BlockTags.SNOW);
    }

    static boolean canPlaceBarrelAt(BlockState state) {
        return canPlaceOnTopOfTerrain(state)
            || state.is(ModBlocks.WASTE_BARREL.get())
            || isSurfaceDecoration(state);
    }

    /**
     * Removes replaceable surface plants and similar cover before a site block
     * replaces the terrain beneath it. A short bounded scan clears both halves
     * of tall grass/flowers without cutting through logs or other solid blocks.
     */
    static void clearSurfaceVegetation(WorldGenLevel level, int x, int groundY, int z) {
        int clearToY = Math.min(groundY + 3, level.getMaxY() - 1);
        for (int y = groundY + 1; y <= clearToY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty() || !state.canBeReplaced()) {
                break;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    static void setSurfaceBlock(WorldGenLevel level, BlockPos groundPos, BlockState replacement) {
        clearSurfaceVegetation(level, groundPos.getX(), groundPos.getY(), groundPos.getZ());
        level.setBlock(groundPos, replacement, 2);
    }

    static boolean hasTreeLogAbove(WorldGenLevel level, int x, int groundY, int z) {
        int top = Math.min(groundY + 4, level.getMaxY() - 1);
        for (int y = groundY + 1; y <= top; y++) {
            if (level.getBlockState(new BlockPos(x, y, z)).is(BlockTags.LOGS)) {
                return true;
            }
        }
        return false;
    }

    /** Smooth interpolated value noise in [-1, 1], with a cell size in blocks. */
    static double noise(long seed, double x, double z, double cellSize) {
        double gx = x / cellSize;
        double gz = z / cellSize;
        int x0 = (int) Math.floor(gx);
        int z0 = (int) Math.floor(gz);
        double tx = smooth(gx - x0);
        double tz = smooth(gz - z0);

        double a = lerp(hashSigned(seed, x0, z0), hashSigned(seed, x0 + 1, z0), tx);
        double b = lerp(hashSigned(seed, x0, z0 + 1), hashSigned(seed, x0 + 1, z0 + 1), tx);
        return lerp(a, b, tz);
    }

    /** Stable [0,1) value shared by blocks in a lattice cell. */
    static double hash01(long seed, int x, int z) {
        long value = seed
            ^ ((long) x * 0x9E3779B97F4A7C15L)
            ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (value >>> 11) * 0x1.0p-53;
    }

    private static double hashSigned(long seed, int x, int z) {
        return hash01(seed, x, z) * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
