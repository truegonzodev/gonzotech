package com.gonzotech.machines.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Non-loading read envelope for vanilla redstone calls made by item logistics. */
final class LoadedItemChunks {
    private LoadedItemChunks() { }

    /**
     * Conservative horizontal envelope: signal -> adjacent conductor -> strong
     * wire -> wire connection can read three blocks from the queried position.
     * Comparator notifications read the neighbour and a second block behind a
     * conductor, then may inspect that comparator's adjacent input. Chunk
     * availability is column-based; checking four corners covers the square.
     * This does not load anything or replace the ordinary server chunk lifecycle.
     */
    static boolean redstoneAreaLoaded(Level level, BlockPos pos) {
        int reach = 3;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        return level.hasChunkAt(new BlockPos(x - reach, y, z - reach))
            && level.hasChunkAt(new BlockPos(x - reach, y, z + reach))
            && level.hasChunkAt(new BlockPos(x + reach, y, z - reach))
            && level.hasChunkAt(new BlockPos(x + reach, y, z + reach));
    }
}
