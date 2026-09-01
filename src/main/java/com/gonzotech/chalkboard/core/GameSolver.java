package com.gonzotech.chalkboard.core;

import com.gonzotech.chalkboard.core.Quantity.Category;
import com.gonzotech.chalkboard.core.Quantity.Kind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates a random but never-nonsensical puzzle:
 * <ol>
 *   <li>pick a skeleton (A = (B·C)/D, …);</li>
 *   <li>freeze the target A plus 1-5 random RHS quantities;</li>
 *   <li>brute-force the remaining '?' slots over the whole catalogue;</li>
 *   <li>if it cannot reach 90 %, grow the tree (up to 4 expansions);</li>
 *   <li>if it still cannot, throw the whole thing away and start over.</li>
 * </ol>
 */
public final class GameSolver {

    private GameSolver() {
    }

    public record Puzzle(
            Expr expr,
            Quantity target,
            List<String> lockedSlotIds,
            Map<String, String> sampleSolution,
            double bestScore,
            int expansionsDone,
            int difficulty,
            String title,
            String description
    ) {
    }

    private static final Random RNG = new Random();

    private static final List<Quantity> TARGET_POOL = Quantities.ALL.stream()
            .filter(q -> !q.vec().isZero()
                    && q.category() != Category.NUMBERS
                    && q.category() != Category.CONSTANTS)
            .toList();

    private static final List<Quantity> RHS_POOL = Quantities.ALL.stream()
            .filter(q -> q.category() != Category.NUMBERS && q.category() != Category.CONSTANTS)
            .toList();

    private static final List<Quantity> SOLVER_POOL = Quantities.ALL.stream()
            .filter(q -> q.kind() != Kind.NUMBER || q.id().equals("num_1") || q.id().equals("num_2"))
            .toList();

    // ───────────────────────── skeletons ─────────────────────────

    private interface Skeleton {
        Expr build();

        String name();
    }

    private static final List<Skeleton> SKELETONS = List.of(
            skeleton("A = (B \u00d7 C) / D", () -> Expr.Eq.of(
                    Expr.Slot.empty(),
                    Expr.Op.of(Expr.OpKind.DIV,
                            Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.empty(), Expr.Slot.empty()),
                            Expr.Slot.empty()))),
            skeleton("A = (B \u00d7 C) \u00d7 D", () -> Expr.Eq.of(
                    Expr.Slot.empty(),
                    Expr.Op.of(Expr.OpKind.MUL,
                            Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.empty(), Expr.Slot.empty()),
                            Expr.Slot.empty()))),
            skeleton("A = B \u00d7 C", () -> Expr.Eq.of(
                    Expr.Slot.empty(),
                    Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.empty(), Expr.Slot.empty()))),
            skeleton("A = B / C", () -> Expr.Eq.of(
                    Expr.Slot.empty(),
                    Expr.Op.of(Expr.OpKind.DIV, Expr.Slot.empty(), Expr.Slot.empty())))
    );

    private static Skeleton skeleton(String name, java.util.function.Supplier<Expr> supplier) {
        return new Skeleton() {
            @Override
            public Expr build() {
                return supplier.get();
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    // ───────────────────────── solver ─────────────────────────

    private record SolverResult(boolean solvable, double score, Map<String, String> assignments) {
    }

    private static SolverResult solve(Expr expr, List<String> emptySlotIds) {
        if (emptySlotIds.isEmpty()) {
            Double s = Evaluator.analyze(expr).sD;
            double score = s == null ? 0 : s;
            return new SolverResult(score >= 90, score, Map.of());
        }

        if (emptySlotIds.size() == 1) {
            String slotId = emptySlotIds.get(0);
            DimVec target = Evaluator.analyze(expr).required.get(slotId);
            if (target == null) return new SolverResult(false, 0, Map.of());

            double best = 0;
            String bestId = null;
            for (Quantity q : SOLVER_POOL) {
                double score = DimVec.dimScore(target, q.vec());
                if (score > best) {
                    best = score;
                    bestId = q.id();
                    if (score >= 99.9) break;
                }
            }
            Map<String, String> a = bestId == null ? Map.of() : Map.of(slotId, bestId);
            return new SolverResult(best >= 90, best, a);
        }

        if (emptySlotIds.size() == 2) {
            String s1 = emptySlotIds.get(0);
            String s2 = emptySlotIds.get(1);
            double best = 0;
            String[] bestPair = null;

            for (Quantity q1 : SOLVER_POOL) {
                Expr partial = Manipulate.setSlotQuantity(expr, s1, q1.id());
                DimVec req2 = Evaluator.analyze(partial).required.get(s2);
                if (req2 == null) continue;
                for (Quantity q2 : SOLVER_POOL) {
                    double score = DimVec.dimScore(req2, q2.vec());
                    if (score > best) {
                        best = score;
                        bestPair = new String[]{q1.id(), q2.id()};
                        if (best >= 99.9) {
                            return new SolverResult(true, best, Map.of(s1, q1.id(), s2, q2.id()));
                        }
                    }
                }
            }
            Map<String, String> a = bestPair == null ? Map.of() : Map.of(s1, bestPair[0], s2, bestPair[1]);
            return new SolverResult(best >= 90, best, a);
        }

        // 3+ holes: greedy fill, slot by slot, re-deriving the requirement each time
        Expr cur = expr;
        Map<String, String> assignments = new HashMap<>();
        for (String sId : emptySlotIds) {
            DimVec req = Evaluator.analyze(cur).required.get(sId);
            if (req == null) continue;
            double best = -1;
            String chosen = SOLVER_POOL.get(0).id();
            for (Quantity q : SOLVER_POOL) {
                double s = DimVec.dimScore(req, q.vec());
                if (s > best) {
                    best = s;
                    chosen = q.id();
                }
            }
            assignments.put(sId, chosen);
            cur = Manipulate.setSlotQuantity(cur, sId, chosen);
        }
        Double fin = Evaluator.analyze(cur).sD;
        double score = fin == null ? 0 : fin;
        return new SolverResult(score >= 90, score, assignments);
    }

    // ───────────────────────── expansion ─────────────────────────

    private static Expr expand(Expr expr) {
        List<Manipulate.SlotInfo> slots = Manipulate.collectSlots(expr);
        List<Manipulate.SlotInfo> free = new ArrayList<>();
        for (Manipulate.SlotInfo s : slots) if (!s.filled() && !s.locked()) free.add(s);
        if (free.isEmpty()) {
            for (Manipulate.SlotInfo s : slots) if (!s.locked()) free.add(s);
        }
        if (free.isEmpty()) return expr;
        Manipulate.SlotInfo target = free.get(RNG.nextInt(free.size()));
        Manipulate.Direction dir = RNG.nextBoolean() ? Manipulate.Direction.RIGHT : Manipulate.Direction.BOTTOM;
        return Manipulate.wrapNode(expr, target.id(), dir, null);
    }

    // ───────────────────────── generation ─────────────────────────

    /**
     * @param difficulty 1 = compact, 2 = bigger skeleton + more locks,
     *                   3 = monstrous, and every frozen node has weight ≥ 2
     */
    public static Puzzle generate(int difficulty) {
        int level = Math.max(1, Math.min(3, difficulty));
        final int MAX_ATTEMPTS = 50;
        final int MAX_EXPANSIONS = 4;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Skeleton skeleton = SKELETONS.get(RNG.nextInt(SKELETONS.size()));
            Expr expr = skeleton.build();

            // Pre-grow the skeleton for higher levels
            int preGrow = switch (level) {
                case 2 -> 1 + RNG.nextInt(3);   // +1..3 slots
                case 3 -> 3 + RNG.nextInt(3);   // +3..5 slots
                default -> 0;
            };
            for (int i = 0; i < preGrow; i++) expr = expand(expr);

            if (!(expr instanceof Expr.Eq eq0)) continue;

            List<Quantity> targetPool = level == 3
                    ? TARGET_POOL.stream().filter(q -> q.weight() >= 2).toList() : TARGET_POOL;
            List<Quantity> rhsPool = level == 3
                    ? RHS_POOL.stream().filter(q -> q.weight() >= 2).toList() : RHS_POOL;
            if (targetPool.isEmpty() || rhsPool.isEmpty()) continue;

            Quantity target = targetPool.get(RNG.nextInt(targetPool.size()));

            List<Manipulate.SlotInfo> lhsSlots = Manipulate.collectSlots(eq0.left());
            if (lhsSlots.isEmpty()) continue;
            String lhsSlotId = lhsSlots.get(0).id();
            expr = Manipulate.setSlotQuantity(expr, lhsSlotId, target.id());

            if (!(expr instanceof Expr.Eq eq1)) continue;

            List<Manipulate.SlotInfo> rhsSlots = new ArrayList<>(Manipulate.collectSlots(eq1.right()));
            Collections.shuffle(rhsSlots, RNG);

            int rhsToFix = switch (level) {
                case 1 -> (rhsSlots.size() >= 3 && RNG.nextDouble() < 0.4) ? 2 : 1;
                case 2 -> Math.min(Math.max(rhsSlots.size() - 1, 1), 2 + RNG.nextInt(2));
                default -> Math.min(Math.max(rhsSlots.size() - 1, 1), 3 + RNG.nextInt(3));
            };

            Set<String> lockedIds = new LinkedHashSet<>();
            lockedIds.add(lhsSlotId);
            for (int i = 0; i < rhsToFix && i < rhsSlots.size(); i++) {
                Quantity pick = rhsPool.get(RNG.nextInt(rhsPool.size()));
                expr = Manipulate.setSlotQuantity(expr, rhsSlots.get(i).id(), pick.id());
                lockedIds.add(rhsSlots.get(i).id());
            }

            expr = Manipulate.markLocked(expr, lockedIds);

            // Test solvability, growing the tree when necessary
            Expr working = expr;
            for (int expansions = 0; expansions <= MAX_EXPANSIONS; expansions++) {
                List<String> empties = new ArrayList<>();
                for (Manipulate.SlotInfo s : Manipulate.collectSlots(working)) {
                    if (!s.filled() && !s.locked()) empties.add(s.id());
                }

                SolverResult solution = solve(working, empties);
                if (solution.solvable() && solution.score() >= 90) {
                    String title = "Загадка: " + target.nameRu() + " (" + target.symbol() + ")";
                    String desc = "Цель: " + target.symbol() + " [" + target.unit() + "] · вес "
                            + target.weight() + " · уровень " + level;
                    return new Puzzle(working, target, new ArrayList<>(lockedIds),
                            solution.assignments(), solution.score(), expansions, level, title, desc);
                }
                if (expansions < MAX_EXPANSIONS) working = expand(working);
            }
        }

        return fallback(level);
    }

    /** Guaranteed-solvable safety net: F = (a · ?) / ?. */
    private static Puzzle fallback(int level) {
        Expr expr = Expr.Eq.of(
                Expr.Slot.empty(),
                Expr.Op.of(Expr.OpKind.DIV,
                        Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.empty(), Expr.Slot.empty()),
                        Expr.Slot.empty()));

        Expr.Eq eq = (Expr.Eq) expr;
        List<Manipulate.SlotInfo> l = Manipulate.collectSlots(eq.left());
        List<Manipulate.SlotInfo> r = Manipulate.collectSlots(eq.right());

        expr = Manipulate.setSlotQuantity(expr, l.get(0).id(), "force");
        expr = Manipulate.setSlotQuantity(expr, r.get(0).id(), "accel");

        Set<String> locked = new LinkedHashSet<>(Set.of(l.get(0).id(), r.get(0).id()));
        expr = Manipulate.markLocked(expr, locked);

        Map<String, String> sample = new HashMap<>();
        sample.put(r.get(1).id(), "mass");
        sample.put(r.get(2).id(), "num_1");

        Quantity force = Quantities.get("force");
        return new Puzzle(expr, force, new ArrayList<>(locked), sample, 100.0, 0, level,
                "Загадка: " + force.nameRu() + " (" + force.symbol() + ")",
                "Цель: F [Н]. Соберите 7D резонанс ≥ 90 %.");
    }

    /** Free-play skeletons for the sandbox menu. */
    public static Expr sandbox(int index) {
        List<java.util.function.Supplier<Expr>> list = List.of(
                () -> Expr.Eq.of(Expr.Slot.empty(), Expr.Slot.empty()),
                () -> Expr.Eq.of(Expr.Slot.empty(),
                        Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.empty(), Expr.Slot.empty())),
                () -> Expr.Eq.of(Expr.Slot.empty(),
                        Expr.Op.of(Expr.OpKind.DIV, Expr.Slot.empty(), Expr.Slot.empty())),
                () -> Expr.Eq.of(Expr.Slot.empty(),
                        Expr.Op.of(Expr.OpKind.DIV,
                                Expr.Op.of(Expr.OpKind.MUL, Expr.Slot.empty(), Expr.Slot.empty()),
                                Expr.Slot.empty())),
                () -> Expr.Eq.of(Expr.Slot.empty(),
                        Expr.Op.of(Expr.OpKind.ADD, Expr.Slot.empty(), Expr.Slot.empty()))
        );
        return list.get(Math.floorMod(index, list.size())).get();
    }

    public static Set<String> lockedSet(Puzzle p) {
        return new HashSet<>(p.lockedSlotIds());
    }
}
