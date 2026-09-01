package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.core.Expr;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline-aligned layout of the expression tree.
 * Divisions are rendered as real stacked fractions, which is what makes the
 * "drag below → build a fraction" gesture readable.
 */
public final class FormulaLayout {

    public static final int SLOT_W = 74;
    public static final int SLOT_H = 46;
    public static final int NUM_W = 34;
    public static final int OP_W = 15;
    public static final int OP_H = 15;
    public static final int EQ_W = 19;
    public static final int GAP = 5;
    public static final int FRAC_PAD = 7;
    public static final int FRAC_LINE = 2;
    public static final int FRAC_GAP = 3;
    public static final int POW_W = 9;

    public enum BoxKind {SLOT, NUM, OP, EQ, FRACTION_LINE, POW_EXP}

    public record Box(Expr node, BoxKind kind, int x, int y, int w, int h, String text) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private record Measure(int w, int h, int baseline) {
    }

    private FormulaLayout() {
    }

    public static Measure measure(Expr e) {
        return switch (e) {
            case Expr.Slot ignored -> new Measure(SLOT_W, SLOT_H, SLOT_H / 2);
            case Expr.Num ignored -> new Measure(NUM_W, SLOT_H, SLOT_H / 2);
            case Expr.Pow p -> {
                Measure b = measure(p.base());
                yield new Measure(b.w() + POW_W, b.h(), b.baseline());
            }
            case Expr.Op o -> {
                Measure l = measure(o.left());
                Measure r = measure(o.right());
                if (o.op() == Expr.OpKind.DIV) {
                    int w = Math.max(l.w(), r.w()) + FRAC_PAD * 2;
                    int h = l.h() + FRAC_GAP + FRAC_LINE + FRAC_GAP + r.h();
                    int baseline = l.h() + FRAC_GAP + FRAC_LINE / 2;
                    yield new Measure(w, h, baseline);
                }
                int above = Math.max(Math.max(l.baseline(), r.baseline()), OP_H / 2);
                int below = Math.max(Math.max(l.h() - l.baseline(), r.h() - r.baseline()), OP_H - OP_H / 2);
                yield new Measure(l.w() + GAP + OP_W + GAP + r.w(), above + below, above);
            }
            case Expr.Eq q -> {
                Measure l = measure(q.left());
                Measure r = measure(q.right());
                int above = Math.max(Math.max(l.baseline(), r.baseline()), 8);
                int below = Math.max(Math.max(l.h() - l.baseline(), r.h() - r.baseline()), 8);
                yield new Measure(l.w() + GAP + EQ_W + GAP + r.w(), above + below, above);
            }
        };
    }

    public static int totalWidth(Expr e) {
        return measure(e).w();
    }

    public static int totalHeight(Expr e) {
        return measure(e).h();
    }

    /** Lays the whole tree out with its bounding box top-left at (originX, originY). */
    public static List<Box> layout(Expr root, int originX, int originY) {
        List<Box> out = new ArrayList<>();
        Measure m = measure(root);
        place(root, originX, originY + m.baseline(), out);
        return out;
    }

    private static void place(Expr e, int x, int baselineY, List<Box> out) {
        Measure m = measure(e);
        int top = baselineY - m.baseline();

        switch (e) {
            case Expr.Slot s -> out.add(new Box(s, BoxKind.SLOT, x, top, SLOT_W, SLOT_H, null));
            case Expr.Num n -> out.add(new Box(n, BoxKind.NUM, x, top, NUM_W, SLOT_H,
                    n.label() != null ? n.label() : trimNum(n.value())));
            case Expr.Pow p -> {
                Measure b = measure(p.base());
                place(p.base(), x, baselineY, out);
                out.add(new Box(p, BoxKind.POW_EXP, x + b.w(), top + 2, POW_W, 10, trimNum(p.exp())));
            }
            case Expr.Op o -> {
                Measure l = measure(o.left());
                Measure r = measure(o.right());
                if (o.op() == Expr.OpKind.DIV) {
                    int w = m.w();
                    place(o.left(), x + (w - l.w()) / 2, top + l.baseline(), out);
                    int lineY = top + l.h() + FRAC_GAP;
                    out.add(new Box(o, BoxKind.FRACTION_LINE, x, lineY, w, FRAC_LINE, null));
                    int denTop = lineY + FRAC_LINE + FRAC_GAP;
                    place(o.right(), x + (w - r.w()) / 2, denTop + r.baseline(), out);
                } else {
                    place(o.left(), x, baselineY, out);
                    int opX = x + l.w() + GAP;
                    out.add(new Box(o, BoxKind.OP, opX, baselineY - OP_H / 2, OP_W, OP_H, o.op().symbol));
                    place(o.right(), opX + OP_W + GAP, baselineY, out);
                }
            }
            case Expr.Eq q -> {
                Measure l = measure(q.left());
                place(q.left(), x, baselineY, out);
                int eqX = x + l.w() + GAP;
                out.add(new Box(q, BoxKind.EQ, eqX, baselineY - 8, EQ_W, 16, "="));
                place(q.right(), eqX + EQ_W + GAP, baselineY, out);
            }
        }
    }

    public static String trimNum(double v) {
        if (Math.abs(v - 0.5) < 1e-9) return "1/2";
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }
}
