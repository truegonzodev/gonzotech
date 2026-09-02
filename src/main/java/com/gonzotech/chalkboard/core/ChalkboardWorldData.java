package com.gonzotech.chalkboard.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-authoritative SavedData storing the 16 seed-deterministic discovery puzzles.
 */
public class ChalkboardWorldData extends SavedData {

    private static final String DATA_NAME = "gonzotech_chalkboard_puzzles";

    private final long worldSeed;
    private final Map<Integer, GameSolver.Puzzle> puzzleCache = new HashMap<>();

    public ChalkboardWorldData(long seed) {
        this.worldSeed = seed;
        initPuzzles();
    }

    private void initPuzzles() {
        for (int i = 0; i < 16; i++) {
            DiscoveryDef def = DiscoveryDef.get(i);
            puzzleCache.put(i, GameSolver.generateDiscovery(def, worldSeed));
        }
    }

    public GameSolver.Puzzle getPuzzle(int index) {
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
        // Puzzles are deterministically derived from world seed, but data can be marked dirty if needed.
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("seed", worldSeed);
        return tag;
    }
}
