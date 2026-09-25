package com.gonzotech.cleanroom;

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

    private final Set<Room> rooms = new HashSet<>();
    private final Map<RoomTopology.Pos, Room> byCell = new HashMap<>();
    private final Map<RoomTopology.Pos, Set<Room>> byShell = new HashMap<>();
    private final Runnable changed;

    public RoomLedger(Runnable changed) { this.changed = changed; }
    public Collection<Room> rooms() { return new ArrayList<>(rooms); }

    public Room find(Function<RoomTopology.Pos, RoomTopology.Kind> world, RoomTopology.Pos origin, long tick) {
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
                invalid |= kind != RoomTopology.Kind.UNLOADED && kind != RoomTopology.Kind.INTERIOR;
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
        RoomTopology.Result result = RoomTopology.find(world, origin);
        if (!result.valid()) return null;
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

    /** Called at the actual block mutation, including break-and-replace in a single tick. */
    public void invalidate(RoomTopology.Pos pos) {
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
