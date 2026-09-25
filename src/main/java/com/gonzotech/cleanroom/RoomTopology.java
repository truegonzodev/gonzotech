package com.gonzotech.cleanroom;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Minecraft-independent, six-face flood fill. Equipment is traversable, never a seal. */
public final class RoomTopology {
    public static final int MAX_VOLUME = 2048;
    public enum Kind { INTERIOR, SEAL, FORBIDDEN, UNLOADED }
    public enum Status { VALID, OPEN, TOO_LARGE, UNLOADED }

    public record Pos(int x, int y, int z) {
        public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }
    public record Result(Status status, Set<Pos> cells, Set<Pos> shell) {
        public boolean valid() { return status == Status.VALID; }
    }
    private static final int[][] DIRECTIONS = {
            {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}
    };

    private RoomTopology() {}

    public static Result find(Function<Pos, Kind> world, Pos origin) {
        Set<Pos> cells = new HashSet<>();
        Set<Pos> shell = new HashSet<>();
        Kind start = world.apply(origin);
        if (start != Kind.INTERIOR) return result(start == Kind.UNLOADED ? Status.UNLOADED : Status.OPEN, cells, shell);
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        cells.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            Pos pos = queue.removeFirst();
            for (int[] d : DIRECTIONS) {
                Pos next = pos.offset(d[0], d[1], d[2]);
                if (cells.contains(next) || shell.contains(next)) continue;
                Kind kind = world.apply(next);
                if (kind == Kind.UNLOADED) return result(Status.UNLOADED, cells, shell);
                if (kind == Kind.FORBIDDEN) return result(Status.OPEN, cells, shell);
                if (kind == Kind.SEAL) {
                    shell.add(next);
                } else {
                    cells.add(next);
                    if (cells.size() > MAX_VOLUME) return result(Status.TOO_LARGE, cells, shell);
                    queue.addLast(next);
                }
            }
        }
        return result(Status.VALID, cells, shell);
    }

    private static Result result(Status status, Set<Pos> cells, Set<Pos> shell) {
        return new Result(status, Set.copyOf(cells), Set.copyOf(shell));
    }
}
