package com.gonzotech.chalkboard.core;

import com.gonzotech.chalkboard.core.Quantity.Category;
import com.gonzotech.chalkboard.core.Quantity.Kind;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

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

    // ───────────────────────── SHA cycle seed derivation ─────────────────────────

    public static long deriveCycleSeed(long worldSeed, int stageIndex, int cycle) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = worldSeed + ":" + stageIndex + ":" + cycle;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.wrap(hash);
            return buffer.getLong();
        } catch (Exception e) {
            return worldSeed ^ ((long) stageIndex * 0x9E3779B97F4A7C15L) ^ ((long) cycle * 0xBF58476D1CE4E5B9L);
        }
    }

    // ───────────────────────── generation ─────────────────────────

    public static Puzzle generateInfinite(int stageIndex, long worldSeed) {
        return generatePuzzleByAlgorithm(stageIndex, null, worldSeed);
    }

    public static Puzzle generateDiscovery(DiscoveryDef def, long worldSeed) {
        return generatePuzzleByAlgorithm(def.index(), def, worldSeed);
    }

    public static Puzzle generateTargetedPuzzle(String titleRu, int index,
                                                int minNodes, int maxNodes,
                                                int reqT4, int reqT99,
                                                long worldSeed) {
        DiscoveryDef def = (index >= 0 && index < 16) ? DiscoveryDef.get(index) : null;
        return generatePuzzleByAlgorithm(index, def, worldSeed);
    }

    public static Puzzle generatePuzzleByAlgorithm(int stageIndex, DiscoveryDef def, long worldSeed) {
        boolean isInfinite = (stageIndex >= 16);
        int stageNumber = stageIndex + 1;

        String titleRu = isInfinite
                ? "Бесконечный резонанс (Стадия " + stageNumber + ")"
                : "Открытие " + stageNumber + "/16: " + def.titleRu();

        int minNodes = isInfinite ? 15 : (def != null ? def.minNodes() : 4);
        int maxNodes = isInfinite ? 25 : (def != null ? def.maxNodes() : 8);

        int reqT2 = (!isInfinite && def != null) ? def.minTier2Req() : 0;
        int reqT3 = (!isInfinite && def != null) ? def.minTier3Req() : 0;
        int reqT4 = isInfinite ? 2 : (def != null ? def.minTier4Req() : 0);
        int reqT99 = isInfinite ? 2 : (def != null ? def.minTier99Req() : 0);

        // Candidate pool for LHS
        List<Quantity> targetPool;
        if (isInfinite) {
            targetPool = Quantities.ALL.stream()
                    .filter(q -> !q.vec().isZero()
                            && q.category() != Category.NUMBERS
                            && q.category() != Category.CONSTANTS
                            && (q.tier() == 4 || q.tier() == 99 || q.tier() == 3))
                    .toList();
        } else if (def != null && !def.targetPoolIds().isEmpty()) {
            targetPool = def.targetPoolIds().stream().map(Quantities::get).filter(q -> q != null).toList();
        } else if (def != null) {
            targetPool = Quantities.ALL.stream()
                    .filter(q -> !q.vec().isZero()
                            && q.category() != Category.NUMBERS
                            && q.category() != Category.CONSTANTS
                            && def.allowedLhsTiers().contains(q.tier()))
                    .toList();
        } else {
            targetPool = TARGET_POOL;
        }
        if (targetPool.isEmpty()) targetPool = TARGET_POOL;

        // Candidate pool for RHS
        List<Quantity> rhsPool;
        if (isInfinite) {
            rhsPool = RHS_POOL;
        } else if (def != null) {
            rhsPool = Quantities.ALL.stream()
                    .filter(q -> q.category() != Category.NUMBERS && q.category() != Category.CONSTANTS
                            && def.allowedRhsTiers().contains(q.tier()))
                    .toList();
            if (rhsPool.isEmpty()) rhsPool = RHS_POOL;
        } else {
            rhsPool = RHS_POOL;
        }

        // Up to 20 deterministic cycles: seed -> +N cycle -> SHA -> gen
        for (int cycle = 0; cycle < 20; cycle++) {
            long cycleSeed = deriveCycleSeed(worldSeed, stageIndex, cycle);
            Random rng = new Random(cycleSeed);

            // Target node count in [minNodes, maxNodes]
            int targetNodes = (minNodes == maxNodes) ? minNodes : minNodes + rng.nextInt(maxNodes - minNodes + 1);
            int numRhsSlots = targetNodes - 1; // 1 LHS slot

            if (numRhsSlots < 2) numRhsSlots = 2;

            Quantity target = targetPool.get(rng.nextInt(targetPool.size()));
            if (!isInfinite && def != null && def.themeBoostTargetId() != null && rng.nextDouble() < 0.6) {
                Quantity boosted = Quantities.get(def.themeBoostTargetId());
                if (boosted != null) target = boosted;
            }

            // Attempt structural variations in this cycle
            for (int varAttempt = 0; varAttempt < 60; varAttempt++) {
                int maxHoles = Math.min(7, numRhsSlots - 1);
                if (maxHoles < 1) maxHoles = 1;

                // Test m holes = 1..7
                for (int m = 1; m <= maxHoles; m++) {
                    int numFixed = numRhsSlots - m;

                    List<Quantity> fixedQuantities = selectFixedRhsQuantities(numFixed, reqT2, reqT3, reqT4, reqT99, rhsPool, rng);
                    if (fixedQuantities == null) continue;

                    int denCount = (numRhsSlots >= 3 && rng.nextDouble() < 0.7) ? 1 + rng.nextInt(numRhsSlots / 2) : 0;
                    int numCount = numRhsSlots - denCount;

                    for (int mNum = 0; mNum <= m; mNum++) {
                        int mDen = m - mNum;
                        if (mNum > numCount || mDen > denCount) continue;

                        int kNum = numCount - mNum;
                        int kDen = denCount - mDen;

                        List<Quantity> numFixed = fixedQuantities.subList(0, kNum);
                        List<Quantity> denFixed = fixedQuantities.subList(kNum, fixedQuantities.size());

                        DimVec netFixed = DimVec.ZERO;
                        for (Quantity q : numFixed) netFixed = netFixed.add(q.vec());
                        for (Quantity q : denFixed) netFixed = netFixed.sub(q.vec());

                        DimVec needed = target.vec().sub(netFixed);

                        List<Quantity> holeQuantities = solveHoles(needed, mNum, mDen, SOLVER_POOL, rng);
                        if (holeQuantities == null) continue;

                        List<Quantity> numHoles = holeQuantities.subList(0, mNum);
                        List<Quantity> denHoles = holeQuantities.subList(mNum, m);

                        List<Quantity> allNum = new ArrayList<>(numFixed);
                        allNum.addAll(numHoles);

                        List<Quantity> allDen = new ArrayList<>(denFixed);
                        allDen.addAll(denHoles);

                        // Rule 1: No target A in RHS
                        boolean targetInRhs = allNum.stream().anyMatch(q -> q.id().equals(target.id()))
                                || allDen.stream().anyMatch(q -> q.id().equals(target.id()));
                        if (targetInRhs) continue;

                        // Rule 2: No direct cross-cancellation between num and den
                        Set<String> numIds = allNum.stream().map(Quantity::id).collect(Collectors.toSet());
                        Set<String> denIds = allDen.stream().map(Quantity::id).collect(Collectors.toSet());
                        boolean hasCrossCancel = numIds.stream().anyMatch(denIds::contains);
                        if (hasCrossCancel) continue;

                        // Rule 3: Tier Requirements
                        int t2 = 0, t3 = 0, t4 = 0, t99 = 0;
                        for (Quantity q : allNum) {
                            if (q.tier() == 2) t2++;
                            if (q.tier() == 3) t3++;
                            if (q.tier() == 4) t4++;
                            if (q.tier() == 99) t99++;
                        }
                        for (Quantity q : allDen) {
                            if (q.tier() == 2) t2++;
                            if (q.tier() == 3) t3++;
                            if (q.tier() == 4) t4++;
                            if (q.tier() == 99) t99++;
                        }
                        if (t2 < reqT2 || t3 < reqT3 || t4 < reqT4 || t99 < reqT99) continue;

                        // Build AST
                        Expr fullExpr = buildEquationExpr(target, numFixed, mNum, denFixed, mDen);
                        if (fullExpr == null) continue;

                        List<String> emptySlotIds = new ArrayList<>();
                        for (Manipulate.SlotInfo s : Manipulate.collectSlots(fullExpr)) {
                            if (!s.filled() && !s.locked()) {
                                emptySlotIds.add(s.id());
                            }
                        }

                        SolverResult solution = solve(fullExpr, emptySlotIds);
                        if (!solution.solvable() || solution.score() < 99.9) continue;

                        List<Manipulate.SlotInfo> allSlots = Manipulate.collectSlots(fullExpr);
                        if (allSlots.size() != targetNodes) continue;

                        Set<String> lockedIds = new HashSet<>();
                        lockedIds.add(allSlots.get(0).id()); // LHS slot
                        for (Manipulate.SlotInfo info : allSlots) {
                            if (!emptySlotIds.contains(info.id())) {
                                lockedIds.add(info.id());
                            }
                        }
                        fullExpr = Manipulate.markLocked(fullExpr, lockedIds);

                        String desc = "Цель: " + target.symbol() + " [" + target.unit() + "] · " + target.nameRu();
                        return new Puzzle(fullExpr, target, new ArrayList<>(lockedIds), solution.assignments(),
                                solution.score(), 0, stageNumber, titleRu, desc);
                    }
                }
            }
        }

        // Fallback after 20 cycles: "L = X"
        Quantity target = targetPool.get(0);
        Expr.Slot lhsSlot = new Expr.Slot(Expr.nid("s"), target.id(), true, false);
        Expr.Slot rhsSlot = Expr.Slot.empty();
        Expr fullExpr = Expr.Eq.of(lhsSlot, rhsSlot);
        Map<String, String> sampleSolution = Map.of(rhsSlot.id(), target.id());
        return new Puzzle(fullExpr, target, List.of(lhsSlot.id()), sampleSolution, 100.0, 0, stageNumber, titleRu, "Цель: " + target.symbol());
    }

    private static List<Quantity> selectFixedRhsQuantities(int count, int reqT2, int reqT3, int reqT4, int reqT99,
                                                           List<Quantity> pool, Random rng) {
        List<Quantity> reqList = new ArrayList<>();
        addTierCandidates(reqList, pool, 99, reqT99);
        addTierCandidates(reqList, pool, 4, reqT4);
        addTierCandidates(reqList, pool, 3, reqT3);
        addTierCandidates(reqList, pool, 2, reqT2);

        if (reqList.size() > count) return null;

        List<Quantity> result = new ArrayList<>(reqList);
        while (result.size() < count) {
            result.add(pool.get(rng.nextInt(pool.size())));
        }
        Collections.shuffle(result, rng);
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

    private static List<Quantity> solveHoles(DimVec needed, int mNum, int mDen, List<Quantity> pool, Random rng) {
        int totalHoles = mNum + mDen;
        if (totalHoles == 0) {
            return needed.isZero() ? List.of() : null;
        }

        if (totalHoles == 1) {
            for (Quantity q : pool) {
                DimVec v = mNum == 1 ? q.vec() : q.vec().scale(-1);
                if (v.equalsVec(needed)) {
                    return List.of(q);
                }
            }
            return null;
        }

        if (totalHoles == 2) {
            List<Quantity> shuffled = new ArrayList<>(pool);
            Collections.shuffle(shuffled, rng);
            for (Quantity q1 : shuffled) {
                DimVec v1 = mNum >= 1 ? q1.vec() : q1.vec().scale(-1);
                DimVec rem = needed.sub(v1);
                int mNumRem = mNum >= 1 ? mNum - 1 : 0;

                for (Quantity q2 : shuffled) {
                    DimVec v2 = mNumRem == 1 ? q2.vec() : q2.vec().scale(-1);
                    if (v2.equalsVec(rem)) {
                        return List.of(q1, q2);
                    }
                }
            }
            return null;
        }

        List<Quantity> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rng);

        int outerCount = totalHoles - 2;
        for (int attempt = 0; attempt < 200; attempt++) {
            List<Quantity> picked = new ArrayList<>();
            DimVec current = DimVec.ZERO;
            int numPicked = 0;
            int denPicked = 0;

            for (int i = 0; i < outerCount; i++) {
                Quantity q = shuffled.get(rng.nextInt(shuffled.size()));
                picked.add(q);
                if (numPicked < mNum) {
                    current = current.add(q.vec());
                    numPicked++;
                } else {
                    current = current.sub(q.vec());
                    denPicked++;
                }
            }

            DimVec rem = needed.sub(current);
            int remNum = mNum - numPicked;
            int remDen = mDen - denPicked;

            for (Quantity q1 : shuffled) {
                DimVec v1 = remNum >= 1 ? q1.vec() : q1.vec().scale(-1);
                DimVec rem2 = rem.sub(v1);
                int remNum2 = remNum >= 1 ? remNum - 1 : 0;

                for (Quantity q2 : shuffled) {
                    DimVec v2 = remNum2 == 1 ? q2.vec() : q2.vec().scale(-1);
                    if (v2.equalsVec(rem2)) {
                        List<Quantity> numHoles = new ArrayList<>();
                        List<Quantity> denHoles = new ArrayList<>();

                        for (int k = 0; k < outerCount; k++) {
                            if (k < mNum) numHoles.add(picked.get(k));
                            else denHoles.add(picked.get(k));
                        }
                        if (remNum >= 1) numHoles.add(q1); else denHoles.add(q1);
                        if (remNum2 == 1) numHoles.add(q2); else denHoles.add(q2);

                        List<Quantity> ordered = new ArrayList<>(numHoles);
                        ordered.addAll(denHoles);
                        return ordered;
                    }
                }
            }
        }

        return null;
    }

    private static Expr buildEquationExpr(Quantity target,
                                          List<Quantity> numFixed, int mNum,
                                          List<Quantity> denFixed, int mDen) {
        List<Expr> numSlots = new ArrayList<>();
        for (Quantity q : numFixed) {
            numSlots.add(new Expr.Slot(Expr.nid("s"), q.id(), true, false));
        }
        for (int i = 0; i < mNum; i++) {
            numSlots.add(Expr.Slot.empty());
        }

        if (numSlots.isEmpty()) return null;

        Expr numExpr = numSlots.get(0);
        for (int i = 1; i < numSlots.size(); i++) {
            numExpr = Expr.Op.of(Expr.OpKind.MUL, numExpr, numSlots.get(i));
        }

        List<Expr> denSlots = new ArrayList<>();
        for (Quantity q : denFixed) {
            denSlots.add(new Expr.Slot(Expr.nid("s"), q.id(), true, false));
        }
        for (int i = 0; i < mDen; i++) {
            denSlots.add(Expr.Slot.empty());
        }

        Expr denExpr = null;
        if (!denSlots.isEmpty()) {
            denExpr = denSlots.get(0);
            for (int i = 1; i < denSlots.size(); i++) {
                denExpr = Expr.Op.of(Expr.OpKind.MUL, denExpr, denSlots.get(i));
            }
        }

        Expr rhsExpr = denExpr == null ? numExpr : Expr.Op.of(Expr.OpKind.DIV, numExpr, denExpr);
        Expr.Slot lhsSlot = new Expr.Slot(Expr.nid("s"), target.id(), true, false);

        return Expr.Eq.of(lhsSlot, rhsExpr);
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
