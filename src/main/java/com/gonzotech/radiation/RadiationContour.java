package com.gonzotech.radiation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/** Bounded, world-independent implementation of the containment geometry and layer policy. */
public final class RadiationContour {
    public static final int VISIT_CAP = 2048;
    public static final int RANGE = 32;
    public static final int MAX_LAYERS = 8;
    public static final double ZERO_THRESHOLD = 1.0E-3;
    public static final double INSIDE_DOSE_WEIGHT = 0.40;
    public enum Kind { OPEN, WALL, SEAL, UNLOADED }
    public record Pos(int x, int y, int z) {
        public Pos move(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }
    public record Cell(Kind kind, double factor, double emission) {
        public boolean barrier() { return kind == Kind.WALL || kind == Kind.SEAL; }
    }
    public static final Cell AIR = new Cell(Kind.OPEN, 1, 0);
    public static final Cell UNLOADED = new Cell(Kind.UNLOADED, 1, 0);
    public record Result(boolean enclosed, double factor, double emission, Set<Pos> interior) {
        public double insideDose() { return enclosed ? emission * INSIDE_DOSE_WEIGHT : 0; }
    }
    private record Step(Pos pos, int direction) {}
    private static final Result OPEN = new Result(false, 1, 0, Set.of());
    private static final int[][] DIRECTIONS = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
    private RadiationContour() {}

    /** Each boundary cell contributes once; its first BFS arrival supplies the outward normal. */
    public static Result probe(Function<Pos, Cell> world, Pos origin) {
        var queue = new ArrayDeque<Step>();
        Set<Pos> visited = new HashSet<>(), interior = new HashSet<>();
        queue.add(new Step(origin, 0));
        visited.add(origin);
        double sum = 0, emission = 0;
        int walls = 0, seals = 0;
        while (!queue.isEmpty()) {
            Step step = queue.remove();
            Pos pos = step.pos();
            Cell cell = world.apply(pos);
            if (cell.kind() == Kind.UNLOADED) return OPEN;
            // Sources are not walls for their own radiation. Traverse adjacent radioactive
            // blocks too: a pile inside the room must neither hide its inner blocks nor
            // replace the actual enclosing lead wall with an imaginary source-block shell.
            if (!pos.equals(origin) && cell.emission() <= 0 && cell.barrier()) {
                if (cell.kind() == Kind.SEAL && cell.factor() >= 1) {
                    seals++;
                } else {
                    sum += depth(world, pos, DIRECTIONS[step.direction()]);
                    walls++;
                }
                continue;
            }
            interior.add(pos);
            emission += Math.max(0, cell.emission());
            for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                int[] d = DIRECTIONS[direction];
                Pos next = pos.move(d[0], d[1], d[2]);
                if (Math.abs(next.x() - origin.x()) > RANGE || Math.abs(next.y() - origin.y()) > RANGE
                        || Math.abs(next.z() - origin.z()) > RANGE) return OPEN;
                if (!visited.contains(next)) {
                    if (visited.size() >= VISIT_CAP) return OPEN;
                    visited.add(next);
                    queue.add(new Step(next, direction));
                }
            }
        }
        if (walls + seals == 0) return OPEN;
        // A mostly-door shell must not get the protection of one isolated shielding block.
        if (seals * 100 > (walls + seals) * 50) {
            sum += seals;
            walls += seals;
        }
        double average = walls == 0 ? 1 : sum / walls;
        double factor = average < ZERO_THRESHOLD ? 0 : Math.min(1, average);
        return new Result(true, factor, emission, Set.copyOf(interior));
    }

    private static double depth(Function<Pos, Cell> world, Pos start, int[] direction) {
        double product = 1;
        Pos pos = start;
        for (int layer = 0; layer < MAX_LAYERS; layer++) {
            Cell cell = world.apply(pos);
            // No global "second layer exists" flag: only this exact outward ray.
            // Air gaps, open doors and unloaded blocks never grant extra shielding.
            if (!cell.barrier()) break;
            product *= cell.factor();
            if (product < ZERO_THRESHOLD) return 0;
            pos = pos.move(direction[0], direction[1], direction[2]);
        }
        return product;
    }
}
