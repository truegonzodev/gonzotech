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
 * Generates seed-deterministic puzzles for the 16 gameplay discoveries
 * and retains solver/hint calculation logic for future mechanics.
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

    public record SolverResult(boolean solvable, double score, Map<String, String> assignments) {
    }

    public static SolverResult solve(Expr expr, List<String> emptySlotIds) {
        if (emptySlotIds.isEmpty()) {
            Double s = Evaluator.analyze(expr).sD;
            double score = s == null ? 0 : s;
            return new SolverResult(score >= 99.9, score, Map.of());
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
            return new SolverResult(best >= 99.9, best, a);
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
            return new SolverResult(best >= 99.9, best, a);
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
        return new SolverResult(score >= 99.9, score, assignments);
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

    public static Puzzle generateDiscovery(DiscoveryDef def, long worldSeed) {
        long seed = worldSeed ^ ((long) def.index() * 0x9E3779B97F4A7C15L);
        Random rng = new Random(seed);

        // 1. Target candidate pool for LHS
        List<Quantity> targetPool;
        if (!def.targetPoolIds().isEmpty()) {
            targetPool = def.targetPoolIds().stream().map(Quantities::get).filter(q -> q != null).toList();
        } else {
            targetPool = Quantities.ALL.stream()
                    .filter(q -> !q.vec().isZero()
                            && q.category() != Category.NUMBERS
                            && q.category() != Category.CONSTANTS
                            && def.allowedLhsTiers().contains(q.tier()))
                    .toList();
        }
        if (targetPool.isEmpty()) targetPool = TARGET_POOL;

        // 2. Candidate pool for RHS fixed quantities
        List<Quantity> rhsPool = Quantities.ALL.stream()
                .filter(q -> q.category() != Category.NUMBERS && q.category() != Category.CONSTANTS
                        && def.allowedRhsTiers().contains(q.tier()))
                .toList();
        if (rhsPool.isEmpty()) rhsPool = RHS_POOL;

        Puzzle bestPuzzle = null;
        double bestPuzzleScore = -1.0;

        for (int attempt = 0; attempt < 300; attempt++) {
            // Target (LHS)
            Quantity target = targetPool.get(rng.nextInt(targetPool.size()));
            if (def.themeBoostTargetId() != null) {
                Quantity boosted = Quantities.get(def.themeBoostTargetId());
                if (boosted != null && (attempt < 80 || rng.nextBoolean())) {
                    target = boosted;
                }
            }

            // Total fixed quantity count K in [minNodes, maxNodes]
            int minK = Math.max(2, def.minNodes());
            int maxK = Math.max(minK, def.maxNodes());
            int fixedCount = minK == maxK ? minK : minK + rng.nextInt(maxK - minK + 1);

            // Fixed RHS quantities count = fixedCount - 1
            int rhsFixedCount = Math.max(1, fixedCount - 1);

            // Select fixed RHS quantities, prioritizing required tiers
            List<Quantity> tierReqList = new ArrayList<>();
            addTierCandidates(tierReqList, rhsPool, 99, def.minTier99Req());
            addTierCandidates(tierReqList, rhsPool, 4, def.minTier4Req());
            addTierCandidates(tierReqList, rhsPool, 3, def.minTier3Req());
            addTierCandidates(tierReqList, rhsPool, 2, def.minTier2Req());

            List<Quantity> fixedRhs = new ArrayList<>();
            for (int i = 0; i < rhsFixedCount; i++) {
                if (!tierReqList.isEmpty()) {
                    fixedRhs.add(tierReqList.remove(0));
                } else {
                    fixedRhs.add(rhsPool.get(rng.nextInt(rhsPool.size())));
                }
            }
            Collections.shuffle(fixedRhs, rng);

            // Random skeleton division into Numerator and Denominator:
            int denFixedCount = (fixedRhs.size() >= 2 && rng.nextDouble() < 0.70)
                    ? 1 + rng.nextInt(fixedRhs.size() - 1)
                    : 0;

            int numFixedCount = fixedRhs.size() - denFixedCount;
            List<Quantity> numFixed = new ArrayList<>(fixedRhs.subList(0, numFixedCount));
            List<Quantity> denFixed = denFixedCount > 0 ? new ArrayList<>(fixedRhs.subList(numFixedCount, fixedRhs.size())) : List.of();

            // Now increment empty slots count (numHoles) from 1 to 4 to find minimal holes with 100% match
            for (int numHoles = 1; numHoles <= 4; numHoles++) {
                for (int numHolesInNum = 0; numHolesInNum <= numHoles; numHolesInNum++) {
                    int numHolesInDen = numHoles - numHolesInNum;

                    Expr numExpr = buildProduct(numFixed, numHolesInNum);
                    Expr denExpr = (denFixed.isEmpty() && numHolesInDen == 0) ? null : buildProduct(denFixed, numHolesInDen);

                    Expr rhsExpr;
                    if (denExpr == null) {
                        rhsExpr = numExpr;
                    } else {
                        rhsExpr = Expr.Op.of(Expr.OpKind.DIV, numExpr, denExpr);
                    }

                    Expr.Slot lhsSlot = new Expr.Slot(Expr.nid("s"), target.id(), true, false);
                    Expr expr = Expr.Eq.of(lhsSlot, rhsExpr);

                    // Collect locked slot IDs and empty slot IDs
                    List<String> lockedSlotIds = new ArrayList<>();
                    List<String> emptySlotIds = new ArrayList<>();
                    for (Manipulate.SlotInfo s : Manipulate.collectSlots(expr)) {
                        if (s.locked()) {
                            lockedSlotIds.add(s.id());
                        } else if (!s.filled()) {
                            emptySlotIds.add(s.id());
                        }
                    }

                    // Solve candidate
                    SolverResult solution = solve(expr, emptySlotIds);
                    if (!solution.solvable() || solution.score() < 99.9) continue;

                    // ANTI-REDUNDANCY / NO CANCELLATION CHECKS:
                    // Collect numerator and denominator quantity IDs
                    Set<String> numQuantities = new HashSet<>();
                    Set<String> denQuantities = new HashSet<>();

                    for (Quantity q : numFixed) numQuantities.add(q.id());
                    for (Quantity q : denFixed) denQuantities.add(q.id());

                    for (Map.Entry<String, String> entry : solution.assignments().entrySet()) {
                        String slotId = entry.getKey();
                        String qId = entry.getValue();
                        if (qId == null) continue;

                        if (isSlotInNumerator(expr, slotId)) {
                            numQuantities.add(qId);
                        } else {
                            denQuantities.add(qId);
                        }
                    }

                    // Check 1: Target must NOT appear on RHS
                    if (numQuantities.contains(target.id()) || denQuantities.contains(target.id())) {
                        continue;
                    }

                    // Check 2: No quantity should appear in BOTH numerator and denominator (no Iv/Iv cancellation)
                    boolean hasCancellation = false;
                    for (String qId : numQuantities) {
                        if (denQuantities.contains(qId)) {
                            hasCancellation = true;
                            break;
                        }
                    }
                    if (hasCancellation) continue;

                    // Check tier constraints
                    boolean tierConstraintsMet = checkTierRequirements(def, expr, solution.assignments());

                    String title = "Открытие " + (def.index() + 1) + "/16: " + def.titleRu();
                    String desc = "Цель: " + target.symbol() + " [" + target.unit() + "] · " + target.nameRu();

                    Puzzle p = new Puzzle(expr, target, lockedSlotIds, solution.assignments(),
                            solution.score(), 0, def.index() + 1, title, desc);

                    if (tierConstraintsMet) {
                        return p;
                    }

                    if (solution.score() > bestPuzzleScore) {
                        bestPuzzleScore = solution.score();
                        bestPuzzle = p;
                    }
                }
            }
        }

        if (bestPuzzle != null) {
            return bestPuzzle;
        }

        return fallback(def.index() + 1);
    }

    private static boolean isSlotInNumerator(Expr expr, String slotId) {
        if (expr instanceof Expr.Eq eq && eq.right() instanceof Expr.Op divOp && divOp.op() == Expr.OpKind.DIV) {
            return containsSlot(divOp.left(), slotId);
        }
        return true;
    }

    private static boolean containsSlot(Expr tree, String slotId) {
        if (tree == null) return false;
        if (tree.id().equals(slotId)) return true;
        return switch (tree) {
            case Expr.Op o -> containsSlot(o.left(), slotId) || containsSlot(o.right(), slotId);
            case Expr.Pow p -> containsSlot(p.base(), slotId);
            default -> false;
        };
    }

    private static Expr buildProduct(List<Quantity> fixedQuantities, int emptySlotsCount) {
        List<Expr> slots = new ArrayList<>();
        for (Quantity q : fixedQuantities) {
            slots.add(new Expr.Slot(Expr.nid("s"), q.id(), true, false));
        }
        for (int i = 0; i < emptySlotsCount; i++) {
            slots.add(Expr.Slot.empty());
        }
        if (slots.isEmpty()) {
            return Expr.Slot.empty();
        }
        Expr result = slots.get(0);
        for (int i = 1; i < slots.size(); i++) {
            result = Expr.Op.of(Expr.OpKind.MUL, result, slots.get(i));
        }
        return result;
    }

    private static void addTierCandidates(List<Quantity> out, List<Quantity> pool, int tier, int count) {
        if (count <= 0) return;
        List<Quantity> matching = pool.stream().filter(q -> q.tier() == tier).toList();
        if (matching.isEmpty()) {
            matching = Quantities.ALL.stream().filter(q -> q.tier() == tier).toList();
        }
        for (int i = 0; i < count && !matching.isEmpty(); i++) {
            out.add(matching.get(i % matching.size()));
        }
    }

    private static boolean checkTierRequirements(DiscoveryDef def, Expr expr, Map<String, String> assignments) {
        Map<Integer, Integer> tierCounts = new HashMap<>();
        for (Manipulate.SlotInfo s : Manipulate.collectSlots(expr)) {
            String qId = s.quantityId();
            if (qId == null) {
                qId = assignments.get(s.id());
            }
            if (qId != null) {
                Quantity q = Quantities.get(qId);
                if (q != null) {
                    tierCounts.put(q.tier(), tierCounts.getOrDefault(q.tier(), 0) + 1);
                }
            }
        }

        if (tierCounts.getOrDefault(2, 0) < def.minTier2Req()) return false;
        if (tierCounts.getOrDefault(3, 0) < def.minTier3Req()) return false;
        if (tierCounts.getOrDefault(4, 0) < def.minTier4Req()) return false;
        if (tierCounts.getOrDefault(99, 0) < def.minTier99Req()) return false;

        return true;
    }

    public static Puzzle generate(int difficulty) {
        int level = Math.max(1, Math.min(3, difficulty));
        final int MAX_ATTEMPTS = 50;
        final int MAX_EXPANSIONS = 4;

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Skeleton skeleton = SKELETONS.get(RNG.nextInt(SKELETONS.size()));
            Expr expr = skeleton.build();

            int preGrow = switch (level) {
                case 2 -> 1 + RNG.nextInt(3);
                case 3 -> 3 + RNG.nextInt(3);
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
