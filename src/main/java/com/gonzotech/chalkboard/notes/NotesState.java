package com.gonzotech.chalkboard.notes;

import java.util.Set;

/**
 * Мгновенное состояние «Заметок учёного» для гейтинга страниц: наигранное
 * время, активированные «Открытия» (тиры рецептов) и действия, которые игрок
 * уже совершал (флаги «Познания мира»: добыл цезий, увидел угасание Солнца…).
 * <p>
 * Клиент собирает это из {@link com.gonzotech.chalkboard.network.NotesNetwork.NotesDataPayload}.
 */
public record NotesState(long playtimeTicks, boolean tier1Unlocked, boolean tier2Unlocked,
                         Set<String> noteFlags) {

    /** Есть ли у игрока флаг действия (см. {@link ScholarNoteFlags}). */
    public boolean hasFlag(String flag) {
        return flag != null && noteFlags.contains(flag);
    }
}
