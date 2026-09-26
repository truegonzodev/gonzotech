package com.gonzotech.cleanroom;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** One authoritative state per connected volume, with a reverse index for world edits. */
public final class RoomLedger {
    public static final class Room {
        private final Set<RoomTopology.Pos> cells;
        private final Set<RoomTopology.Pos> shell;
        private double quality;
        private long checkedAt = Long.MIN_VALUE;
        private boolean available;

        private Room(Set<RoomTopology.Pos> cells, Set<RoomTopology.Pos> shell, double quality) {
            this.cells = Set.copyOf(cells);
            this.shell = Set.copyOf(shell);
            this.quality = clamp(quality);
        }
        public Set<RoomTopology.Pos> cells() { return cells; }
        public Set<RoomTopology.Pos> shell() { return shell; }
        public double quality() { return quality; }
    }

    // A wall filter also queries its outside face. Tick-based operation must not
    // flood-fill 2049 outdoor cells 20 times/second for the same origin.
    private final Set<RoomTopology.Pos> oversized = new HashSet<>();
    private long oversizedEpoch = Long.MIN_VALUE;
    private final Set<Room> rooms = new HashSet<>();
    private final Map<RoomTopology.Pos, Room> byCell = new HashMap<>();
    private final Map<RoomTopology.Pos, Set<Room>> byShell = new HashMap<>();
    private final Runnable changed;

    public RoomLedger(Runnable changed) { this.changed = changed; }
    public Collection<Room> rooms() { return new ArrayList<>(rooms); }

    public Room find(Function<RoomTopology.Pos, RoomTopology.Kind> world, RoomTopology.Pos origin, long tick) {
        long epoch = Math.floorDiv(tick, 20);
        if (epoch != oversizedEpoch) {
            oversized.clear();
            oversizedEpoch = epoch;
        }
        Room known = byCell.get(origin);
        if (known != null) {
            if (known.checkedAt == tick) return known.available ? known : null;
            // Revalidate loaded/saved geometry without forcing chunk loads. An unloaded
            // chunk pauses a room; it is NOT a breach and must not erase its saved quality.
            boolean unloaded = false;
            boolean invalid = false;
            for (RoomTopology.Pos p : known.cells) {
                var kind = world.apply(p);
                unloaded |= kind == RoomTopology.Kind.UNLOADED;
                invalid |= kind != RoomTopology.Kind.UNLOADED && !allowedInside(kind);
            }
            for (RoomTopology.Pos p : known.shell) {
                var kind = world.apply(p);
                unloaded |= kind == RoomTopology.Kind.UNLOADED;
                invalid |= kind != RoomTopology.Kind.UNLOADED && kind != RoomTopology.Kind.SEAL;
            }
            if (!invalid) {
                known.checkedAt = tick;
                known.available = !unloaded;
                return unloaded ? null : known;
            }
            remove(known);
        }
        if (oversized.contains(origin)) return null;
        RoomTopology.Result result = RoomTopology.find(world, origin);
        if (!result.valid()) {
            if (result.status() == RoomTopology.Status.TOO_LARGE) {
                if (oversized.size() >= 4096) oversized.clear();
                oversized.add(origin);
            }
            return null;
        }
        Room room = restore(result.cells(), result.shell(), 0.0);
        room.checkedAt = tick;
        room.available = true;
        return room;
    }

    /** SavedData adapter calls this on load; geometry is verified at the first query. */
    public Room restore(Set<RoomTopology.Pos> cells, Set<RoomTopology.Pos> shell, double quality) {
        if (cells.isEmpty() || cells.size() > RoomTopology.MAX_VOLUME || shell.isEmpty()
                || shell.size() > 6 * RoomTopology.MAX_VOLUME || !java.util.Collections.disjoint(cells, shell)) {
            throw new IllegalArgumentException("Invalid clean-room geometry");
        }
        for (RoomTopology.Pos p : cells) {
            Room old = byCell.get(p);
            if (old != null) remove(old);
        }
        Room room = new Room(cells, shell, quality);
        rooms.add(room);
        for (RoomTopology.Pos p : cells) byCell.put(p, room);
        for (RoomTopology.Pos p : shell) byShell.computeIfAbsent(p, ignored -> new HashSet<>()).add(room);
        changed.run();
        return room;
    }

    private static boolean allowedInside(RoomTopology.Kind kind) {
        return kind == RoomTopology.Kind.INTERIOR || kind == RoomTopology.Kind.SEAL;
    }

    /** Preserve approved furniture only if it does not split the known air volume. */
    public void blockChanged(Function<RoomTopology.Pos, RoomTopology.Kind> world, RoomTopology.Pos pos,
                             RoomTopology.Kind before, RoomTopology.Kind after) {
        if (before == after) return;
        Room room = byCell.get(pos);
        if (room != null && allowedInside(before) && allowedInside(after)
                && (after == RoomTopology.Kind.INTERIOR || connectedWithout(world, room, pos))) return;
        invalidate(pos);
    }

    private static final int[][] DIRECTIONS = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};

    // Removing one traversable vertex preserves connectivity iff its air neighbours
    // are still mutually reachable. Stop as soon as those (at most six) targets meet;
    // ordinary furniture needs only a small local walk, worst case <= MAX_VOLUME.
    private static boolean connectedWithout(Function<RoomTopology.Pos, RoomTopology.Kind> world,
                                             Room room, RoomTopology.Pos blocked) {
        Set<RoomTopology.Pos> targets = new HashSet<>();
        for (int[] d : DIRECTIONS) {
            var p = blocked.offset(d[0], d[1], d[2]);
            if (!room.cells.contains(p)) continue;
            var kind = world.apply(p);
            if (kind == RoomTopology.Kind.UNLOADED) return false;
            if (kind == RoomTopology.Kind.INTERIOR) targets.add(p);
        }
        if (targets.isEmpty()) return false; // No air left at this location.
        var start = targets.iterator().next();
        targets.remove(start);
        var seen = new HashSet<RoomTopology.Pos>();
        seen.add(blocked); seen.add(start);
        var queue = new ArrayDeque<RoomTopology.Pos>();
        queue.add(start);
        while (!targets.isEmpty() && !queue.isEmpty()) {
            var p = queue.removeFirst();
            for (int[] d : DIRECTIONS) {
                var next = p.offset(d[0], d[1], d[2]);
                if (room.cells.contains(next) && seen.add(next)
                        && world.apply(next) == RoomTopology.Kind.INTERIOR) {
                    targets.remove(next);
                    queue.add(next);
                }
            }
        }
        return targets.isEmpty();
    }

    /** Called at the actual block mutation, including break-and-replace in a single tick. */
    public void invalidate(RoomTopology.Pos pos) {
        oversized.clear(); // Sealing a formerly open space must be visible immediately.
        Room inside = byCell.get(pos);
        Set<Room> affected = new HashSet<>(byShell.getOrDefault(pos, Set.of()));
        if (inside != null) affected.add(inside);
        for (Room room : affected) remove(room);
    }

    public void adjust(Room room, double delta) {
        if (!rooms.contains(room)) return;
        double next = clamp(room.quality + delta);
        if (next != room.quality) {
            room.quality = next;
            changed.run();
        }
    }

    private void remove(Room room) {
        if (!rooms.remove(room)) return;
        for (RoomTopology.Pos p : room.cells) byCell.remove(p, room);
        for (RoomTopology.Pos p : room.shell) {
            Set<Room> adjacent = byShell.get(p);
            if (adjacent != null) {
                adjacent.remove(room);
                if (adjacent.isEmpty()) byShell.remove(p);
            }
        }
        changed.run();
    }

    private static double clamp(double value) {
        return Double.isFinite(value) ? Math.max(0.0, Math.min(100.0, value)) : 0.0;
    }
}
