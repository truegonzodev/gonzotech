package com.gonzotech.cleanroom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos.asLong())) continue;
            // The component is bounded by the contour. A solid block inside
            // it is simply an obstacle and must not invalidate the room.
            if (visited.size() > MAX_VOLUME) return new Result(false, 0L, visited.size());
            hash ^= pos.asLong();
            hash *= 0x100000001b3L;
            for (var direction : net.minecraft.core.Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (level.getBlockState(next).isAir()
                        && !visited.contains(next.asLong())) {
                    queue.add(next.immutable());
                }
            }
        }
        // A closed contour produces a finite air component. Interior machines,
        // filters, nodes and other solid blocks never become part of the
        // component, so they cannot break an already closed contour.
        return new Result(true, hash, visited.size());
    }
}
