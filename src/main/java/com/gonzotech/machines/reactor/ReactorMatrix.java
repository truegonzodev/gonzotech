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

/** Builds dry, structure-only coefficient inputs from the formed reactor's live interior blocks. */
public final class ReactorMatrix {

    private static final MatrixCoefficients ENGINE = MatrixCoefficients.balancedDefault();

    /** Structural coefficient result; fuel loading is intentionally not an input. */
    public record Diagnostic(int fuelSheaths, MatrixCoefficients.Coefficients coefficients) {
    }

    private ReactorMatrix() {
    }

    public static Diagnostic evaluate(ServerLevel level, ReactorControlPanelBlockEntity panel) {
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

        return new Diagnostic(fuelSheaths, ENGINE.evaluate(grid));
    }

    private static boolean isMetalBlock(Block block, String path) {
        var key = BuiltInRegistries.BLOCK.getKey(block);
        return key != null && "gonzotech".equals(key.getNamespace()) && path.equals(key.getPath());
    }
}
