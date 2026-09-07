package com.gonzotech.chalkboard.notes;

/**
 * Условие разблокировки страницы «Заметок учёного». Клиент вычисляет доступность
 * по данным {@code NotesDataPayload} (наигранное время + разблокирован ли tier 1).
 */
public enum ScholarUnlock {
    /** Открыта сразу при создании мира. */
    ALWAYS,
    /** Открывается, когда в мире наиграно больше 5 минут (6000 тиков). */
    PLAYTIME_5MIN,
    /** Открывается после активации «Открытия 1» (attachment: recipe tier 1). */
    DISCOVERY_1;

    /** Порог наигранного времени в тиках для {@link #PLAYTIME_5MIN}. */
    public static final long PLAYTIME_THRESHOLD_TICKS = 5L * 60L * 20L; // 5 мин

    /** Доступна ли страница при данном состоянии игрока. */
    public boolean isMet(long playtimeTicks, boolean tier1Unlocked) {
        return switch (this) {
            case ALWAYS -> true;
            case PLAYTIME_5MIN -> playtimeTicks >= PLAYTIME_THRESHOLD_TICKS;
            case DISCOVERY_1 -> tier1Unlocked;
        };
    }
}
