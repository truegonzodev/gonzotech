package com.gonzotech.chalkboard.core;

import java.util.Arrays;

/**
 * Immutable 7-dimensional SI dimension vector: [L, M, T, I, Theta, N, J].
 * <p>
 * Direct port of {@code src/engine/vector.ts}. Multiplication adds vectors,
 * division subtracts them, plus/minus require equality.
 */
public final class DimVec {

    public static final int SIZE = 7;

    /** Axis keys in canonical order. */
    public static final String[] AXIS_KEY = {"L", "M", "T", "I", "\u0398", "N", "J"};

    /** Human readable axis names (ru). */
    public static final String[] AXIS_NAME = {
            "\u0414\u043b\u0438\u043d\u0430", "\u041c\u0430\u0441\u0441\u0430", "\u0412\u0440\u0435\u043c\u044f",
            "\u0422\u043e\u043a", "\u0422\u0435\u043c\u043f\u0435\u0440\u0430\u0442\u0443\u0440\u0430",
            "\u0412\u0435\u0449\u0435\u0441\u0442\u0432\u043e", "\u0421\u0432\u0435\u0442"
    };

    /** Per-axis accent colours (0xRRGGBB) used by the bar renderer. */
    public static final int[] AXIS_COLOR = {
            0x7DD3FC, 0xFBBF24, 0xC4B5FD, 0x38BDF8, 0xFB7185, 0x4ADE80, 0xF9A8D4
    };

    public static final DimVec ZERO = new DimVec(0, 0, 0, 0, 0, 0, 0);

    private final double[] a;

    public DimVec(double l, double m, double t, double i, double th, double n, double j) {
        this.a = new double[]{l, m, t, i, th, n, j};
    }

    private DimVec(double[] raw) {
        this.a = raw;
    }

    public static DimVec of(double l, double m, double t, double i, double th, double n, double j) {
        return new DimVec(l, m, t, i, th, n, j);
    }

    public double get(int axis) {
        return a[axis];
    }

    public double[] raw() {
        return a.clone();
    }

    public DimVec add(DimVec o) {
        double[] r = new double[SIZE];
        for (int i = 0; i < SIZE; i++) r[i] = a[i] + o.a[i];
        return new DimVec(r);
    }

    public DimVec sub(DimVec o) {
        double[] r = new double[SIZE];
        for (int i = 0; i < SIZE; i++) r[i] = a[i] - o.a[i];
        return new DimVec(r);
    }

    public DimVec scale(double k) {
        double[] r = new double[SIZE];
        for (int i = 0; i < SIZE; i++) r[i] = a[i] * k;
        return new DimVec(r);
    }

    public DimVec avg(DimVec o) {
        return add(o).scale(0.5);
    }

    /** Euclidean norm ||v||. */
    public double mag() {
        double s = 0;
        for (double x : a) s += x * x;
        return Math.sqrt(s);
    }

    /** Euclidean distance d = ||A - B||. */
    public double dist(DimVec o) {
        return sub(o).mag();
    }

    public boolean isZero() {
        return mag() < 1e-9;
    }

    public boolean equalsVec(DimVec o) {
        return dist(o) < 1e-9;
    }

    /** Number of axes actually involved. */
    public int activeAxes() {
        int n = 0;
        for (double x : a) if (Math.abs(x) > 1e-9) n++;
        return n;
    }

    /** Sum of |exponents| — the "rank" of the quantity. */
    public double rank() {
        double s = 0;
        for (double x : a) s += Math.abs(x);
        return s;
    }

    /**
     * Normalized dimensional compatibility, the heart of the whole game:
     * <pre>S_D = 100 * (1 - ||A-B|| / (||A|| + ||B||))</pre>
     * Bounded to [0, 100] by the triangle inequality.
     */
    public static double dimScore(DimVec a, DimVec b) {
        double d = a.dist(b);
        double denom = a.mag() + b.mag();
        if (denom < 1e-12) return 100.0;
        double s = 100.0 * (1.0 - d / denom);
        return Math.max(0.0, Math.min(100.0, s));
    }

    /** Numerical layer: relative closeness of two scalars. */
    public static double numScore(double a, double b) {
        double aa = Math.abs(a);
        double bb = Math.abs(b);
        if (aa < 1e-15 && bb < 1e-15) return 100.0;
        if (aa < 1e-15 || bb < 1e-15) return 0.0;
        double ratio = Math.min(aa, bb) / Math.max(aa, bb);
        return Math.max(0.0, Math.min(100.0, 100.0 * ratio));
    }

    /** Aura falloff over graph distance: 1 / (1 + d^2 / (complexity + 1)). */
    public static double decay(int graphDistance, double complexity) {
        return 1.0 / (1.0 + (graphDistance * (double) graphDistance) / (complexity + 1.0));
    }

    private static final char[] SUP_DIGIT = {'\u2070', '\u00b9', '\u00b2', '\u00b3', '\u2074',
            '\u2075', '\u2076', '\u2077', '\u2078', '\u2079'};

    private static String superscript(double exp) {
        if (Math.abs(exp - 1.0) < 1e-9) return "";
        StringBuilder sb = new StringBuilder();
        String s = trim(exp);
        for (char c : s.toCharArray()) {
            if (c == '-') sb.append('\u207b');
            else if (c >= '0' && c <= '9') sb.append(SUP_DIGIT[c - '0']);
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String trim(double v) {
        double r = Math.round(v * 1000.0) / 1000.0;
        if (Math.abs(r - Math.rint(r)) < 1e-9) return String.valueOf((long) Math.rint(r));
        return String.valueOf(r);
    }

    /** Pretty SI notation, e.g. {@code M\u00b7L\u00b2\u00b7T\u207b\u00b3}. */
    public String format() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIZE; i++) {
            if (Math.abs(a[i]) < 1e-9) continue;
            if (sb.length() > 0) sb.append('\u00b7');
            sb.append(AXIS_KEY[i]).append(superscript(a[i]));
        }
        return sb.length() == 0 ? "1" : sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DimVec d && Arrays.equals(a, d.a);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(a);
    }
}
