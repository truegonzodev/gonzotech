package com.gonzotech.chalkboard.core;

import com.gonzotech.chalkboard.core.Quantity.Category;
import com.gonzotech.chalkboard.core.Quantity.Kind;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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
 * and infinite post-game mode using a Greedy Vector Reduction solver.
 */
public final class GameSolver {

    private static final Logger LOGGER = LogUtils.getLogger();

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
        long startMs = System.currentTimeMillis();
        boolean isInfinite = (stageIndex >= 16);
        int stageNumber = stageIndex + 1;

        String titleRu = isInfinite
                ? "Бесконечный резонанс (Стадия " + stageNumber + ")"
                : "Открытие " + stageNumber + "/16: " + def.titleRu();

        LOGGER.info("[Chalkboard] Generating puzzle for stage {} (isInfinite={}) on worldSeed {}...",
                stageNumber, isInfinite, worldSeed);

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

        // ──────────────── 1. Post-Game Infinite Mode (Stage 17+) ────────────────
        if (isInfinite) {
            long cycleSeed = deriveCycleSeed(worldSeed, stageIndex, 0);
            Random rng = new Random(cycleSeed);

            int targetFixedNodes = 15 + rng.nextInt(11); // 15..25 FIXED nodes [ФИКС]
            int numRhsFixedCount = targetFixedNodes - 1;

            Quantity target = targetPool.get(rng.nextInt(targetPool.size()));

            List<Quantity> fixedQuantities = selectFixedRhsQuantities(numRhsFixedCount, 0, 0, 2, 2, rhsPool, rng);
            if (fixedQuantities == null) {
                fixedQuantities = selectFixedRhsQuantities(numRhsFixedCount, 0, 0, 0, 0, rhsPool, rng);
            }

            int denFixedCount = 1 + rng.nextInt(numRhsFixedCount / 2);
            int numFixedCount = numRhsFixedCount - denFixedCount;

            List<Quantity> numFixed = fixedQuantities.subList(0, numFixedCount);
            List<Quantity> denFixed = fixedQuantities.subList(numFixedCount, fixedQuantities.size());

            Expr fullExpr = buildEquationExpr(target, numFixed, 0, denFixed, 0);

            List<Manipulate.SlotInfo> allSlots = Manipulate.collectSlots(fullExpr);
            Set<String> lockedIds = allSlots.stream().map(Manipulate.SlotInfo::id).collect(Collectors.toSet());
            fullExpr = Manipulate.markLocked(fullExpr, lockedIds);

            long elapsedMs = System.currentTimeMillis() - startMs;
            LOGGER.info("[Chalkboard] Generated Infinite Mode puzzle for stage {} in {} ms (target={}, fixedSlots={}).",
                    stageNumber, elapsedMs, target.id(), allSlots.size());

            String desc = "Цель: " + target.symbol() + " [" + target.unit() + "] · " + target.nameRu();
            return new Puzzle(fullExpr, target, new ArrayList<>(lockedIds), Map.of(),
                    100.0, 0, stageNumber, titleRu, desc);
        }

        // ──────────────── 2. Discoveries 1..16 (Greedy Vector Reduction) ────────────────
        int minFixedNodes = def != null ? def.minNodes() : 4;
        int maxFixedNodes = def != null ? def.maxNodes() : 8;

        int reqT2 = def != null ? def.minTier2Req() : 0;
        int reqT3 = def != null ? def.minTier3Req() : 0;
        int reqT4 = def != null ? def.minTier4Req() : 0;
        int reqT99 = def != null ? def.minTier99Req() : 0;

        for (int cycle = 0; cycle < 20; cycle++) {
            long cycleSeed = deriveCycleSeed(worldSeed, stageIndex, cycle);
            Random rng = new Random(cycleSeed);

            // targetFixedNodes = exact number of FIXED nodes [ФИКС] from table
            int targetFixedNodes = (minFixedNodes == maxFixedNodes)
                    ? minFixedNodes
                    : minFixedNodes + rng.nextInt(maxFixedNodes - minFixedNodes + 1);

            int numRhsFixedCount = Math.max(1, targetFixedNodes - 1); // 1 LHS target A

            Quantity candidateTarget = targetPool.get(rng.nextInt(targetPool.size()));
            if (def != null && def.themeBoostTargetId() != null && rng.nextDouble() < 0.6) {
                Quantity boosted = Quantities.get(def.themeBoostTargetId());
                if (boosted != null) candidateTarget = boosted;
            }
            final Quantity target = candidateTarget;
            final String targetId = target.id();

            for (int varAttempt = 0; varAttempt < 30; varAttempt++) {
                List<Quantity> fixedQuantities = selectFixedRhsQuantities(numRhsFixedCount, reqT2, reqT3, reqT4, reqT99, rhsPool, rng);
                if (fixedQuantities == null) continue;

                int denFixedCount = (numRhsFixedCount >= 2 && rng.nextDouble() < 0.7) ? 1 + rng.nextInt(numRhsFixedCount / 2) : 0;
                int numFixedCount = numRhsFixedCount - denFixedCount;

                List<Quantity> numFixed = fixedQuantities.subList(0, numFixedCount);
                List<Quantity> denFixed = fixedQuantities.subList(numFixedCount, fixedQuantities.size());

                DimVec netFixed = DimVec.ZERO;
                for (Quantity q : numFixed) netFixed = netFixed.add(q.vec());
                for (Quantity q : denFixed) netFixed = netFixed.sub(q.vec());

                DimVec needed = target.vec().sub(netFixed);

                // Run Greedy Reduction Solver (Шахматный жадный редуктор)
                ReductionResult red = solveGreedyReduction(needed, SOLVER_POOL, rng);
                if (red == null) continue;

                List<Quantity> numHoleSolutions = red.numHoles();
                List<Quantity> denHoleSolutions = red.denHoles();
                int mNum = numHoleSolutions.size();
                int mDen = denHoleSolutions.size();

                List<Quantity> allNum = new ArrayList<>(numFixed);
                allNum.addAll(numHoleSolutions);

                List<Quantity> allDen = new ArrayList<>(denFixed);
                allDen.addAll(denHoleSolutions);

                // Validation Rule 1: No target A in RHS
                boolean targetInRhs = allNum.stream().anyMatch(q -> q.id().equals(targetId))
                        || allDen.stream().anyMatch(q -> q.id().equals(targetId));
                if (targetInRhs) continue;

                // Validation Rule 2: No direct cross-cancellation between num and den
                Set<String> numIds = allNum.stream().map(Quantity::id).collect(Collectors.toSet());
                Set<String> denIds = allDen.stream().map(Quantity::id).collect(Collectors.toSet());
                boolean hasCrossCancel = numIds.stream().anyMatch(denIds::contains);
                if (hasCrossCancel) continue;

                // Build AST with empty slots for reduction holes
                Expr fullExpr = buildEquationExpr(target, numFixed, mNum, denFixed, mDen);
                if (fullExpr == null) continue;

                List<String> emptySlotIds = new ArrayList<>();
                for (Manipulate.SlotInfo s : Manipulate.collectSlots(fullExpr)) {
                    if (!s.filled() && !s.locked()) {
                        emptySlotIds.add(s.id());
                    }
                }

                // Map target solution for the empty slots
                Map<String, String> sampleSolution = new HashMap<>();
                int holeIdx = 0;
                for (String slotId : emptySlotIds) {
                    if (holeIdx < mNum) {
                        sampleSolution.put(slotId, numHoleSolutions.get(holeIdx).id());
                    } else if (holeIdx - mNum < mDen) {
                        sampleSolution.put(slotId, denHoleSolutions.get(holeIdx - mNum).id());
                    }
                    holeIdx++;
                }

                List<Manipulate.SlotInfo> allSlots = Manipulate.collectSlots(fullExpr);

                Set<String> lockedIds = new HashSet<>();
                lockedIds.add(allSlots.get(0).id()); // LHS slot
                for (Manipulate.SlotInfo info : allSlots) {
                    if (!emptySlotIds.contains(info.id())) {
                        lockedIds.add(info.id());
                    }
                }
                fullExpr = Manipulate.markLocked(fullExpr, lockedIds);

                int totalFixedSlots = lockedIds.size();
                int totalSlots = allSlots.size();

                long elapsedMs = System.currentTimeMillis() - startMs;
                LOGGER.info("[Chalkboard] Generated puzzle for stage {} in {} ms (cycle {}, target={}, fixedSlots={}/{}, totalSlots={}).",
                        stageNumber, elapsedMs, cycle, target.id(), totalFixedSlots, targetFixedNodes, totalSlots);

                String desc = "Цель: " + target.symbol() + " [" + target.unit() + "] · " + target.nameRu();
                return new Puzzle(fullExpr, target, new ArrayList<>(lockedIds), sampleSolution,
                        100.0, 0, stageNumber, titleRu, desc);
            }
        }

        // Guaranteed safety fallback
        long elapsedMs = System.currentTimeMillis() - startMs;
        LOGGER.warn("[Chalkboard] Puzzle generation for stage {} reached fallback 'L=X' after 20 cycles ({} ms).",
                stageNumber, elapsedMs);

        Quantity target = targetPool.get(0);
        Expr.Slot lhsSlot = new Expr.Slot(Expr.nid("s"), target.id(), true, false);
        Expr.Slot rhsSlot = Expr.Slot.empty();
        Expr fullExpr = Expr.Eq.of(lhsSlot, rhsSlot);
        Map<String, String> sampleSolution = Map.of(rhsSlot.id(), target.id());
        return new Puzzle(fullExpr, target, List.of(lhsSlot.id()), sampleSolution, 100.0, 0, stageNumber, titleRu, "Цель: " + target.symbol());
    }

    private record ReductionResult(List<Quantity> numHoles, List<Quantity> denHoles) {
    }

    /**
     * Greedy Reduction Solver (Шахматный жадный редуктор).
     * Iteratively selects quantities from pool that reduce dimensional deficit
     * obeying non-linear acceptable damage rules until D == ZERO.
     */
    private static ReductionResult solveGreedyReduction(DimVec needed, List<Quantity> pool, Random rng) {
        if (needed.isZero()) {
            return new ReductionResult(List.of(), List.of());
        }

        DimVec currentDeficit = needed;
        List<Quantity> numHoles = new ArrayList<>();
        List<Quantity> denHoles = new ArrayList<>();
        Set<DimVec> visited = new HashSet<>();
        visited.add(currentDeficit);

        List<Quantity> baseSiQuantities = List.of(
                Quantities.get("length"),
                Quantities.get("mass"),
                Quantities.get("time"),
                Quantities.get("current"),
                Quantities.get("temperature"),
                Quantities.get("amount"),
                Quantities.get("luminous")
        );

        int maxSteps = 12;
        for (int step = 0; step < maxSteps && !currentDeficit.isZero(); step++) {
            boolean addToNum = (step % 2 == 0); // Alternate chess pattern: Num, Den, Num, Den...

            Quantity bestCandidate = null;
            double bestNetGain = -999.0;
            DimVec bestNextDeficit = null;

            List<Quantity> candidatePool = new ArrayList<>(pool);
            Collections.shuffle(candidatePool, rng);

            for (Quantity candidate : candidatePool) {
                DimVec candidateVec = addToNum ? candidate.vec() : candidate.vec().scale(-1);
                DimVec nextDeficit = currentDeficit.sub(candidateVec);

                if (visited.contains(nextDeficit)) {
                    continue;
                }

                double gain = 0;
                double damage = 0;

                for (int i = 0; i < DimVec.SIZE; i++) {
                    double oldDist = Math.abs(currentDeficit.get(i));
                    double newDist = Math.abs(nextDeficit.get(i));
                    if (newDist < oldDist) {
                        gain += (oldDist - newDist);
                    } else if (newDist > oldDist) {
                        damage += (newDist - oldDist);
                    }
                }

                if (isAcceptableDamage(gain, damage)) {
                    double netGain = gain - damage;
                    if (netGain > bestNetGain) {
                        bestNetGain = netGain;
                        bestCandidate = candidate;
                        bestNextDeficit = nextDeficit;
                    }
                }
            }

            // Fallback to Base SI quantities for guaranteed progress
            if (bestCandidate == null) {
                for (int i = 0; i < DimVec.SIZE; i++) {
                    double axisVal = currentDeficit.get(i);
                    if (Math.abs(axisVal) > 1e-9) {
                        Quantity baseQ = baseSiQuantities.get(i);
                        if (baseQ != null) {
                            DimVec baseVec = addToNum ? baseQ.vec() : baseQ.vec().scale(-1);
                            if (axisVal < 0) baseVec = baseVec.scale(-1);

                            bestCandidate = baseQ;
                            bestNextDeficit = currentDeficit.sub(baseVec);
                            break;
                        }
                    }
                }
            }

            if (bestCandidate == null || bestNextDeficit == null) {
                break;
            }

            if (addToNum) {
                numHoles.add(bestCandidate);
            } else {
                denHoles.add(bestCandidate);
            }

            currentDeficit = bestNextDeficit;
            visited.add(currentDeficit);
        }

        if (currentDeficit.isZero()) {
            return new ReductionResult(numHoles, denHoles);
        }

        return null;
    }

    /**
     * Non-linear acceptable damage rule:
     * - damage <= 0: true (if gain > 0)
     * - gain <= damage: false
     * - gain +3, damage -1: true
     * - gain +4, damage -1: true
     * - gain +5, damage -2: true
     * - gain +6, damage -3: true
     * - gain +k, damage <= (k - 3): true
     */
    private static boolean isAcceptableDamage(double gain, double damage) {
        if (damage <= 0) return gain > 0;
        if (gain <= damage) return false;

        if (gain >= 3 && damage <= 1) return true;
        if (gain >= 5 && damage <= 2) return true;
        if (gain >= 6 && damage <= (gain - 3)) return true;

        return false;
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
