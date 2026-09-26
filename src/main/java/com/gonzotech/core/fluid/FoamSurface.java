package com.gonzotech.core.fluid;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Client-independent surface sampling and four-connected, same-fluid puddles. */
public final class FoamSurface {
    public static final int RADIUS = 16;
    public static final int VERTICAL_RADIUS = 8;
    public static final int READS_PER_TICK = 512;
    private static final int WIDTH = 2 * RADIUS + 1;
    public static final int SCAN_VOLUME = WIDTH * WIDTH * (2 * VERTICAL_RADIUS + 1);
    private static final int[][] SIDES = {{1,0}, {-1,0}, {0,1}, {0,-1}};

    public record Pos(int x, int y, int z) {
        public Pos offset(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
        public boolean near(Pos other) {
            return Math.abs((long)x - other.x) <= RADIUS && Math.abs((long)z - other.z) <= RADIUS
                    && Math.abs((long)y - other.y) <= VERTICAL_RADIUS;
        }
    }
    public record Pool(int kind, List<Pos> cells) {}
    public record Snapshot(List<Pool> pools, Map<Pos, Integer> membership) {}
    public static final Snapshot EMPTY = new Snapshot(List.of(), Map.of());
    private FoamSurface() {}

    public static int target(int surfaceCells) {
        return surfaceCells <= 0 ? 0 : (int)Math.round(7 + 1.32 * surfaceCells);
    }

    /** Missing cells, other kinds, diagonal touches and different block Y do not connect. */
    public static Snapshot group(Map<Pos, Integer> surface) {
        var seen = new HashSet<Pos>();
        var pools = new ArrayList<Pool>();
        var membership = new HashMap<Pos, Integer>();
        for (var entry : surface.entrySet()) {
            Pos seed = entry.getKey();
            if (!seen.add(seed)) continue;
            int kind = entry.getValue(), index = pools.size();
            var cells = new ArrayList<Pos>();
            var queue = new ArrayDeque<Pos>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                Pos pos = queue.removeFirst();
                cells.add(pos);
                membership.put(pos, index);
                for (int[] side : SIDES) {
                    Pos next = pos.offset(side[0], 0, side[1]);
                    Integer nextKind = surface.get(next);
                    if (nextKind != null && nextKind == kind && seen.add(next)) queue.add(next);
                }
            }
            pools.add(new Pool(kind, List.copyOf(cells)));
        }
        return new Snapshot(List.copyOf(pools), Map.copyOf(membership));
    }

    /** One bounded rolling scan, never a world-sized flood fill or per-block random tick. */
    public static final class Scan {
        private final Pos centre;
        private final Map<Pos, Integer> surface = new LinkedHashMap<>();
        private int cursor;
        public Scan(Pos centre) { this.centre = centre; }
        public Pos centre() { return centre; }
        public boolean done() { return cursor == SCAN_VOLUME; }
        public int advance(ToIntFunction<Pos> reader) {
            int reads = 0;
            while (!done() && reads < READS_PER_TICK) {
                int column = cursor % (WIDTH * WIDTH), layer = cursor / (WIDTH * WIDTH);
                // Sample the observer's height first, then alternate below/above it.
                int dy = layer == 0 ? 0 : (layer % 2 == 1 ? -(layer + 1) / 2 : layer / 2);
                Pos pos = centre.offset(column % WIDTH - RADIUS, dy, column / WIDTH - RADIUS);
                int kind = reader.applyAsInt(pos);
                if (kind >= 0) surface.put(pos, kind);
                cursor++; reads++;
            }
            return reads;
        }
        public Snapshot snapshot() {
            if (!done()) throw new IllegalStateException("Do not publish unfinished scan fragments as separate puddles");
            return group(surface);
        }
    }
}
