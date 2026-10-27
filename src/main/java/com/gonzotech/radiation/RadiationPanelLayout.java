package com.gonzotech.radiation;

/** Logical GUI pixels, independently testable on the dedicated server. */
public final class RadiationPanelLayout {
    public static final int BACKGROUND = 0xF0100010;
    public static final int LIGHT_EDGE = 0xFFCCE82C;
    public static final int DARK_EDGE = 0xFF8AA51D;
    public record Bounds(int x, int y, int width, int height) {
        public int textX() { return x + 4; }
        public int textY() { return y + 6; }
    }
    private RadiationPanelLayout() {}

    /** Unknown is not a positive rate: never paint a placeholder during slot fences. */
    public static boolean visible(Double total) {
        return total != null && Double.isFinite(total) && total >= 1;
    }

    public static Bounds place(int textWidth, int lineHeight, int tooltipX, int tooltipY,
                               int tooltipHeight, int screenWidth, int screenHeight) {
        int width = textWidth + 12, height = lineHeight + 10;
        int x = Math.max(0, Math.min(tooltipX - 4, screenWidth - width));
        // Vanilla tooltip outer top is text Y - 4. Leave three clear pixels.
        int y = tooltipY - 4 - 3 - height;
        if (y < 0) y = tooltipY + tooltipHeight + 4 + 3;
        y = Math.max(0, Math.min(y, screenHeight - height));
        return new Bounds(x, y, width, height);
    }
}
