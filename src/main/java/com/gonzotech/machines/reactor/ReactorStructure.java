package com.gonzotech.machines.reactor;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.registry.ModMachines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * Minimal validator for the first-generation uranium reactor's outer cube.
 *
 * <p>This stage only checks geometry and material classes; it does not run
 * neutron/thermal calculations. A valid structure is a cube from 4x4x4 to
 * 10x10x10 whose shell is made of casing/panel/nodes and whose interior has
 * no air and only the allowed moderator/absorber/fuel blocks.</p>
 */
public final class ReactorStructure {

    private ReactorStructure() {
    }

    public static String describe(Level level, BlockPos panelPos) {
        for (int size = 4; size <= 10; size++) {
            for (int dx = -(size - 1); dx <= 0; dx++) {
                for (int dy = -(size - 1); dy <= 0; dy++) {
                    for (int dz = -(size - 1); dz <= 0; dz++) {
                        BlockPos min = panelPos.offset(dx, dy, dz);
                        BlockPos max = min.offset(size - 1, size - 1, size - 1);
                        if (!contains(min, max, panelPos)) continue;
                        Result result = validate(level, min, max);
                        if (result != null) return result.describe();
                    }
                }
            }
        }
        return null;
    }

    private static boolean contains(BlockPos min, BlockPos max, BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private static Result validate(Level level, BlockPos min, BlockPos max) {
        int size = max.getX() - min.getX() + 1;
        if (size < 4 || size > 10) return null;
        if (max.getY() - min.getY() + 1 != size) return null;
        if (max.getZ() - min.getZ() + 1 != size) return null;

        int sheaths = 0;
        int heatNodes = 0;
        int fluidNodes = 0;
        int panels = 0;
        int graphite = 0;
        int barium = 0;
        int boron = 0;
        int water = 0;

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(p);
                    boolean shell = x == min.getX() || x == max.getX()
                        || y == min.getY() || y == max.getY()
                        || z == min.getZ() || z == max.getZ();

                    if (shell) {
                        Block block = state.getBlock();
                        if (block == ModMachines.THIRD_URANIUM_REACTOR_CASING.get()) {
                            // casing
                        } else if (block == ModMachines.THIRD_URANIUM_REACTOR_CONTROL_PANEL.get()) {
                            panels++;
                        } else if (block == ModMachines.THIRD_HEAT_NODE.get()) {
                            heatNodes++;
                        } else if (block == ModMachines.THIRD_UNIVERSAL_FLUID_NODE.get()) {
                            fluidNodes++;
                        } else {
                            return null;
                        }
                    } else {
                        if (state.isAir()) return null;
                        Block block = state.getBlock();
                        if (block == ModMachines.THIRD_URANIUM_REACTOR_FUEL_SHEATH.get()) {
                            sheaths++;
                        } else if (block == ModBlocks.GRAPHITE_BLOCK.get()) {
                            graphite++;
                        } else if (isMetalBlock(state, "barium_block")) {
                            barium++;
                        } else if (isMetalBlock(state, "boron_block")) {
                            boron++;
                        } else if (block == Blocks.WATER) {
                            water++;
                        } else {
                            return null;
                        }
                    }
                }
            }
        }

        if (panels < 1 || heatNodes < 1 || fluidNodes < 1) return null;

        return new Result(size, sheaths, heatNodes, fluidNodes, panels, graphite, barium, boron, water);
    }


    private static boolean isMetalBlock(BlockState state, String path) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals(path);
    }


    private static final class Result {
        final int size;
        final int sheaths;
        final int heatNodes;
        final int fluidNodes;
        final int panels;
        final int graphite;
        final int barium;
        final int boron;
        final int water;

        Result(int size, int sheaths, int heatNodes, int fluidNodes, int panels,
               int graphite, int barium, int boron, int water) {
            this.size = size;
            this.sheaths = sheaths;
            this.heatNodes = heatNodes;
            this.fluidNodes = fluidNodes;
            this.panels = panels;
            this.graphite = graphite;
            this.barium = barium;
            this.boron = boron;
            this.water = water;
        }

        String describe() {
            return "Реактор I поколения: куб " + size + "x" + size + "x" + size
                + ", чехлов=" + sheaths
                + ", теплоузлов=" + heatNodes
                + ", жидкостных узлов=" + fluidNodes
                + ", панелей=" + panels
                + ", графита=" + graphite
                + ", бария=" + barium
                + ", бора=" + boron
                + ", воды=" + water;
        }
    }
}
