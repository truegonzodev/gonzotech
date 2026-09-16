package com.gonzotech.chalkboard.notes;

/**
 * Id флагов «действий» для вкладки «Познание мира» (глава II «Заметок учёного»).
 * Страницы этой главы открываются не по «Открытиям», а по конкретным событиям
 * в мире: игрок добыл цезий — открылась страница про цезий; увидел угасание
 * Солнца — открылась страница про него. Флаги живут в
 * {@link com.gonzotech.chalkboard.progress.PlayerChalkboardProgress} (per-player,
 * персистентно) и синхронизируются в
 * {@link com.gonzotech.chalkboard.network.NotesNetwork.NotesDataPayload}.
 */
public final class ScholarNoteFlags {

    /** Игрок впервые получил в инвентарь хотя бы одну форму цезия. */
    public static final String CESIUM = "cesium";

    /** Игрок впервые получил в инвентарь вольфрамовый блок (абсорбер). */
    public static final String WOLFRAM = "wolfram";

    /** Игрок впервые оказался свидетелем угасания Солнца (SunState → GONE). */
    public static final String SUN_FADE = "sun_fade";

    private ScholarNoteFlags() {
    }
}
