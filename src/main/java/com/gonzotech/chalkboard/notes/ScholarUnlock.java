package com.gonzotech.chalkboard.notes;

/**
 * Условие разблокировки страницы «Заметок учёного». Клиент вычисляет доступность
 * по {@link NotesState} (наигранное время + активированные «Открытия» + флаги
 * действий «Познания мира»).
 * <p>
 * Три семейства условий:
 * <ul>
 *   <li><b>стартовые</b> — {@link #ALWAYS} / {@link #PLAYTIME_5MIN};</li>
 *   <li><b>по «Открытиям»</b> — {@link #DISCOVERY_1} / {@link #DISCOVERY_2}
 *       (эпоха I: машины и логистика);</li>
 *   <li><b>по действию</b> — {@link #FLAG_CESIUM} / {@link #FLAG_WOLFRAM} /
 *       {@link #FLAG_SUN_FADE} (познание мира: страница открывается, когда игрок
 *       впервые совершает соответствующее событие в мире).</li>
 * </ul>
 */
public enum ScholarUnlock {
    /** Открыта сразу при создании мира. */
    ALWAYS,
    /** Открывается, когда в мире наиграно больше 5 минут (6000 тиков). */
    PLAYTIME_5MIN,
    /** Открывается после активации «Открытия 1» (attachment: recipe tier 1). */
    DISCOVERY_1,
    /** Открывается после активации «Открытия 2» (attachment: recipe tier 2). */
    DISCOVERY_2,
    /** Открывается, когда игрок впервые получает форму цезия в инвентарь. */
    FLAG_CESIUM,
    /** Открывается, когда игрок впервые получает вольфрамовый блок в инвентарь. */
    FLAG_WOLFRAM,
    /** Открывается, когда игрок впервые видит угасание Солнца. */
    FLAG_SUN_FADE,
    /** Открывается, когда в инвентаре впервые появился аттачмент «Открытие 3» (предмет discovery_3). */
    FLAG_DISCOVERY_3;

    /** Порог наигранного времени в тиках для {@link #PLAYTIME_5MIN}. */
    public static final long PLAYTIME_THRESHOLD_TICKS = 5L * 60L * 20L; // 5 мин

    /** Доступна ли страница при данном состоянии игрока. */
    public boolean isMet(NotesState state) {
        return switch (this) {
            case ALWAYS -> true;
            case PLAYTIME_5MIN -> state.playtimeTicks() >= PLAYTIME_THRESHOLD_TICKS;
            case DISCOVERY_1 -> state.tier1Unlocked();
            case DISCOVERY_2 -> state.tier2Unlocked();
            case FLAG_CESIUM -> state.hasFlag(ScholarNoteFlags.CESIUM);
            case FLAG_WOLFRAM -> state.hasFlag(ScholarNoteFlags.WOLFRAM);
            case FLAG_SUN_FADE -> state.hasFlag(ScholarNoteFlags.SUN_FADE);
            case FLAG_DISCOVERY_3 -> state.hasFlag(ScholarNoteFlags.DISCOVERY_3);
        };
    }
}
