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
 * and infinite post-game mode, retaining solver/hint calculation logic.
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

    public static Puzzle generateInfinite(int stageIndex, long worldSeed) {
        int stage = stageIndex + 1; // Stage 17, 18, 19...
        String titleRu = "Бесконечный резонанс (Стадия " + stage + ")";

        return generateTargetedPuzzle(titleRu, stageIndex, 15, 25, 2, 2, worldSeed);
    }

    public static Puzzle generateDiscovery(DiscoveryDef def, long worldSeed) {
        String titleRu = "Открытие " + (def.index() + 1) + "/16: " + def.titleRu();

        // For Discovery 16 (index 15), use the targeted 10-13 node puzzle generator directly
        if (def.index() == 15) {
            return generateTargetedPuzzle(titleRu, 15, 10, 13, 1, 2, worldSeed);
        }

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
            Quantity target = targetPool.get(rng.nextInt(targetPool.size()));
            if (def.themeBoostTargetId() != null) {
                Quantity boosted = Quantities.get(def.themeBoostTargetId());
                if (boosted != null && (attempt < 80 || rng.nextBoolean())) {
                    target = boosted;
                }
            }

            int minK = Math.max(2, def.minNodes());
            int maxK = Math.max(minK, def.maxNodes());
            int fixedCount = minK == maxK ? minK : minK + rng.nextInt(maxK - minK + 1);

            int rhsFixedCount = Math.max(1, fixedCount - 1);

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

            int denFixedCount = (fixedRhs.size() >= 2 && rng.nextDouble() < 0.70)
                    ? 1 + rng.nextInt(fixedRhs.size() - 1)
                    : 0;

            int numFixedCount = fixedRhs.size() - denFixedCount;
            List<Quantity> numFixed = new ArrayList<>(fixedRhs.subList(0, numFixedCount));
            List<Quantity> denFixed = denFixedCount > 0 ? new ArrayList<>(fixedRhs.subList(numFixedCount, fixedRhs.size())) : List.of();

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

                    List<String> lockedSlotIds = new ArrayList<>();
                    List<String> emptySlotIds = new ArrayList<>();
                    for (Manipulate.SlotInfo s : Manipulate.collectSlots(expr)) {
                        if (s.locked()) {
                            lockedSlotIds.add(s.id());
                        } else if (!s.filled()) {
                            emptySlotIds.add(s.id());
                        }
                    }

                    SolverResult solution = solve(expr, emptySlotIds);
                    if (!solution.solvable() || solution.score() < 99.9) continue;

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

                    if (numQuantities.contains(target.id()) || denQuantities.contains(target.id())) {
                        continue;
                    }

                    boolean hasCancellation = false;
                    for (String qId : numQuantities) {
                        if (denQuantities.contains(qId)) {
                            hasCancellation = true;
                            break;
                        }
                    }
                    if (hasCancellation) continue;

                    boolean tierConstraintsMet = checkTierRequirements(def, expr, solution.assignments());

                    String desc = "Цель: " + target.symbol() + " [" + target.unit() + "] · " + target.nameRu();

                    Puzzle p = new Puzzle(expr, target, lockedSlotIds, solution.assignments(),
                            solution.score(), 0, def.index() + 1, titleRu, desc);

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

        return generateTargetedPuzzle(titleRu, def.index(), def.minNodes(), def.maxNodes(), def.minTier4Req(), def.minTier99Req(), worldSeed);
    }

    public static Puzzle generateTargetedPuzzle(String titleRu, int index,
                                                int minNodes, int maxNodes,
                                                int reqT4, int reqT99,
                                                long worldSeed) {
        long seed = worldSeed ^ ((long) index * 0x9E3779B97F4A7C15L);
        Random rng = new Random(seed);

        List<Quantity> t4Pool = Quantities.ALL.stream().filter(q -> q.tier() == 4).toList();
        List<Quantity> t99Pool = Quantities.ALL.stream().filter(q -> q.tier() == 99).toList();
        List<Quantity> compositePool = Quantities.ALL.stream()
                .filter(q -> q.tier() >= 1 && q.tier() <= 3 && !q.vec().isZero())
                .toList();

        List<Quantity> targetPool = new ArrayList<>(t99Pool);
        targetPool.addAll(t4Pool);

        for (int attempt = 0; attempt < 2000; attempt++) {
            Quantity target = targetPool.get(rng.nextInt(targetPool.size()));

            List<Quantity> numList = new ArrayList<>();
            List<Quantity> denList = new ArrayList<>();

            for (int i = 0; i < reqT4; i++) {
                Quantity q = t4Pool.get(rng.nextInt(t4Pool.size()));
                if (numList.size() <= denList.size()) numList.add(q);
                else denList.add(q);
            }
            for (int i = 0; i < reqT99; i++) {
                Quantity q = t99Pool.get(rng.nextInt(t99Pool.size()));
                if (numList.size() <= denList.size()) numList.add(q);
                else denList.add(q);
            }

            int extraCnt = 1 + rng.nextInt(3);
            for (int i = 0; i < extraCnt; i++) {
                Quantity q = compositePool.get(rng.nextInt(compositePool.size()));
                if (numList.size() <= denList.size()) numList.add(q);
                else denList.add(q);
            }

            DimVec net = DimVec.ZERO;
            for (Quantity q : numList) net = net.add(q.vec());
            for (Quantity q : denList) net = net.sub(q.vec());

            DimVec diff = target.vec().sub(net);

            List<Quantity> siMap = List.of(
                    Quantities.get("length"),
                    Quantities.get("mass"),
                    Quantities.get("time"),
                    Quantities.get("current"),
                    Quantities.get("temperature"),
                    Quantities.get("amount"),
                    Quantities.get("luminous")
            );

            for (int i = 0; i < DimVec.SIZE; i++) {
                int val = (int) Math.round(diff.get(i));
                Quantity baseQ = siMap.get(i);
                if (val > 0) {
                    for (int c = 0; c < val; c++) numList.add(baseQ);
                } else if (val < 0) {
                    for (int c = 0; c < -val; c++) denList.add(baseQ);
                }
            }

            int totalItems = numList.size() + denList.size();
            int totalNodes = 1 + totalItems + Math.max(0, numList.size() - 1) + Math.max(0, denList.size() - 1) + (denList.isEmpty() ? 0 : 1);

            if (totalNodes >= minNodes && totalNodes <= maxNodes) {
                Expr numExpr = buildProductFromQuantities(numList);
                Expr denExpr = denList.isEmpty() ? null : buildProductFromQuantities(denList);
                Expr rhsExpr = denExpr == null ? numExpr : Expr.Op.of(Expr.OpKind.DIV, numExpr, denExpr);

                Expr.Slot lhsSlot = new Expr.Slot(Expr.nid("s"), target.id(), true, false);
                Expr fullExpr = Expr.Eq.of(lhsSlot, rhsExpr);

                List<Manipulate.SlotInfo> allSlots = Manipulate.collectSlots(fullExpr);
                List<Manipulate.SlotInfo> rhsSlots = allSlots.stream().filter(s -> !s.id().equals(lhsSlot.id())).toList();

                Map<String, String> sampleSolution = new HashMap<>();
                int holes = Math.min(2, Math.max(1, rhsSlots.size() / 3));
                Set<String> emptyIds = new HashSet<>();
                for (int h = 0; h < holes && h < rhsSlots.size(); h++) {
                    Manipulate.SlotInfo info = rhsSlots.get(h);
                    emptyIds.add(info.id());
                    sampleSolution.put(info.id(), info.quantityId());
                    fullExpr = Manipulate.setSlotQuantity(fullExpr, info.id(), null);
                }

                Set<String> lockedIds = new HashSet<>();
                lockedIds.add(lhsSlot.id());
                for (Manipulate.SlotInfo info : rhsSlots) {
                    if (!emptyIds.contains(info.id())) {
                        lockedIds.add(info.id());
                    }
                }

                fullExpr = Manipulate.markLocked(fullExpr, lockedIds);

                String desc = "Цель: " + target.symbol() + " [" + target.unit() + "] · " + target.nameRu();
                return new Puzzle(fullExpr, target, new ArrayList<>(lockedIds), sampleSolution, 100.0, 0, index + 1, titleRu, desc);
            }
        }

        // Guaranteed fallback if exact range isn't hit
        Quantity target = targetPool.get(rng.nextInt(targetPool.size()));
        Expr.Slot lhsSlot = new Expr.Slot(Expr.nid("s"), target.id(), true, false);
        Expr.Slot rhsSlot = Expr.Slot.empty();
        Expr fullExpr = Expr.Eq.of(lhsSlot, rhsSlot);
        Map<String, String> sampleSolution = Map.of(rhsSlot.id(), target.id());
        return new Puzzle(fullExpr, target, List.of(lhsSlot.id()), sampleSolution, 100.0, 0, index + 1, titleRu, "Цель: " + target.symbol());
    }

    private static Expr buildProductFromQuantities(List<Quantity> list) {
        if (list.isEmpty()) return Expr.Slot.empty();
        Expr result = new Expr.Slot(Expr.nid("s"), list.get(0).id(), true, false);
        for (int i = 1; i < list.size(); i++) {
            Expr slot = new Expr.Slot(Expr.nid("s"), list.get(i).id(), true, false);
            result = Expr.Op.of(Expr.OpKind.MUL, result, slot);
        }
        return result;
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

        return generateTargetedPuzzle("Загадка", 0, 4, 8, 0, 0, 0);
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
