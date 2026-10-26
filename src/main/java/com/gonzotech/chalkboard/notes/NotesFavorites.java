package com.gonzotech.chalkboard.notes;

import java.util.ArrayList;
import java.util.List;

/** Ordered stable page IDs. No client-global settings and no page-index persistence. */
public final class NotesFavorites {
    private final List<Integer> ids = new ArrayList<>();

    public NotesFavorites(List<Integer> saved) {
        for (int id : saved) set(id, true);
    }

    public List<Integer> pages() { return List.copyOf(ids); }

    /** Idempotent intent: duplicate requests cannot flip a bookmark back. */
    public boolean set(int id, boolean favorite) {
        if (ScholarNotesContent.indexOfNumber(id) < 0) return false;
        if (!favorite) return ids.remove(Integer.valueOf(id));
        if (ids.contains(id)) return false;
        return ids.add(id);
    }

    public static final int FLAG_HEIGHT = 14;
    public static final int GAP = 4;
    public static final int STEP = FLAG_HEIGHT + GAP;

    public static double maxScroll(int count, int viewHeight) {
        return Math.max(0, count * (double) STEP - GAP - viewHeight);
    }

    public static double clampScroll(double scroll, int count, int viewHeight) {
        return Math.max(0, Math.min(scroll, maxScroll(count, viewHeight)));
    }

    /** Fade only the edge with off-screen entries; zero at the clipped boundary. */
    public static float edgeAlpha(int pixelY, int viewHeight, double scroll, double maxScroll) {
        float alpha = 1f;
        if (scroll > 0) alpha = Math.min(alpha, (pixelY + 0.5f) / 10f);
        if (scroll < maxScroll) alpha = Math.min(alpha, (viewHeight - pixelY - 0.5f) / 10f);
        return Math.max(0f, Math.min(1f, alpha));
    }
}
