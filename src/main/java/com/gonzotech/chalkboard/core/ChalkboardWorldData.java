package com.gonzotech.chalkboard.core;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-authoritative SavedData storing the 16 seed-deterministic discovery puzzles
 * and dynamic post-game infinite puzzles.
 * Eagerly pre-generates all 16 world discovery puzzles on server launch in ~2-5 ms.
 */
public class ChalkboardWorldData extends SavedData {

    private static final String DATA_NAME = "gonzotech_chalkboard_puzzles";
    private static final Logger LOGGER = LogUtils.getLogger();

    private final long worldSeed;
    private final Map<Integer, GameSolver.Puzzle> puzzleCache = new HashMap<>();

    public ChalkboardWorldData(long seed) {
        this.worldSeed = seed;
        preloadDiscoveries();
    }

    /**
     * Eagerly pre-generates all 16 world discoveries on initialization.
     * Takes ~2-5 ms total thanks to the Greedy Vector Reduction solver.
     */
    private void preloadDiscoveries() {
        long startMs = System.currentTimeMillis();
        for (int i = 0; i < 16; i++) {
            final int idx = i;
            puzzleCache.computeIfAbsent(idx, k -> GameSolver.generateDiscovery(DiscoveryDef.get(idx), worldSeed));
        }
        long elapsed = System.currentTimeMillis() - startMs;
        LOGGER.info("[Chalkboard] Pre-generated all 16 world discovery puzzles on server init in {} ms (seed={})", elapsed, worldSeed);
    }

    public GameSolver.Puzzle getPuzzle(int index) {
        if (index >= 16) {
            return puzzleCache.computeIfAbsent(index, i -> GameSolver.generateInfinite(i, worldSeed));
        }
        int idx = Math.max(0, Math.min(15, index));
        return puzzleCache.computeIfAbsent(idx, i -> GameSolver.generateDiscovery(DiscoveryDef.get(i), worldSeed));
    }

    public static ChalkboardWorldData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new Factory<>(
                        () -> new ChalkboardWorldData(level.getSeed()),
                        (tag, provider) -> load(tag, level.getSeed()),
                        null
                ),
                DATA_NAME
        );
    }

    private static ChalkboardWorldData load(CompoundTag tag, long seed) {
        ChalkboardWorldData data = new ChalkboardWorldData(seed);
        LOGGER.info("[Chalkboard] Loaded chalkboard SavedData with seed {}", seed);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("seed", worldSeed);
        return tag;
    }
}
