package com.gonzotech.chalkboard.core;

import com.gonzotech.chalkboard.core.Analysis.Conflict;
import com.gonzotech.chalkboard.core.Analysis.LocalLove;
import com.gonzotech.chalkboard.core.Analysis.NodeEval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole balance model, ported 1:1 from {@code src/engine/evaluate.ts}.
 * <p>
 * Layer 1 — dimensional: S_D = 100 * (1 - ||A-B|| / (||A||+||B||)).<br>
 * Layer 2 — numerical:   S_N compares the actual coefficient ratio.<br>
 * S_final = S_D * S_N / 100 - lhsPenalty.
 * <p>
 * Before scoring, factors that appear on both sides are cancelled, so
 * multiplying both sides by the same block can no longer inflate the score.
 */
public final class Evaluator {

    private Evaluator() {
    }

    public static final double DISCOVERY_THRESHOLD = 90.0;

    private record Internal(DimVec vec, Double value, boolean complete,
                            List<Conflict> conflicts, List<LocalLove> locals) {
    }

    private static Quantity qty(String id) {
        return Quantities.get(id);
    }

    // ───────────────────────── bottom-up evaluation ─────────────────────────

    private static Internal bottomUp(Expr expr, Map<String, NodeEval> out) {
        if (expr instanceof Expr.Slot s) {
            Quantity q = qty(s.quantityId());
            boolean complete = q != null;
            DimVec vec = q != null ? q.vec() : null;
            Double value = q != null ? q.value() : null;
            out.put(s.id(), new NodeEval(vec, value, complete));
            return new Internal(vec, value, complete, new ArrayList<>(), new ArrayList<>());
        }

        if (expr instanceof Expr.Num n) {
            out.put(n.id(), new NodeEval(DimVec.ZERO, n.value(), true));
            return new Internal(DimVec.ZERO, n.value(), true, new ArrayList<>(), new ArrayList<>());
        }

        if (expr instanceof Expr.Pow p) {
            Internal base = bottomUp(p.base(), out);
            DimVec vec = base.vec() != null ? base.vec().scale(p.exp()) : null;
            Double value = base.value() != null ? Math.pow(base.value(), p.exp()) : null;
            out.put(p.id(), new NodeEval(vec, value, base.complete()));
            return new Internal(vec, value, base.complete(), base.conflicts(), base.locals());
        }

        if (expr instanceof Expr.Op o) {
            Internal l = bottomUp(o.left(), out);
            Internal r = bottomUp(o.right(), out);
            List<Conflict> conflicts = new ArrayList<>(l.conflicts());
            conflicts.addAll(r.conflicts());
            List<LocalLove> locals = new ArrayList<>(l.locals());
            locals.addAll(r.locals());

            DimVec vec = null;
            Double value = null;
            boolean bothVec = l.vec() != null && r.vec() != null;
            boolean bothVal = l.value() != null && r.value() != null;

            if (o.op() == Expr.OpKind.MUL) {
                if (bothVec) vec = l.vec().add(r.vec());
                if (bothVal) value = l.value() * r.value();
                if (l.complete() && r.complete() && bothVec) {
                    locals.add(new LocalLove(o.id(), "\u00d7", 100.0, "product"));
                }
            } else if (o.op() == Expr.OpKind.DIV) {
                if (bothVec) vec = l.vec().sub(r.vec());
                if (bothVal) value = r.value() == 0 ? null : l.value() / r.value();
                if (l.complete() && r.complete() && bothVec) {
                    locals.add(new LocalLove(o.id(), "\u00f7", 100.0, "product"));
                }
            } else {
                if (bothVal) value = o.op() == Expr.OpKind.ADD
                        ? l.value() + r.value() : l.value() - r.value();
                if (bothVec && l.complete() && r.complete()) {
                    double love = DimVec.dimScore(l.vec(), r.vec());
                    if (!l.vec().equalsVec(r.vec())) {
                        conflicts.add(new Conflict(o.id(), o.op(), l.vec(), r.vec(), love));
                        vec = l.vec().avg(r.vec());
                    } else {
                        vec = l.vec();
                    }
                    locals.add(new LocalLove(o.id(), o.op().symbol, love,
                            o.op() == Expr.OpKind.ADD ? "add" : "sub"));
                } else if (bothVec) {
                    vec = l.vec().equalsVec(r.vec()) ? l.vec() : l.vec().avg(r.vec());
                }
            }

            boolean complete = l.complete() && r.complete();
            out.put(o.id(), new NodeEval(vec, value, complete));
            return new Internal(vec, value, complete, conflicts, locals);
        }

        Expr.Eq q = (Expr.Eq) expr;
        Internal l = bottomUp(q.left(), out);
        Internal r = bottomUp(q.right(), out);
        List<Conflict> conflicts = new ArrayList<>(l.conflicts());
        conflicts.addAll(r.conflicts());
        List<LocalLove> locals = new ArrayList<>(l.locals());
        locals.addAll(r.locals());
        boolean complete = l.complete() && r.complete();
        if (complete && l.vec() != null && r.vec() != null) {
            locals.add(new LocalLove(q.id(), "=", DimVec.dimScore(l.vec(), r.vec()), "eq"));
        }
        out.put(q.id(), new NodeEval(l.vec(), l.value(), complete));
        return new Internal(l.vec(), l.value(), complete, conflicts, locals);
    }

    // ───────────────────────── required-vector back-propagation ─────────────────────────

    private static void pushRequired(Expr expr, DimVec required,
                                     Map<String, DimVec> store, Map<String, NodeEval> evalById) {
        if (required != null) store.put(expr.id(), required);

        switch (expr) {
            case Expr.Slot ignored -> {
            }
            case Expr.Num ignored -> {
            }
            case Expr.Pow p -> {
                DimVec inv = (required == null || p.exp() == 0) ? null : required.scale(1.0 / p.exp());
                pushRequired(p.base(), inv, store, evalById);
            }
            case Expr.Op o -> {
                NodeEval le = evalById.get(o.left().id());
                NodeEval re = evalById.get(o.right().id());
                DimVec lv = le != null ? le.vec() : null;
                DimVec rv = re != null ? re.vec() : null;
                boolean lc = le != null && le.complete();
                boolean rc = re != null && re.complete();

                switch (o.op()) {
                    case MUL -> {
                        DimVec leftReq = (required != null && rc && rv != null) ? required.sub(rv) : null;
                        DimVec rightReq = (required != null && lc && lv != null) ? required.sub(lv) : null;
                        pushRequired(o.left(), leftReq, store, evalById);
                        pushRequired(o.right(), rightReq, store, evalById);
                    }
                    case DIV -> {
                        // required = L - R  =>  L = required + R,  R = L - required
                        DimVec leftReq = (required != null && rc && rv != null) ? required.add(rv) : null;
                        DimVec rightReq = (required != null && lc && lv != null) ? lv.sub(required) : null;
                        pushRequired(o.left(), leftReq, store, evalById);
                        pushRequired(o.right(), rightReq, store, evalById);
                    }
                    case ADD, SUB -> {
                        pushRequired(o.left(), required, store, evalById);
                        pushRequired(o.right(), required, store, evalById);
                    }
                }
            }
            case Expr.Eq q -> {
                NodeEval le = evalById.get(q.left().id());
                NodeEval re = evalById.get(q.right().id());
                boolean lc = le != null && le.complete();
                boolean rc = re != null && re.complete();
                DimVec leftReq = (rc && re.vec() != null) ? re.vec() : required;
                DimVec rightReq = (lc && le.vec() != null) ? le.vec() : required;
                pushRequired(q.left(), leftReq, store, evalById);
                pushRequired(q.right(), rightReq, store, evalById);
            }
        }
    }

    // ───────────────────────── anti-inflation compression ─────────────────────────

    /**
     * Flattens one side into a map {@code quantityId -> net power}, where a
     * quantity under a division contributes a negative power.
     */
    static Map<String, Double> multiplicativeFactors(Expr expr) {
        Map<String, Double> factors = new LinkedHashMap<>();
        walkFactors(expr, 1.0, factors);
        return factors;
    }

    private static void walkFactors(Expr node, double scale, Map<String, Double> out) {
        switch (node) {
            case Expr.Slot s -> {
                if (s.quantityId() != null) out.merge(s.quantityId(), scale, Double::sum);
            }
            case Expr.Pow p -> walkFactors(p.base(), scale * p.exp(), out);
            case Expr.Op o -> {
                if (o.op() == Expr.OpKind.MUL) {
                    walkFactors(o.left(), scale, out);
                    walkFactors(o.right(), scale, out);
                } else if (o.op() == Expr.OpKind.DIV) {
                    walkFactors(o.left(), scale, out);
                    walkFactors(o.right(), -scale, out);
                }
                // add/sub are not multiplicative — nothing to cancel across them
            }
            default -> {
            }
        }
    }

    // ───────────────────────── public entry point ─────────────────────────

    public static Analysis analyze(Expr expr) {
        return analyze(expr, null, null, 1.0);
    }

    public static Analysis analyze(Expr expr, String previewSlotId, String previewQuantityId, double numericRatio) {
        Expr working = (previewSlotId != null && previewQuantityId != null)
                ? Manipulate.setSlotQuantity(expr, previewSlotId, previewQuantityId)
                : expr;

        Map<String, NodeEval> evalById = new HashMap<>();
        Internal root = bottomUp(working, evalById);

        Map<String, DimVec> required = new HashMap<>();
        pushRequired(working, null, required, evalById);

        Map<String, Double> slotLove = new HashMap<>();
        List<LocalLove> extraLocals = new ArrayList<>();
        Manipulate.walkSlots(working, s -> {
            DimVec req = required.get(s.id());
            Quantity q = qty(s.quantityId());
            if (req != null && q != null) {
                double love = DimVec.dimScore(req, q.vec());
                slotLove.put(s.id(), love);
                extraLocals.add(new LocalLove(s.id(), q.symbol(), love, "slot"));
            }
        });

        int[] counts = new int[3]; // empty, filled, total
        Manipulate.walkSlots(working, s -> {
            counts[2]++;
            if (s.quantityId() != null) counts[1]++;
            else counts[0]++;
        });

        NodeEval left = null;
        NodeEval right = null;
        if (working instanceof Expr.Eq eq) {
            left = evalById.get(eq.left().id());
            right = evalById.get(eq.right().id());
        }

        // ── LHS penalty: the target must be isolated ──
        List<String> lhsExtraSlotIds = new ArrayList<>();
        if (working instanceof Expr.Eq eq) {
            Manipulate.walkSlots(eq.left(), s -> {
                if (s.quantityId() != null && !s.locked()) lhsExtraSlotIds.add(s.id());
            });
        }
        int lhsPenalty = lhsExtraSlotIds.size();

        // ── background cancellation of common factors ──
        DimVec leftVec = left != null ? left.vec() : null;
        DimVec rightVec = right != null ? right.vec() : null;
        List<String> cancelled = new ArrayList<>();

        if (working instanceof Expr.Eq eq && leftVec != null && rightVec != null) {
            Map<String, Double> lhs = multiplicativeFactors(eq.left());
            Map<String, Double> rhs = multiplicativeFactors(eq.right());
            for (Map.Entry<String, Double> e : lhs.entrySet()) {
                Double pR = rhs.get(e.getKey());
                if (pR == null) continue;
                double pL = e.getValue();
                double cancel = 0;
                if (pL > 0 && pR > 0) cancel = Math.min(pL, pR);
                else if (pL < 0 && pR < 0) cancel = Math.max(pL, pR);
                if (cancel == 0) continue;
                Quantity q = qty(e.getKey());
                if (q == null) continue;
                DimVec shift = q.vec().scale(cancel);
                leftVec = leftVec.sub(shift);
                rightVec = rightVec.sub(shift);
                cancelled.add(q.symbol());
            }
        }

        boolean complete = root.complete();
        Double sD = null;
        Double sN = null;
        Double sFinal = null;

        if (complete && leftVec != null && rightVec != null) {
            sD = DimVec.dimScore(leftVec, rightVec);
        }
        if (complete && left != null && right != null && left.value() != null && right.value() != null) {
            double rv = right.value();
            double lv = left.value();
            double ratio = rv == 0 ? (lv == 0 ? 1 : Double.POSITIVE_INFINITY) : lv / rv;
            sN = Double.isFinite(ratio) ? DimVec.numScore(ratio, numericRatio) : 0.0;
        }
        if (sD != null && sN != null) {
            sFinal = Math.max(0.0, (sD * sN) / 100.0 - lhsPenalty);
        }

        boolean discovery = sFinal != null && sFinal >= DISCOVERY_THRESHOLD && root.conflicts().isEmpty();

        List<LocalLove> locals = new ArrayList<>(root.locals());
        locals.addAll(extraLocals);

        return new Analysis(complete, leftVec, rightVec,
                left != null ? left.value() : null,
                right != null ? right.value() : null,
                sD, sN, sFinal, root.conflicts(), locals, required, slotLove, evalById,
                discovery, counts[0], counts[1], counts[2],
                lhsPenalty, lhsExtraSlotIds, cancelled);
    }

    /** Aura strength of every empty slot, given the already placed blocks. */
    public static Map<String, Double> decayedLove(Expr expr, Analysis analysis) {
        Map<String, Double> out = new HashMap<>();
        List<Manipulate.SlotInfo> slots = Manipulate.collectSlots(expr);
        for (Manipulate.SlotInfo empty : slots) {
            if (empty.filled()) continue;
            DimVec req = analysis.required.get(empty.id());
            if (req == null) continue;
            double best = 0;
            for (Manipulate.SlotInfo filled : slots) {
                if (!filled.filled()) continue;
                Quantity q = Quantities.get(filled.quantityId());
                if (q == null) continue;
                int d = Manipulate.graphDistance(expr, filled.id(), empty.id());
                best = Math.max(best, DimVec.dimScore(req, q.vec()) * DimVec.decay(d, q.complexity()));
            }
            out.put(empty.id(), best);
        }
        return out;
    }
}
