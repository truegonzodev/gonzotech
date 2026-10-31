package com.gonzotech.machines.network;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Same-tick directed transfer history. Contains no Level, Container or ItemStack references. */
final class ItemTransferGuard {
    record Owner(boolean entity, long first, long second) {
        static Owner block(long position) { return new Owner(false, position, 0); }
        static Owner entity(UUID id) { return new Owner(true, id.getMostSignificantBits(), id.getLeastSignificantBits()); }
    }
    private record Edge(Owner from, Owner to) { }

    private long tick = Long.MIN_VALUE;
    private final Map<Owner, Set<Owner>> outgoing = new HashMap<>();
    private final Set<Edge> denied = new HashSet<>();

    private void at(long now) {
        if (tick == now) return;
        outgoing.clear();
        denied.clear();
        tick = now;
    }

    boolean allows(long now, List<Owner> source, List<Owner> target) {
        at(now);
        if (source.isEmpty() || target.isEmpty()) return false;
        for (Owner from : source) {
            for (Owner to : target) {
                if (from.equals(to)) return false;
                Edge edge = new Edge(from, to);
                if (denied.contains(edge)) return false;
                // Repeated forward transfers on an existing edge do not need another search.
                if (outgoing.getOrDefault(from, Set.of()).contains(to)) continue;
                if (reaches(to, from)) {
                    denied.add(edge); // edges only grow during this tick; a denial remains valid
                    return false;
                }
            }
        }
        return true;
    }

    /** Called only after a successful, already checked transfer on the server thread. */
    void record(long now, List<Owner> source, List<Owner> target) {
        at(now);
        for (Owner from : source) outgoing.computeIfAbsent(from, key -> new HashSet<>()).addAll(target);
    }

    private boolean reaches(Owner start, Owner goal) {
        if (!outgoing.containsKey(start)) return false;
        Set<Owner> visited = new HashSet<>();
        ArrayDeque<Owner> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            Owner at = queue.removeFirst();
            if (at.equals(goal)) return true;
            if (!visited.add(at)) continue;
            for (Owner next : outgoing.getOrDefault(at, Set.of())) queue.addLast(next);
        }
        return false;
    }
}
