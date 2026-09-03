package com.gonzotech.chalkboard.core;

import java.util.List;
import java.util.Map;

/** Full evaluation result of one equation tree. */
public final class Analysis {

    /** Illegal add/sub between mismatched dimension vectors. */
    public record Conflict(String nodeId, Expr.OpKind op, DimVec left, DimVec right, double love) {
    }

    /** Local "love" reading of one operator or one filled slot. */
    public record LocalLove(String nodeId, String label, double score, String kind) {
    }

    /** Bottom-up value of a node. */
    public record NodeEval(DimVec vec, Double value, boolean complete) {
    }

    public final boolean complete;
    public final DimVec leftVec;
    public final DimVec rightVec;
    public final Double leftValue;
    public final Double rightValue;

    /** Dimensional compatibility, after common-factor cancellation. */
    public final Double sD;
    /** Numerical layer. */
    public final Double sN;
    /** S_D * S_N / 100 - lhsPenalty - rhsPenalty. */
    public final Double sFinal;

    public final List<Conflict> conflicts;
    public final List<LocalLove> locals;
    public final Map<String, DimVec> required;
    public final Map<String, Double> slotLove;
    public final Map<String, NodeEval> evalById;

    public final boolean discovery;
    public final int emptySlots;
    public final int filledSlots;
    public final int totalSlots;

    /** −1 point for every non-frozen quantity left on the LHS. */
    public final int lhsPenalty;
    public final List<String> lhsExtraSlotIds;

    /** −1 point for every extra slot added in the RHS beyond initial preset slots. */
    public final int rhsPenalty;
    public final List<String> rhsExtraSlotIds;

    /** Quantities that were cancelled out on both sides (anti-inflation compression). */
    public final List<String> cancelledIds;

    public Analysis(boolean complete, DimVec leftVec, DimVec rightVec, Double leftValue, Double rightValue,
                    Double sD, Double sN, Double sFinal, List<Conflict> conflicts, List<LocalLove> locals,
                    Map<String, DimVec> required, Map<String, Double> slotLove, Map<String, NodeEval> evalById,
                    boolean discovery, int emptySlots, int filledSlots, int totalSlots,
                    int lhsPenalty, List<String> lhsExtraSlotIds,
                    int rhsPenalty, List<String> rhsExtraSlotIds,
                    List<String> cancelledIds) {
        this.complete = complete;
        this.leftVec = leftVec;
        this.rightVec = rightVec;
        this.leftValue = leftValue;
        this.rightValue = rightValue;
        this.sD = sD;
        this.sN = sN;
        this.sFinal = sFinal;
        this.conflicts = conflicts;
        this.locals = locals;
        this.required = required;
        this.slotLove = slotLove;
        this.evalById = evalById;
        this.discovery = discovery;
        this.emptySlots = emptySlots;
        this.filledSlots = filledSlots;
        this.totalSlots = totalSlots;
        this.lhsPenalty = lhsPenalty;
        this.lhsExtraSlotIds = lhsExtraSlotIds;
        this.rhsPenalty = rhsPenalty;
        this.rhsExtraSlotIds = rhsExtraSlotIds;
        this.cancelledIds = cancelledIds;
    }

    public double scoreOr(double fallback) {
        return sFinal == null ? fallback : sFinal;
    }

    public static String loveLabel(double score) {
        if (score < 20) return "Конфликт";
        if (score < 40) return "Слабая связь";
        if (score < 60) return "Допустимо";
        if (score < 80) return "Хорошая совместимость";
        if (score < 95) return "Сильная связь";
        return "Озарение";
    }
}
