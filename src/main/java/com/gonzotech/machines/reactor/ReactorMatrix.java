package com.gonzotech.machines.reactor;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/** Builds and caches dry and loaded statistics for a formed reactor. */
public final class ReactorMatrix {

    private static final MatrixCoefficients ENGINE = MatrixCoefficients.balancedDefault();
    private static final Map<ReactorControlPanelBlockEntity, CacheEntry> CACHE = new WeakHashMap<>();

    /** Structural coefficient result; fuel loading is intentionally not part of the dry solve. */
    public record Diagnostic(int fuelSheaths, MatrixCoefficients.Analysis analysis) {
        public MatrixCoefficients.Coefficients coefficients() {
            return analysis.coefficients();
        }
    }

    public record OperatingSnapshot(Diagnostic diagnostic, int loadedTvels,
                                    ReactorOperatingMetrics.Values metrics) {
    }

    private record CacheEntry(Diagnostic diagnostic, int loadedTvels,
                              ReactorOperatingMetrics.Values metrics) {
    }

    private ReactorMatrix() {
    }

    /** Cached dry analysis; callers must invalidate it when the formed geometry/materials change. */
    public static Diagnostic evaluate(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        synchronized (CACHE) {
            CacheEntry cached = CACHE.get(panel);
            if (cached != null && cached.diagnostic() != null) return cached.diagnostic();
            Diagnostic diagnostic = calculate(level, panel);
            CACHE.put(panel, new CacheEntry(diagnostic, -1, null));
            return diagnostic;
        }
    }

    /**
     * Reuse the matrix solve and operating values until the structure or loaded count
     * changes. May return {@code null} while the volume chunks are unloaded and no prior
     * snapshot exists (0.3.269 #24): UNKNOWN must not read as "0 TVELs" — the caller
     * keeps its previous readings instead.
     */
    public static OperatingSnapshot operatingSnapshot(ServerLevel level,
                                                       ReactorControlPanelBlockEntity panel) {
        if (!panel.isFormedController()) {
            throw new IllegalStateException("Cannot evaluate a reactor without a formed controller");
        }
        int loadedTvels = ReactorFuelStorage.totalTvelsKnown(level, panel);
        if (loadedTvels == ReactorFuelStorage.UNKNOWN) {
            synchronized (CACHE) {
                CacheEntry cachedUnknown = CACHE.get(panel);
                if (cachedUnknown != null && cachedUnknown.metrics() != null) {
                    return new OperatingSnapshot(cachedUnknown.diagnostic(),
                        cachedUnknown.loadedTvels(), cachedUnknown.metrics());
                }
            }
            return null;
        }
        synchronized (CACHE) {
            CacheEntry cached = CACHE.get(panel);
            if (cached != null && cached.diagnostic() != null
                && cached.loadedTvels() == loadedTvels && cached.metrics() != null) {
                return new OperatingSnapshot(cached.diagnostic(), loadedTvels, cached.metrics());
            }
            Diagnostic diagnostic = cached != null && cached.diagnostic() != null
                ? cached.diagnostic() : calculate(level, panel);
            // Слоты GUI могут превышать расчётную ёмкость (миграция/переделка зоны):
            // лишние ТВЭЛы не участвуют в матрице, но остаются видимыми в чехлах.
            int capacity = diagnostic.fuelSheaths() <= 0 ? 0
                : Math.min(diagnostic.fuelSheaths() + 2, ReactorControlPanelBlockEntity.MAX_FUEL_SLOTS);
            int effectiveTvels = Math.min(loadedTvels, capacity);
            ReactorOperatingMetrics.Values metrics = ReactorOperatingMetrics.calculate(
                diagnostic.analysis(), effectiveTvels);
            CACHE.put(panel, new CacheEntry(diagnostic, loadedTvels, metrics));
            return new OperatingSnapshot(diagnostic, loadedTvels, metrics);
        }
    }

    /** Drop both dry and loaded values after a structure mutation or panel unload. */
    public static void invalidate(ReactorControlPanelBlockEntity panel) {
        ReactorFuelStorage.invalidateSheathCache(panel);
        synchronized (CACHE) {
            CACHE.remove(panel);
        }
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    private static Diagnostic calculate(ServerLevel level, ReactorControlPanelBlockEntity panel) {
        if (!panel.isFormedController()) {
            throw new IllegalStateException("Cannot evaluate a reactor without a formed controller");
        }
        BlockPos min = panel.min();
        BlockPos max = panel.max();
        int nx = max.getX() - min.getX() - 1;
        int ny = max.getY() - min.getY() - 1;
        int nz = max.getZ() - min.getZ() - 1;
        if (nx < 1 || nx != ny || nx != nz) {
            throw new IllegalStateException("Saved reactor bounds do not describe a positive interior cube");
        }

        MatrixCoefficients.Grid grid = new MatrixCoefficients.Grid(
            nx, ny, nz, MatrixCoefficients.Material.WATER);
        int fuelSheaths = 0;

        for (int z = 0; z < nz; z++) {
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    BlockPos worldPos = new BlockPos(
                        min.getX() + x + 1,
                        min.getY() + y + 1,
                        min.getZ() + z + 1);
                    BlockState state = level.getBlockState(worldPos);
                    Block block = state.getBlock();

                    if (block == ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()) {
                        // Grid.set(FUEL) assigns the model's fixed unit reference source per sheath.
                        grid.set(x, y, z, MatrixCoefficients.Material.FUEL);
                        fuelSheaths++;
                    } else if (block == ModBlocks.GRAPHITE_BLOCK.get()) {
                        grid.set(x, y, z, MatrixCoefficients.Material.GRAPHITE);
                    } else if (isMetalBlock(block, "boron_block")) {
                        grid.set(x, y, z, MatrixCoefficients.Material.BORON);
                    } else if (isMetalBlock(block, "barium_block")) {
                        grid.set(x, y, z, MatrixCoefficients.Material.BARIUM);
                    } else if (block == Blocks.WATER) {
                        grid.set(x, y, z, MatrixCoefficients.Material.WATER);
                    } else {
                        throw new IllegalStateException("Validated reactor contains an unmapped interior block: "
                            + BuiltInRegistries.BLOCK.getKey(block) + " at " + worldPos.toShortString());
                    }
                }
            }
        }

        return new Diagnostic(fuelSheaths, ENGINE.analyzeDetailed(grid));
    }

    static boolean isMetalBlock(Block block, String path) {
        var key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && "gonzotech".equals(key.getNamespace()) && path.equals(key.getPath());
    }
}
