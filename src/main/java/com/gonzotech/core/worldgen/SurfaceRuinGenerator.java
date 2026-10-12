package com.gonzotech.core.worldgen;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Deterministic, chunk-scoped pieces of a buried surface wreck. */
public final class SurfaceRuinGenerator {

    /** 1 ruin per about 444 candidate chunks outside Desolation. */
    private static final double BASE_CHANCE = 1.0 / 444.0;
    private static final long SEED_SALT = 0x6A09E667F3BCC909L;
    private static final long DESOLATION_SALT = 0xBB67AE8584CAA73BL;
    private static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    private SurfaceRuinGenerator() {
    }

    /**
     * Evaluates nearby candidate chunks but writes only into {@code targetChunk}.
     * That makes the noisy structure seamless across chunk boundaries without
     * asking worldgen to mutate a neighbouring chunk out of order.
     */
    public static void generateForChunk(WorldGenLevel level, ChunkPos targetChunk) {
        for (int anchorX = targetChunk.x - 1; anchorX <= targetChunk.x + 1; anchorX++) {
            for (int anchorZ = targetChunk.z - 1; anchorZ <= targetChunk.z + 1; anchorZ++) {
                // WorldGenRegion only exposes chunks required by this generation step.
                if (level.hasChunk(anchorX, anchorZ)) {
                    generateCandidate(level, targetChunk, anchorX, anchorZ);
                }
            }
        }
    }

    private static void generateCandidate(WorldGenLevel level, ChunkPos targetChunk,
                                         int anchorChunkX, int anchorChunkZ) {
        long seed = candidateSeed(level.getSeed(), anchorChunkX, anchorChunkZ);
        RandomSource random = RandomSource.create(seed);
        int centerX = (anchorChunkX << 4) + random.nextInt(16);
        int centerZ = (anchorChunkZ << 4) + random.nextInt(16);
        // Consume the same roll as before, but reject impossible candidates before
        // heightmap/biome/block reads. This avoids nine terrain scans per chunk.
        double chanceRoll = random.nextDouble();
        if (chanceRoll >= BASE_CHANCE * 2.0) {
            return;
        }
        int centerY = DesolationWorldgenSupport.findGroundY(level, centerX, centerZ);
        if (centerY == DesolationWorldgenSupport.NO_SURFACE) {
            return;
        }

        BlockPos center = new BlockPos(centerX, centerY, centerZ);
        boolean desolation = level.getBiome(center).is(DESOLATION);
        double chance = BASE_CHANCE * (desolation ? 2.0 : 1.0);
        if (chanceRoll >= chance) {
            return;
        }

        double coreRadius = 1.5 + random.nextDouble() * 2.0;
        double debrisRadius = 6.0 + random.nextDouble() * 10.0;
        double plasticRadius = 5.0 + random.nextDouble() * 5.0;
        double siteRadius = Math.max(debrisRadius, plasticRadius);
        int scatterCount = 2 + random.nextInt(5);
        List<Scatter> scatter = createScatter(random, centerX, centerZ, debrisRadius, scatterCount);

        int minX = targetChunk.getMinBlockX();
        int maxX = minX + 16;
        int minZ = targetChunk.getMinBlockZ();
        int maxZ = minZ + 16;
        int searchRadius = (int) Math.ceil(siteRadius + 2.5);
        int fromX = Math.max(minX, centerX - searchRadius);
        int toX = Math.min(maxX - 1, centerX + searchRadius);
        int fromZ = Math.max(minZ, centerZ - searchRadius);
        int toZ = Math.min(maxZ - 1, centerZ + searchRadius);

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                int groundY = DesolationWorldgenSupport.findGroundY(level, x, z);
                if (groundY == DesolationWorldgenSupport.NO_SURFACE
                        || Math.abs(groundY - centerY) > 5) {
                    continue;
                }
                BlockPos groundPos = new BlockPos(x, groundY, z);
                if (!DesolationWorldgenSupport.isNaturalTerrain(level.getBlockState(groundPos))
                        || DesolationWorldgenSupport.hasTreeLogAbove(level, x, groundY, z)) {
                    continue;
                }

                double dx = x + 0.5 - (centerX + 0.5);
                double dz = z + 0.5 - (centerZ + 0.5);
                double distance = Math.sqrt(dx * dx + dz * dz);
                double boundaryNoise = DesolationWorldgenSupport.noise(seed ^ 0x3C6EF372FE94F82AL,
                    x, z, 3.2) * 1.9;
                double localDebrisRadius = clamp(debrisRadius + boundaryNoise, 6.0, 16.0);
                double plasticNoise = DesolationWorldgenSupport.noise(seed ^ 0x510E527FADE682D1L,
                    x, z, 2.8) * 0.45;
                double localPlasticRadius = clamp(plasticRadius + plasticNoise, 5.0, 10.0);
                boolean insideDebris = distance <= localDebrisRadius;
                boolean insidePlastic = distance <= localPlasticRadius;
                if (!insideDebris && !insidePlastic) {
                    continue;
                }

                double edgeFade = insideDebris
                    ? clamp((localDebrisRadius - distance) / 2.5, 0.0, 1.0)
                    : 0.0;
                double centerDensity = 0.54 + 0.28 * (1.0 - Math.min(1.0, distance / debrisRadius));
                double debrisCoverage = edgeFade * centerDensity;
                boolean debrisCovered = insideDebris
                    && (distance <= coreRadius
                        || DesolationWorldgenSupport.hash01(seed ^ 0xA54FF53A5F1D36F1L, x, z)
                            < debrisCoverage);
                double plasticDensity = 0.78 - 0.48 * Math.min(1.0, distance / plasticRadius);
                double plasticFade = clamp((localPlasticRadius - distance) / 1.8, 0.0, 1.0);
                boolean plasticCovered = insidePlastic
                    && DesolationWorldgenSupport.hash01(seed ^ 0x1F83D9ABFB41BD6BL, x, z)
                        < plasticDensity * plasticFade;

                if (distance <= coreRadius && debrisCovered) {
                    makeWreckCore(level, seed, x, z, groundY, distance, coreRadius);
                    continue;
                }
                BlockState material;
                if (plasticCovered) {
                    material = ModBlocks.PLASTIC_WASTE.get().defaultBlockState();
                } else if (debrisCovered) {
                    material = pickSurfaceMaterial(seed, x, z);
                } else {
                    continue;
                }
                DesolationWorldgenSupport.setSurfaceBlock(level, groundPos, material);

                // Broken hull panels and debris rise irregularly around the wreck's core.
                boolean nearCoreEdge = Math.abs(distance - coreRadius) <= 2.2;
                double protrusionChance = nearCoreEdge ? 0.34 : 0.035;
                if (debrisCovered
                        && DesolationWorldgenSupport.hash01(seed ^ 0x5BE0CD19137E2179L, x, z)
                            < protrusionChance * edgeFade) {
                    int height = 1 + (DesolationWorldgenSupport.hash01(seed ^ 0xCBBB9D5DC1059ED8L,
                        x, z) < 0.22 ? 1 : 0);
                    for (int dy = 1; dy <= height; dy++) {
                        BlockPos above = groundPos.above(dy);
                        if (above.getY() >= level.getMaxY()
                                || !DesolationWorldgenSupport.canPlaceOnTopOfTerrain(level.getBlockState(above))) {
                            break;
                        }
                        level.setBlock(above, pickSurfaceMaterial(seed ^ dy, x, z), 2);
                    }
                }
            }
        }

        placeScatter(level, targetChunk, seed, scatter);
    }

    private static void makeWreckCore(WorldGenLevel level, long seed, int x, int z,
                                      int groundY, double distance, double coreRadius) {
        // Keep the core mostly at the natural surface; only scattered inner
        // columns collapse by one block, avoiding a continuous crater silhouette.
        int depth = distance <= coreRadius * 0.72
                && DesolationWorldgenSupport.hash01(seed ^ 0xC6E00BF33DA88FC2L, x, z) < 0.32
            ? 1 : 0;
        int floorY = groundY - depth;
        if (floorY <= level.getMinY()) {
            return;
        }

        BlockPos floor = new BlockPos(x, floorY, z);
        if (floorY - 1 < level.getMinY()
                || !DesolationWorldgenSupport.isNaturalTerrain(level.getBlockState(floor))
                || !DesolationWorldgenSupport.isNaturalTerrain(
                    level.getBlockState(new BlockPos(x, floorY - 1, z)))) {
            return;
        }
        // Never open an existing cave or aquifer while shaping a collapsed pocket.
        for (int y = floorY; y <= groundY; y++) {
            if (!DesolationWorldgenSupport.isNaturalTerrain(level.getBlockState(new BlockPos(x, y, z)))) {
                return;
            }
        }

        int clearTop = Math.min(groundY + 2, level.getMaxY() - 1);
        for (int y = groundY + 1; y <= clearTop; y++) {
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.is(net.minecraft.tags.BlockTags.LOGS)
                    || (!state.isAir()
                        && !state.canBeReplaced()
                        && !state.is(net.minecraft.tags.BlockTags.LEAVES)
                        && !state.is(net.minecraft.tags.BlockTags.SNOW))) {
                return;
            }
        }

        int markerY = floorY - depth - 1;
        if (depth > 0 && (markerY < level.getMinY()
                || !DesolationWorldgenSupport.isNaturalTerrain(level.getBlockState(new BlockPos(x, markerY, z))))) {
            return;
        }
        for (int y = floorY + 1; y <= clearTop; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.getBlockState(pos).isAir()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }

        // Weathered machinery/plastic form a broken surface mound; only the
        // hashed pockets sit below the original terrain line.
        double coreRoll = DesolationWorldgenSupport.hash01(seed ^ 0x428A2F98D728AE22L, x, z);
        BlockState core = coreRoll < 0.58
            ? ModBlocks.WEATHERED_MECHANISMS.get().defaultBlockState()
            : ModBlocks.PLASTIC_WASTE.get().defaultBlockState();
        level.setBlock(floor, core, 2);

        // A shallow-pocket marker lets neighboring chunks recover the original
        // terrain height even though this column's visible core sits one block lower.
        if (depth > 0) {
            level.setBlock(new BlockPos(x, markerY, z),
                ModBlocks.SILICON_CACHE.get().defaultBlockState(), 2);
        }
        int cacheY = groundY - 8;
        if (distance <= coreRadius * 0.82
                && DesolationWorldgenSupport.hash01(seed ^ 0x7137449123EF65CDL, x, z) < 0.28
                && cacheY >= level.getMinY()
                && DesolationWorldgenSupport.isNaturalTerrain(level.getBlockState(new BlockPos(x, cacheY, z)))) {
            level.setBlock(new BlockPos(x, cacheY, z), ModBlocks.SILICON_CACHE.get().defaultBlockState(), 2);
        }

        // Surface fragments fill some of the shallow pockets and break up the core.
        BlockPos inner = floor.above();
        if (inner.getY() < level.getMaxY()
                && distance <= coreRadius * 0.72
                && DesolationWorldgenSupport.hash01(seed ^ 0xB5C0FBCFEC4D3B2FL, x, z) < 0.42
                && DesolationWorldgenSupport.canPlaceOnTopOfTerrain(level.getBlockState(inner))) {
            BlockState coreFragment = DesolationWorldgenSupport.hash01(seed ^ 0xE9B5DBA58189DBBCL,
                x, z) < 0.58
                ? ModBlocks.WEATHERED_MECHANISMS.get().defaultBlockState()
                : ModBlocks.PLASTIC_WASTE.get().defaultBlockState();
            level.setBlock(inner, coreFragment, 2);
        }
    }

    private static BlockState pickSurfaceMaterial(long seed, int x, int z) {
        // Coarse interpolated noise groups the six weighted materials into veins;
        // a little per-column variation keeps the patches rough rather than tiled.
        double coarse = (DesolationWorldgenSupport.noise(seed ^ 0x243F6A8885A308D3L,
            x, z, 2.6) + 1.0) * 0.5;
        double fine = DesolationWorldgenSupport.hash01(seed ^ 0x13198A2E03707344L, x, z);
        double roll = clamp(coarse * 0.78 + fine * 0.22, 0.0, 1.0);

        Block block;
        if (roll < 0.05) {
            block = ModBlocks.MECHANISMS.get();
        } else if (roll < 0.20) {
            block = ModBlocks.WEATHERED_PLATING.get();
        } else if (roll < 0.40) {
            block = ModBlocks.DEBRIS.get();
        } else if (roll < 0.60) {
            block = ModBlocks.WEATHERED_DEBRIS.get();
        } else if (roll < 0.80) {
            block = ModBlocks.WEATHERED_MECHANISMS.get();
        } else {
            block = ModBlocks.PLASTIC_WASTE.get();
        }
        return block.defaultBlockState();
    }

    private static List<Scatter> createScatter(RandomSource random, int centerX, int centerZ,
                                                double radius, int count) {
        List<Scatter> pieces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = Math.max(5.0, radius * (0.64 + random.nextDouble() * 0.34));
            int x = centerX + (int) Math.round(Math.cos(angle) * distance);
            int z = centerZ + (int) Math.round(Math.sin(angle) * distance);
            int height = random.nextInt(3) == 0 ? 2 : 1;
            pieces.add(new Scatter(x, z, height, i));
        }
        return pieces;
    }

    private static void placeScatter(WorldGenLevel level, ChunkPos targetChunk, long seed,
                                     List<Scatter> scatter) {
        int minX = targetChunk.getMinBlockX();
        int maxX = minX + 16;
        int minZ = targetChunk.getMinBlockZ();
        int maxZ = minZ + 16;
        for (Scatter piece : scatter) {
            if (piece.x() < minX || piece.x() >= maxX || piece.z() < minZ || piece.z() >= maxZ) {
                continue;
            }
            int groundY = DesolationWorldgenSupport.findGroundY(level, piece.x(), piece.z());
            if (groundY == DesolationWorldgenSupport.NO_SURFACE) {
                continue;
            }
            BlockPos ground = new BlockPos(piece.x(), groundY, piece.z());
            if (!DesolationWorldgenSupport.isNaturalTerrain(level.getBlockState(ground))
                    || DesolationWorldgenSupport.hasTreeLogAbove(level, piece.x(), groundY, piece.z())) {
                continue;
            }
            BlockState base = pickSurfaceMaterial(seed ^ (0x9E3779B97F4A7C15L * (piece.index() + 1L)),
                piece.x(), piece.z());
            DesolationWorldgenSupport.setSurfaceBlock(level, ground, base);
            for (int dy = 1; dy < piece.height(); dy++) {
                BlockPos above = ground.above(dy);
                if (above.getY() >= level.getMaxY()
                        || !DesolationWorldgenSupport.canPlaceOnTopOfTerrain(level.getBlockState(above))) {
                    break;
                }
                level.setBlock(above, base, 2);
            }
        }
    }

    private static long candidateSeed(long worldSeed, int chunkX, int chunkZ) {
        long value = worldSeed ^ SEED_SALT
            ^ ((long) chunkX * 0x9E3779B97F4A7C15L)
            ^ ((long) chunkZ * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31) ^ DESOLATION_SALT;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Scatter(int x, int z, int height, int index) {
    }
}
