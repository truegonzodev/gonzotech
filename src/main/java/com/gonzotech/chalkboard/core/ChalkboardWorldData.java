package com.gonzotech.chalkboard.core;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            puzzleCache.computeIfAbsent(idx, k -> stableIds(k,
                    GameSolver.generateDiscovery(DiscoveryDef.get(k), worldSeed)));
        }
        long elapsed = System.currentTimeMillis() - startMs;
        LOGGER.info("[Chalkboard] Pre-generated all 16 world discovery puzzles on server init in {} ms (seed={})", elapsed, worldSeed);
    }

    public GameSolver.Puzzle getPuzzle(int index) {
        if (index >= 16) {
            return puzzleCache.computeIfAbsent(index, i -> stableIds(i, GameSolver.generateInfinite(i, worldSeed)));
        }
        int idx = Math.max(0, Math.min(15, index));
        return puzzleCache.computeIfAbsent(idx, i -> stableIds(i,
                GameSolver.generateDiscovery(DiscoveryDef.get(i), worldSeed)));
    }

    /**
     * {@link Expr#nid(String)} is process-local, while a saved board must remain
     * recognisable after a server restart.  Rebuild every server puzzle with
     * path-derived IDs so the seed/index pair has the same skeleton identity in
     * every runtime and for every dimension.
     */
    private static GameSolver.Puzzle stableIds(int stageIndex, GameSolver.Puzzle generated) {
        Map<String, String> idMap = new HashMap<>();
        Expr stableExpr = stableExpr(generated.expr(), "root", stageIndex, idMap);

        List<String> locked = new ArrayList<>();
        for (String id : generated.lockedSlotIds()) {
            String stableId = idMap.get(id);
            if (stableId != null) locked.add(stableId);
        }

        Map<String, String> solution = new HashMap<>();
        for (Map.Entry<String, String> entry : generated.sampleSolution().entrySet()) {
            String stableId = idMap.get(entry.getKey());
            if (stableId != null) solution.put(stableId, entry.getValue());
        }

        return new GameSolver.Puzzle(stableExpr, generated.target(), locked, solution,
                generated.bestScore(), generated.expansionsDone(), generated.difficulty(),
                generated.title(), generated.description());
    }

    private static Expr stableExpr(Expr expr, String path, int stageIndex, Map<String, String> idMap) {
        String stableId = "cb_" + stageIndex + "_" + path;
        idMap.put(expr.id(), stableId);
        if (expr instanceof Expr.Slot slot) {
            return new Expr.Slot(stableId, slot.quantityId(), slot.locked(), slot.isAdded());
        }
        if (expr instanceof Expr.Num num) {
            return new Expr.Num(stableId, num.value(), num.label());
        }
        if (expr instanceof Expr.Pow pow) {
            return new Expr.Pow(stableId, stableExpr(pow.base(), path + "_b", stageIndex, idMap), pow.exp());
        }
        if (expr instanceof Expr.Op op) {
            return new Expr.Op(stableId, op.op(),
                    stableExpr(op.left(), path + "_l", stageIndex, idMap),
                    stableExpr(op.right(), path + "_r", stageIndex, idMap), op.isAdded());
        }
        if (expr instanceof Expr.Eq eq) {
            return new Expr.Eq(stableId,
                    stableExpr(eq.left(), path + "_l", stageIndex, idMap),
                    stableExpr(eq.right(), path + "_r", stageIndex, idMap));
        }
        throw new IllegalArgumentException("Unknown expression node");
    }

    /**
     * Resolve data through the overworld, not the player's current dimension.
     * Chalkboard discoveries are world progression shared across dimensions;
     * using a per-dimension {@link DimensionDataStorage} would create separate
     * caches (and distinct expression node IDs) for the same player.
     */
    public static ChalkboardWorldData get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        DimensionDataStorage storage = overworld.getDataStorage();
        long seed = overworld.getSeed();
        return storage.computeIfAbsent(
                new Factory<>(
                        () -> new ChalkboardWorldData(seed),
                        (tag, provider) -> load(tag, seed),
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
