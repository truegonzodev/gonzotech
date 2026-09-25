package com.gonzotech.cleanroom;

import com.gonzotech.core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Bounded server-side detector for clean-room air volumes. */
public final class CleanRoomDetector {
    private static final int MAX_VOLUME = 2048;

    private CleanRoomDetector() {}

    public record Result(boolean valid, long fingerprint, int volume) {}

    public static Result find(ServerLevel level, BlockPos origin) {
        if (!level.getBlockState(origin).isAir()) return new Result(false, 0L, 0);
        Set<Long> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin.immutable());
        long hash = 0xcbf29ce484222325L;
        boolean valid = true;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos.asLong())) continue;
            if (visited.size() > MAX_VOLUME) return new Result(false, 0L, visited.size());
            hash ^= pos.asLong();
            hash *= 0x100000001b3L;
            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos next = pos.relative(direction);
                BlockState state = level.getBlockState(next);
                if (state.isAir()) {
                    if (!visited.contains(next.asLong())) queue.add(next.immutable());
                } else if (!isCleanShell(state)) {
                    valid = false;
                }
            }
        }
        return valid ? new Result(true, hash, visited.size()) : new Result(false, 0L, visited.size());
    }

    private static boolean isCleanShell(BlockState state) {
        var block = state.getBlock();
        return block == ModBlocks.PORCELAIN.get()
                || block == ModBlocks.BORE_STAINED_GLASS.get()
                || block == ModBlocks.THIRD_HERMETIC_DOOR.get()
                || block == Blocks.QUARTZ_BLOCK
                || block == Blocks.QUARTZ_BRICKS
                || block == Blocks.CHISELED_QUARTZ_BLOCK
                || block == Blocks.WHITE_CONCRETE;
    }
}
