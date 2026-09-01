package com.gonzotech.chalkboard.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structural edits of the immutable expression tree. */
public final class Manipulate {

    private Manipulate() {
    }

    /** Where a dragged block was dropped relative to the target block. */
    public enum Direction {
        CENTER, LEFT, RIGHT, TOP, BOTTOM
    }

    public record SlotInfo(String id, String quantityId, boolean locked, boolean isAdded) {
        public boolean filled() {
            return quantityId != null;
        }
    }

    // ───────────────────────── traversal ─────────────────────────

    public static List<SlotInfo> collectSlots(Expr e) {
        List<SlotInfo> out = new ArrayList<>();
        walkSlots(e, s -> out.add(new SlotInfo(s.id(), s.quantityId(), s.locked(), s.isAdded())));
        return out;
    }

    public static void walkSlots(Expr e, java.util.function.Consumer<Expr.Slot> fn) {
        switch (e) {
            case Expr.Slot s -> fn.accept(s);
            case Expr.Num ignored -> {
            }
            case Expr.Pow p -> walkSlots(p.base(), fn);
            case Expr.Op o -> {
                walkSlots(o.left(), fn);
                walkSlots(o.right(), fn);
            }
            case Expr.Eq q -> {
                walkSlots(q.left(), fn);
                walkSlots(q.right(), fn);
            }
        }
    }

    public static List<String> allNodeIds(Expr e) {
        List<String> ids = new ArrayList<>();
        collectIds(e, ids);
        return ids;
    }

    private static void collectIds(Expr e, List<String> out) {
        out.add(e.id());
        switch (e) {
            case Expr.Op o -> {
                collectIds(o.left(), out);
                collectIds(o.right(), out);
            }
            case Expr.Eq q -> {
                collectIds(q.left(), out);
                collectIds(q.right(), out);
            }
            case Expr.Pow p -> collectIds(p.base(), out);
            default -> {
            }
        }
    }

    // ───────────────────────── slot writes ─────────────────────────

    public static Expr setSlotQuantity(Expr e, String slotId, String quantityId) {
        return switch (e) {
            case Expr.Slot s -> s.id().equals(slotId) ? s.withQuantity(quantityId) : s;
            case Expr.Num n -> n;
            case Expr.Pow p -> new Expr.Pow(p.id(), setSlotQuantity(p.base(), slotId, quantityId), p.exp());
            case Expr.Op o -> new Expr.Op(o.id(), o.op(),
                    setSlotQuantity(o.left(), slotId, quantityId),
                    setSlotQuantity(o.right(), slotId, quantityId), o.isAdded());
            case Expr.Eq q -> new Expr.Eq(q.id(),
                    setSlotQuantity(q.left(), slotId, quantityId),
                    setSlotQuantity(q.right(), slotId, quantityId));
        };
    }

    public static Expr markLocked(Expr e, Set<String> lockedIds) {
        return switch (e) {
            case Expr.Slot s -> s.withLocked(lockedIds.contains(s.id()));
            case Expr.Num n -> n;
            case Expr.Pow p -> new Expr.Pow(p.id(), markLocked(p.base(), lockedIds), p.exp());
            case Expr.Op o -> new Expr.Op(o.id(), o.op(), markLocked(o.left(), lockedIds),
                    markLocked(o.right(), lockedIds), o.isAdded());
            case Expr.Eq q -> new Expr.Eq(q.id(), markLocked(q.left(), lockedIds), markLocked(q.right(), lockedIds));
        };
    }

    // ───────────────────────── directional wrapping ─────────────────────────

    /**
     * Wraps the target node with a new operation and a fresh slot:
     * <ul>
     *   <li>{@code LEFT}   → [new] × target</li>
     *   <li>{@code RIGHT}  → target × [new]</li>
     *   <li>{@code BOTTOM} → target ÷ [new] (fraction, new block below)</li>
     *   <li>{@code TOP}    → [new] ÷ target (fraction, new block above)</li>
     * </ul>
     * Works even when the target itself is frozen — that is how the player
     * builds around a locked node without ever replacing it.
     */
    public static Expr wrapNode(Expr root, String targetId, Direction dir, String newQuantityId) {
        return transform(root, targetId, dir, newQuantityId);
    }

    private static Expr transform(Expr node, String targetId, Direction dir, String qId) {
        if (node.id().equals(targetId)) {
            Expr.Slot fresh = new Expr.Slot(Expr.nid("s"), qId, false, true);
            return switch (dir) {
                case LEFT -> Expr.Op.added(Expr.OpKind.MUL, fresh, node);
                case RIGHT -> Expr.Op.added(Expr.OpKind.MUL, node, fresh);
                case BOTTOM -> Expr.Op.added(Expr.OpKind.DIV, node, fresh);
                case TOP -> Expr.Op.added(Expr.OpKind.DIV, fresh, node);
                case CENTER -> node;
            };
        }
        return switch (node) {
            case Expr.Op o -> new Expr.Op(o.id(), o.op(), transform(o.left(), targetId, dir, qId),
                    transform(o.right(), targetId, dir, qId), o.isAdded());
            case Expr.Eq q -> new Expr.Eq(q.id(), transform(q.left(), targetId, dir, qId),
                    transform(q.right(), targetId, dir, qId));
            case Expr.Pow p -> new Expr.Pow(p.id(), transform(p.base(), targetId, dir, qId), p.exp());
            default -> node;
        };
    }

    // ───────────────────────── removal / collapse ─────────────────────────

    /** A node can collapse only if it has an {@code Op} parent and is not frozen. */
    public static boolean canRemoveNode(Expr root, String targetId) {
        return findRemovable(root, targetId);
    }

    private static boolean findRemovable(Expr node, String targetId) {
        if (node instanceof Expr.Op o) {
            if (o.left().id().equals(targetId) && !isLockedSlot(o.left())) return true;
            if (o.right().id().equals(targetId) && !isLockedSlot(o.right())) return true;
            return findRemovable(o.left(), targetId) || findRemovable(o.right(), targetId);
        }
        if (node instanceof Expr.Eq q) {
            return findRemovable(q.left(), targetId) || findRemovable(q.right(), targetId);
        }
        if (node instanceof Expr.Pow p) {
            return findRemovable(p.base(), targetId);
        }
        return false;
    }

    private static boolean isLockedSlot(Expr e) {
        return e instanceof Expr.Slot s && s.locked();
    }

    /** Drops the node and collapses its parent operator into the sibling. */
    public static Expr removeNode(Expr root, String targetId) {
        return switch (root) {
            case Expr.Op o -> {
                if (o.left().id().equals(targetId)) yield o.right();
                if (o.right().id().equals(targetId)) yield o.left();
                yield new Expr.Op(o.id(), o.op(), removeNode(o.left(), targetId),
                        removeNode(o.right(), targetId), o.isAdded());
            }
            case Expr.Eq q -> new Expr.Eq(q.id(), removeNode(q.left(), targetId), removeNode(q.right(), targetId));
            case Expr.Pow p -> new Expr.Pow(p.id(), removeNode(p.base(), targetId), p.exp());
            default -> root;
        };
    }

    // ───────────────────────── graph distance (aura decay) ─────────────────────────

    public static Map<String, String> parents(Expr root) {
        Map<String, String> map = new HashMap<>();
        walkParents(root, null, map);
        return map;
    }

    private static void walkParents(Expr node, String parent, Map<String, String> map) {
        map.put(node.id(), parent);
        switch (node) {
            case Expr.Op o -> {
                walkParents(o.left(), node.id(), map);
                walkParents(o.right(), node.id(), map);
            }
            case Expr.Eq q -> {
                walkParents(q.left(), node.id(), map);
                walkParents(q.right(), node.id(), map);
            }
            case Expr.Pow p -> walkParents(p.base(), node.id(), map);
            default -> {
            }
        }
    }

    /** BFS distance in the operator graph — used by the aura falloff. */
    public static int graphDistance(Expr root, String a, String b) {
        if (a.equals(b)) return 0;
        Map<String, String> parent = parents(root);
        Map<String, List<String>> children = new HashMap<>();
        for (Map.Entry<String, String> e : parent.entrySet()) {
            if (e.getValue() == null) continue;
            children.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
        }
        Set<String> seen = new HashSet<>();
        seen.add(a);
        Deque<String> queue = new ArrayDeque<>();
        Map<String, Integer> depth = new HashMap<>();
        queue.add(a);
        depth.put(a, 0);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            int d = depth.get(cur);
            List<String> adj = new ArrayList<>(children.getOrDefault(cur, List.of()));
            String p = parent.get(cur);
            if (p != null) adj.add(p);
            for (String nb : adj) {
                if (seen.contains(nb)) continue;
                if (nb.equals(b)) return d + 1;
                seen.add(nb);
                depth.put(nb, d + 1);
                queue.add(nb);
            }
        }
        return 99;
    }
}
