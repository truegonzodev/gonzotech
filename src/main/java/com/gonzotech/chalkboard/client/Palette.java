package com.gonzotech.chalkboard.client;

import com.gonzotech.chalkboard.core.DimVec;
import com.gonzotech.chalkboard.core.Quantity;

/**
 * Green chalkboard theme palette with white chalk strokes and high-contrast text.
 */
public final class Palette {

    private Palette() {
    }

    public static final int BG = 0xFF4B7C43;         // Chalkboard Green #4b7c43
    public static final int PANEL = 0xFF648952;      // Panel Green #648952
    public static final int PANEL_SOFT = 0xFF3B6334; // Darker Panel Green #3b6334
    public static final int STROKE = 0xFFF5F5F5;     // Off-white Chalk Border #f5f5f5
    public static final int TEXT = 0xFFFFFFFF;       // Pure White Text #FFFFFF
    public static final int TEXT_DIM = 0xFFE2E8F0;   // Bright White Chalk Text
    public static final int TEXT_FAINT = 0xFFCBD5E1; // Faint White Chalk Text
    public static final int CYAN = 0xFF7DD3FC;       // Light Blue Chalk
    public static final int AMBER = 0xFFFDE047;      // Yellow Chalk
    public static final int ROSE = 0xFFFCA5A5;       // Pink/Red Chalk
    public static final int GREEN = 0xFF86EFAC;      // Bright Green Chalk
    public static final int VIOLET = 0xFFC084FC;     // Violet Chalk

    /** Discrete love gradient, identical bands to the web build. */
    public static int love(double score) {
        if (score < 20) return 0xFFFCA5A5;
        if (score < 40) return 0xFFFDBA74;
        if (score < 60) return 0xFFFDE047;
        if (score < 80) return 0xFFA3E635;
        if (score < 95) return 0xFF4ADE80;
        return 0xFFFFFFFF;
    }

    public static int weightColor(int weight) {
        return switch (weight) {
            case 0 -> 0xFFCBD5E1;
            case 1 -> 0xFF4ADE80;
            case 2 -> 0xFF7DD3FC;
            default -> 0xFFFDE047;
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

        if (total < 1e-9) return 0xFFF5F5F5;

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
        double lig = kind == Quantity.Kind.FIELD ? 0.65 : 0.60;
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
