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
 *       впервые совершает соответствующее событие в мире);</li>
 *   <li><b>составное (по «И»)</b> — {@link #SUN_EVENT_AND_DISCOVERY_2}: нужны ОБА
 *       условия сразу (встреченный багровый день И активированное «Открытие 2»);
 *       тот же гейт стоит на крафте солнечных часов (автор 22.09.2026).</li>
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
    FLAG_DISCOVERY_3,
    /** Открывается при наступлении ПЕРВОГО суневента (багрового дня). */
    FLAG_SUN_EVENT,
    /**
     * Составное условие «И» (автор 22.09.2026): игрок УЖЕ видел багровый день
     * И активировал «Открытие 2». Страница с солнечными часами открывается по нему
     * же, что и физический крафт часов.
     */
    SUN_EVENT_AND_DISCOVERY_2;

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
            case FLAG_SUN_EVENT -> state.hasFlag(ScholarNoteFlags.SUN_EVENT);
            case SUN_EVENT_AND_DISCOVERY_2 ->
                    state.hasFlag(ScholarNoteFlags.SUN_EVENT) && state.tier2Unlocked();
        };
    }
}
