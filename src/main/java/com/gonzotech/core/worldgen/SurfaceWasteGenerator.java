package com.gonzotech.core.worldgen;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.registry.ModBlocks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

/** Deterministic surface waste sites; all mutations stay inside the active chunk. */
public final class SurfaceWasteGenerator {

    /** A little more frequent than ruins: about three candidate sites per 1024 chunks. */
    private static final double BASE_CHANCE = 3.0 / 1024.0;
    private static final long SEED_SALT = 0x3C6EF372FE94F82BL;
    private static final ResourceKey<Biome> DESOLATION = ResourceKey.create(
        Registries.BIOME,
        ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "desolation"));

    private SurfaceWasteGenerator() {
    }

    /** Evaluates neighbouring anchors and writes only the portion in this chunk. */
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
        int sampledX = (anchorChunkX << 4) + random.nextInt(16);
        int sampledZ = (anchorChunkZ << 4) + random.nextInt(16);
        // Consume the same roll as before, but reject impossible candidates before
        // heightmap/biome/block reads. This avoids nine terrain scans per chunk.
        double chanceRoll = random.nextDouble();
        if (chanceRoll >= BASE_CHANCE * 12.0) {
            return;
        }
        int sampledY = DesolationWorldgenSupport.findGroundY(level, sampledX, sampledZ);
        if (sampledY == DesolationWorldgenSupport.NO_SURFACE) {
            return;
        }
        boolean desolation = level.getBiome(new BlockPos(sampledX, sampledY, sampledZ)).is(DESOLATION);
        double chance = BASE_CHANCE * (desolation ? 12.0 : 1.0);
        if (chanceRoll >= chance) {
            return;
        }

        SurfaceSpot center = findBarrelCenter(level, sampledX, sampledZ, anchorChunkX, anchorChunkZ);
        if (center == null) {
            return;
        }
        int centerX = center.x();
        int centerZ = center.z();
        int centerY = center.y();

        int conversionRadius = 4 + random.nextInt(13);
        int barrelCount = 1 + random.nextInt(10);
        int width;
        int depth;
        do {
            width = barrelCount == 1 ? 1 + random.nextInt(4) : 2 + random.nextInt(3);
            depth = barrelCount == 1 ? 1 + random.nextInt(4) : 2 + random.nextInt(3);
        } while (width * depth < barrelCount);
        List<BarrelSite> barrels = createBarrelSites(random, centerX, centerZ,
            width, depth, barrelCount);
        double debrisChance = 0.01 + random.nextDouble() * 0.04;

        convertSurfacePatch(level, targetChunk, seed, centerX, centerZ, centerY, conversionRadius);
        placeBarrels(level, targetChunk, barrels);
        placeDebris(level, targetChunk, seed, barrels, debrisChance);
    }

    private static SurfaceSpot findBarrelCenter(WorldGenLevel level, int sampledX, int sampledZ,
                                               int anchorChunkX, int anchorChunkZ) {
        int minX = anchorChunkX << 4;
        int minZ = anchorChunkZ << 4;
        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = sampledX + dx;
                    int z = sampledZ + dz;
                    if (x < minX || x >= minX + 16 || z < minZ || z >= minZ + 16) {
                        continue;
                    }
                    int groundY = DesolationWorldgenSupport.findGroundY(level, x, z);
                    if (groundY == DesolationWorldgenSupport.NO_SURFACE || groundY + 1 >= level.getMaxY()) {
                        continue;
                    }
                    BlockPos surface = new BlockPos(x, groundY, z);
                    if (DesolationWorldgenSupport.isSurfaceSupport(level.getBlockState(surface))
                            && DesolationWorldgenSupport.canPlaceBarrelAt(
                                level.getBlockState(surface.above()))) {
                        return new SurfaceSpot(x, groundY, z);
                    }
                }
            }
        }
        return null;
    }

    private static void convertSurfacePatch(WorldGenLevel level, ChunkPos targetChunk, long seed,
                                            int centerX, int centerZ, int centerY, int radius) {
        int minX = targetChunk.getMinBlockX();
        int maxX = minX + 16;
        int minZ = targetChunk.getMinBlockZ();
        int maxZ = minZ + 16;
        int fromX = Math.max(minX, centerX - radius - 2);
        int toX = Math.min(maxX - 1, centerX + radius + 2);
        int fromZ = Math.max(minZ, centerZ - radius - 2);
        int toZ = Math.min(maxZ - 1, centerZ + radius + 2);

        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                int groundY = DesolationWorldgenSupport.findGroundY(level, x, z);
                if (groundY == DesolationWorldgenSupport.NO_SURFACE
                        || Math.abs(groundY - centerY) > 6) {
                    continue;
                }
                double dx = x + 0.5 - (centerX + 0.5);
                double dz = z + 0.5 - (centerZ + 0.5);
                double distance = Math.sqrt(dx * dx + dz * dz);
                double edgeNoise = DesolationWorldgenSupport.noise(seed ^ 0xA54FF53A5F1D36F1L,
                    x, z, 3.8) * 1.6;
                double localRadius = clamp(radius + edgeNoise, 4.0, 16.0);
                if (distance > localRadius) {
                    continue;
                }

                double coverage = distance < radius - 2.0
                    ? 1.0
                    : clamp((localRadius - distance) / 3.0, 0.0, 1.0);
                if (DesolationWorldgenSupport.hash01(seed ^ 0x510E527FADE682D1L, x, z) >= coverage) {
                    continue;
                }
                DesolationConversionFeature.convertColumnWithoutBiome(
                    level, x, z, RandomSource.create(columnSeed(seed, x, z)));
            }
        }
    }

    private static void placeBarrels(WorldGenLevel level, ChunkPos targetChunk,
                                     List<BarrelSite> barrels) {
        int minX = targetChunk.getMinBlockX();
        int maxX = minX + 16;
        int minZ = targetChunk.getMinBlockZ();
        int maxZ = minZ + 16;
        for (BarrelSite barrel : barrels) {
            if (barrel.x() < minX || barrel.x() >= maxX || barrel.z() < minZ || barrel.z() >= maxZ) {
                continue;
            }
            int groundY = DesolationWorldgenSupport.findGroundY(level, barrel.x(), barrel.z());
            if (groundY == DesolationWorldgenSupport.NO_SURFACE) {
                continue;
            }
            BlockPos surface = new BlockPos(barrel.x(), groundY, barrel.z());
            BlockPos placePos = surface.above();
            if (placePos.getY() >= level.getMaxY()
                    || !DesolationWorldgenSupport.isSurfaceSupport(level.getBlockState(surface))) {
                continue;
            }
            BlockState above = level.getBlockState(placePos);
            if (above.is(ModBlocks.WASTE_BARREL.get())
                    || !DesolationWorldgenSupport.canPlaceBarrelAt(above)) {
                continue;
            }
            DesolationWorldgenSupport.clearSurfaceVegetation(level, barrel.x(), groundY, barrel.z());
            level.setBlock(placePos, ModBlocks.WASTE_BARREL.get().defaultBlockState(), 2);
        }
    }

    private static void placeDebris(WorldGenLevel level, ChunkPos targetChunk, long seed,
                                    List<BarrelSite> barrels, double chance) {
        int minX = targetChunk.getMinBlockX();
        int maxX = minX + 16;
        int minZ = targetChunk.getMinBlockZ();
        int maxZ = minZ + 16;
        Set<Long> barrelColumns = new HashSet<>();
        for (BarrelSite barrel : barrels) {
            barrelColumns.add(BlockPos.asLong(barrel.x(), 0, barrel.z()));
        }

        for (BarrelSite barrel : barrels) {
            int debrisRadius = 2 + Math.floorMod((int) columnSeed(seed, barrel.x(), barrel.z()), 3);
            int fromX = Math.max(minX, barrel.x() - debrisRadius);
            int toX = Math.min(maxX - 1, barrel.x() + debrisRadius);
            int fromZ = Math.max(minZ, barrel.z() - debrisRadius);
            int toZ = Math.min(maxZ - 1, barrel.z() + debrisRadius);
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    long column = BlockPos.asLong(x, 0, z);
                    if (barrelColumns.contains(column)
                            || (x - barrel.x()) * (x - barrel.x())
                                + (z - barrel.z()) * (z - barrel.z()) > debrisRadius * debrisRadius
                            || DesolationWorldgenSupport.hash01(seed ^ 0x9B05688C2B3E6C1FL, x, z)
                                >= chance) {
                        continue;
                    }
                    int groundY = DesolationWorldgenSupport.findGroundY(level, x, z);
                    if (groundY == DesolationWorldgenSupport.NO_SURFACE) {
                        continue;
                    }
                    long posSeed = columnSeed(seed ^ barrel.x() * 31L ^ barrel.z(), x, z);
                    int y = groundY - Math.floorMod((int) posSeed, 4);
                    if (y < level.getMinY()) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!DesolationWorldgenSupport.isNaturalTerrain(level.getBlockState(pos))) {
                        continue;
                    }
                    BlockState debris = (DesolationWorldgenSupport.hash01(posSeed, x, z) < 0.5)
                        ? ModBlocks.DEBRIS.get().defaultBlockState()
                        : ModBlocks.WEATHERED_DEBRIS.get().defaultBlockState();
                    if (y == groundY) {
                        DesolationWorldgenSupport.setSurfaceBlock(level, pos, debris);
                    } else {
                        level.setBlock(pos, debris, 2);
                    }
                }
            }
        }
    }

    private static List<BarrelSite> createBarrelSites(RandomSource random, int centerX, int centerZ,
                                                       int width, int depth, int count) {
        List<BarrelSite> sites = new ArrayList<>(count);
        Set<Long> occupied = new HashSet<>();
        int minX = centerX - (width - 1) / 2;
        int minZ = centerZ - (depth - 1) / 2;
        sites.add(new BarrelSite(centerX, centerZ));
        occupied.add(BlockPos.asLong(centerX, 0, centerZ));
        int attempts = 0;
        while (sites.size() < count && attempts++ < width * depth * 4) {
            int x = minX + random.nextInt(width);
            int z = minZ + random.nextInt(depth);
            if (occupied.add(BlockPos.asLong(x, 0, z))) {
                sites.add(new BarrelSite(x, z));
            }
        }
        return sites;
    }

    private static long candidateSeed(long worldSeed, int chunkX, int chunkZ) {
        long value = worldSeed ^ SEED_SALT
            ^ ((long) chunkX * 0x9E3779B97F4A7C15L)
            ^ ((long) chunkZ * 0xC2B2AE3D27D4EB4FL);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static long columnSeed(long seed, int x, int z) {
        return seed
            ^ ((long) x * 0x9E3779B97F4A7C15L)
            ^ ((long) z * 0xC2B2AE3D27D4EB4FL);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record BarrelSite(int x, int z) {
    }

    private record SurfaceSpot(int x, int y, int z) {
    }
}
