package com.gonzotech.chalkboard.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Server-side proof that a submitted resonance expression still belongs to the
 * seed-generated puzzle currently assigned to a player.
 *
 * <p>This deliberately does <strong>not</strong> compare the expression to a
 * sample solution.  Instead, the original equation is treated as an immutable
 * skeleton: its root, operator layout, slots and locked quantities must remain
 * present; the player may fill original holes and wrap any node using the
 * same added multiply/divide nodes created by {@link Manipulate}.  Thus a
 * legitimate rearrangement such as {@code F * t = m * a / Hz} remains valid
 * when it reaches the normal evaluator threshold, while a standalone
 * {@code F = F} tree cannot replace a discovery puzzle.</p>
 */
public final class ChalkboardSubmissionValidator {

    /** Matches the normal editor's practical identifier format and bounds map keys. */
    private static final int MAX_NODE_ID_LENGTH = 96;

    private ChalkboardSubmissionValidator() {
    }

    /** Outcome of either a draft save or a claim validation. */
    public record Result(boolean accepted, String reason, Analysis analysis) {
        private static Result accept(Analysis analysis) {
            return new Result(true, "", analysis);
        }

        private static Result reject(String reason) {
            return new Result(false, reason, null);
        }
    }

    /**
     * Verifies that a work-in-progress expression is a legal continuation of
     * {@code puzzle}.  Incomplete slots are allowed so it can be persisted.
     */
    public static Result validateDraft(GameSolver.Puzzle puzzle, Expr submitted,
                                       Predicate<Quantity> isQuantityUnlocked) {
        if (puzzle == null || puzzle.expr() == null || submitted == null || isQuantityUnlocked == null) {
            return Result.reject("missing puzzle or expression");
        }
        if (!(puzzle.expr() instanceof Expr.Eq baseEq) || !(submitted instanceof Expr.Eq submittedEq)) {
            return Result.reject("discovery must remain an equation");
        }
        if (!Objects.equals(baseEq.id(), submittedEq.id())) {
            return Result.reject("equation root does not belong to this discovery");
        }

        TreeInfo baseInfo = inspectTree(puzzle.expr());
        TreeInfo submittedInfo = inspectTree(submitted);
        if (baseInfo == null || submittedInfo == null) {
            return Result.reject("malformed expression tree");
        }

        Set<String> lockedSlotIds = new HashSet<>(puzzle.lockedSlotIds());
        for (String id : lockedSlotIds) {
            Expr baseNode = baseInfo.nodesById.get(id);
            if (!(baseNode instanceof Expr.Slot)) {
                return Result.reject("server puzzle has an invalid locked slot");
            }
        }

        Expr.Slot targetSlot = findTargetAnchor(baseEq.left(), puzzle.target());
        if (targetSlot == null || !lockedSlotIds.contains(targetSlot.id())) {
            return Result.reject("server puzzle has no locked target on the left side");
        }

        ValidationContext context = new ValidationContext(baseInfo.nodesById, lockedSlotIds,
                puzzle.target().id(), isQuantityUnlocked);
        if (!matchesBase(baseEq, submittedEq, context)) {
            return Result.reject("expression no longer preserves the discovery skeleton");
        }

        return Result.accept(null);
    }

    /**
     * Verifies a completed discovery claim.  This performs the draft/skeleton
     * validation first and only then invokes the shared scoring engine.
     */
    public static Result validateClaim(GameSolver.Puzzle puzzle, Expr submitted,
                                       boolean isInfiniteMode,
                                       Predicate<Quantity> isQuantityUnlocked) {
        Result draft = validateDraft(puzzle, submitted, isQuantityUnlocked);
        if (!draft.accepted()) return draft;

        Analysis analysis = Evaluator.analyze(submitted, isInfiniteMode);
        if (!analysis.complete) {
            return Result.reject("expression is incomplete");
        }
        if (!analysis.conflicts.isEmpty()) {
            return Result.reject("expression has dimensional conflicts");
        }
        if (!analysis.discovery || analysis.sFinal == null || analysis.sFinal < Evaluator.DISCOVERY_THRESHOLD) {
            return Result.reject("expression did not reach the discovery score");
        }

        return Result.accept(analysis);
    }

    /** A verified target must be the server's locked target, on the equation's left. */
    private static Expr.Slot findTargetAnchor(Expr expr, Quantity target) {
        if (expr == null || target == null) return null;
        if (expr instanceof Expr.Slot slot) {
            return slot.locked() && target.id().equals(slot.quantityId()) ? slot : null;
        }
        if (expr instanceof Expr.Op op) {
            Expr.Slot left = findTargetAnchor(op.left(), target);
            return left != null ? left : findTargetAnchor(op.right(), target);
        }
        if (expr instanceof Expr.Pow pow) {
            return findTargetAnchor(pow.base(), target);
        }
        return null;
    }

    /**
     * Matches a baseline node, allowing only added MUL/DIV wrappers around it.
     * The sibling of such a wrapper must be a pure added subtree, so no baseline
     * slots can be moved, duplicated or exchanged between equation sides.
     */
    private static boolean matchesBase(Expr base, Expr candidate, ValidationContext context) {
        if (base == null || candidate == null) return false;

        if (Objects.equals(base.id(), candidate.id())) {
            if (base instanceof Expr.Slot baseSlot && candidate instanceof Expr.Slot submittedSlot) {
                return matchesBaseSlot(baseSlot, submittedSlot, context);
            }
            if (base instanceof Expr.Op baseOp && candidate instanceof Expr.Op submittedOp) {
                return baseOp.op() == submittedOp.op()
                        && baseOp.isAdded() == submittedOp.isAdded()
                        && matchesBase(baseOp.left(), submittedOp.left(), context)
                        && matchesBase(baseOp.right(), submittedOp.right(), context);
            }
            if (base instanceof Expr.Eq baseEq && candidate instanceof Expr.Eq submittedEq) {
                return matchesBase(baseEq.left(), submittedEq.left(), context)
                        && matchesBase(baseEq.right(), submittedEq.right(), context);
            }
            // Generated discovery puzzles do not contain bare numeric or power nodes.
            return false;
        }

        if (!(candidate instanceof Expr.Op wrapper)
                || !isAddedMultiplyOrDivide(wrapper)
                || context.baseNodesById.containsKey(wrapper.id())) {
            return false;
        }

        return (matchesBase(base, wrapper.left(), context)
                    && isPureAddedSubtree(wrapper.right(), context))
                || (matchesBase(base, wrapper.right(), context)
                    && isPureAddedSubtree(wrapper.left(), context));
    }

    private static boolean matchesBaseSlot(Expr.Slot base, Expr.Slot submitted, ValidationContext context) {
        if (base.isAdded() != submitted.isAdded()) return false;
        boolean frozen = context.lockedSlotIds.contains(base.id());
        if (frozen) {
            return base.locked()
                    && submitted.locked()
                    && Objects.equals(base.quantityId(), submitted.quantityId());
        }
        if (submitted.locked()) return false;
        return submitted.quantityId() == null || isPlayerQuantity(submitted.quantityId(), context);
    }

    /** Validates the new slot/operation branch created by {@code Manipulate.wrapNode}. */
    private static boolean isPureAddedSubtree(Expr expr, ValidationContext context) {
        if (expr == null || context.baseNodesById.containsKey(expr.id())) return false;
        if (expr instanceof Expr.Slot slot) {
            return slot.isAdded()
                    && !slot.locked()
                    && (slot.quantityId() == null || isPlayerQuantity(slot.quantityId(), context));
        }
        if (expr instanceof Expr.Op op) {
            return isAddedMultiplyOrDivide(op)
                    && isPureAddedSubtree(op.left(), context)
                    && isPureAddedSubtree(op.right(), context);
        }
        // Added equations, powers and bare numbers cannot be made by the board UI.
        return false;
    }

    private static boolean isAddedMultiplyOrDivide(Expr.Op op) {
        return op.isAdded() && (op.op() == Expr.OpKind.MUL || op.op() == Expr.OpKind.DIV);
    }

    /** Quantity IDs are resolved on the server and the target cannot be duplicated into a player slot. */
    private static boolean isPlayerQuantity(String quantityId, ValidationContext context) {
        Quantity quantity = Quantities.get(quantityId);
        return quantity != null
                && !context.targetQuantityId.equals(quantity.id())
                && context.isQuantityUnlocked.test(quantity);
    }

    /**
     * Generic malformed-tree protection for expressions created directly in
     * Java as well as those decoded from JSON.  It catches duplicate node IDs,
     * null children and unknown quantities before Evaluator uses ID-keyed maps.
     */
    private static TreeInfo inspectTree(Expr root) {
        Map<String, Expr> nodesById = new HashMap<>();
        return inspect(root, 0, new int[]{0}, nodesById) ? new TreeInfo(nodesById) : null;
    }

    private static boolean inspect(Expr expr, int depth, int[] count, Map<String, Expr> nodesById) {
        if (expr == null || depth > Serde.MAX_TREE_DEPTH || ++count[0] > Serde.MAX_TREE_NODES
                || !validNodeId(expr.id()) || nodesById.putIfAbsent(expr.id(), expr) != null) {
            return false;
        }

        if (expr instanceof Expr.Slot slot) {
            return slot.quantityId() == null || Quantities.get(slot.quantityId()) != null;
        }
        if (expr instanceof Expr.Num num) {
            return Double.isFinite(num.value());
        }
        if (expr instanceof Expr.Pow pow) {
            return Double.isFinite(pow.exp()) && inspect(pow.base(), depth + 1, count, nodesById);
        }
        if (expr instanceof Expr.Op op) {
            return op.op() != null
                    && inspect(op.left(), depth + 1, count, nodesById)
                    && inspect(op.right(), depth + 1, count, nodesById);
        }
        if (expr instanceof Expr.Eq eq) {
            return inspect(eq.left(), depth + 1, count, nodesById)
                    && inspect(eq.right(), depth + 1, count, nodesById);
        }
        return false;
    }

    private static boolean validNodeId(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_NODE_ID_LENGTH) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9')
                    && c != '_' && c != '-') {
                return false;
            }
        }
        return true;
    }

    private record TreeInfo(Map<String, Expr> nodesById) {
    }

    private record ValidationContext(Map<String, Expr> baseNodesById,
                                     Set<String> lockedSlotIds,
                                     String targetQuantityId,
                                     Predicate<Quantity> isQuantityUnlocked) {
    }
}
