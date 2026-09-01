package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.core.DimVec;
import com.gonzotech.chalkboard.core.Quantity;

/** Colour projection of the 7 axes onto a hue wheel + the love gradient. */
public final class Palette {

    private Palette() {
    }

    public static final int BG = 0xF00A0D16;
    public static final int PANEL = 0xE0111726;
    public static final int PANEL_SOFT = 0x60151C2C;
    public static final int STROKE = 0x3394A3B8;
    public static final int TEXT = 0xFFE8EEF7;
    public static final int TEXT_DIM = 0xFF8A97AB;
    public static final int TEXT_FAINT = 0xFF5A667A;
    public static final int CYAN = 0xFF5CE1FF;
    public static final int AMBER = 0xFFFFC14A;
    public static final int ROSE = 0xFFFF4D6A;
    public static final int GREEN = 0xFF3DFF9A;
    public static final int VIOLET = 0xFFA78BFA;

    /** Discrete love gradient, identical bands to the web build. */
    public static int love(double score) {
        if (score < 20) return 0xFFFF4D6A;
        if (score < 40) return 0xFFFF8C42;
        if (score < 60) return 0xFFFFC14A;
        if (score < 80) return 0xFF9AE66E;
        if (score < 95) return 0xFF3DFF9A;
        return 0xFFE8F6FF;
    }

    public static int weightColor(int weight) {
        return switch (weight) {
            case 0 -> 0xFF94A3B8;
            case 1 -> 0xFF4ADE80;
            case 2 -> 0xFF5CE1FF;
            default -> 0xFFFFC14A;
        };
    }

    /** ARGB with a custom alpha applied to an RGB triplet. */
    public static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Projects a 7D vector onto a single hue: mechanics → yellow, EM → blue,
     * thermal → red, chemical → green, optical → pink; tensors drift to violet.
     */
    public static int flavor(DimVec v, Quantity.Kind kind) {
        double[] a = v.raw();
        for (int i = 0; i < a.length; i++) a[i] = Math.abs(a[i]);

        double mech = a[0] + a[1] + a[2];
        double em = a[3];
        double therm = a[4];
        double chem = a[5];
        double opt = a[6];
        double total = mech + em + therm + chem + opt;

        if (total < 1e-9) return 0xFFB6C0CE;

        double[][] hues = {{mech, 46}, {em, 212}, {therm, 8}, {chem, 142}, {opt, 320}};
        double x = 0, y = 0;
        for (double[] h : hues) {
            double rad = Math.toRadians(h[1]);
            x += h[0] * Math.cos(rad);
            y += h[0] * Math.sin(rad);
        }
        double hue = Math.toDegrees(Math.atan2(y, x));
        if (hue < 0) hue += 360;

        if (kind == Quantity.Kind.TENSOR || kind == Quantity.Kind.FIELD) {
            hue = (hue * 0.7 + 280 * 0.3) % 360;
        }

        int axes = v.activeAxes();
        double sat = Math.min(0.85, 0.48 + axes * 0.08 + (kind == Quantity.Kind.TENSOR ? 0.08 : 0.0));
        double lig = kind == Quantity.Kind.FIELD ? 0.62 : 0.58;
        return hslToArgb(hue, sat, lig);
    }

    public static int hslToArgb(double h, double s, double l) {
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double hp = h / 60.0;
        double xx = c * (1 - Math.abs(hp % 2 - 1));
        double r = 0, g = 0, b = 0;
        if (hp < 1) {
            r = c;
            g = xx;
        } else if (hp < 2) {
            r = xx;
            g = c;
        } else if (hp < 3) {
            g = c;
            b = xx;
        } else if (hp < 4) {
            g = xx;
            b = c;
        } else if (hp < 5) {
            r = xx;
            b = c;
        } else {
            r = c;
            b = xx;
        }
        double m = l - c / 2;
        int ri = (int) Math.round(Math.max(0, Math.min(1, r + m)) * 255);
        int gi = (int) Math.round(Math.max(0, Math.min(1, g + m)) * 255);
        int bi = (int) Math.round(Math.max(0, Math.min(1, b + m)) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }
}
