package com.gonzotech.chalkboard.notes;

/**
 * Глава-«эпоха» «Заметок учёного» — вертикальная вкладка слева от рамки буклета.
 * Всего 5 эпох (каждая со временем вместит ~20 страниц). Сейчас наполнена только
 * первая; остальные — заглушки-плейсхолдеры (точные тексты/иконки автор задаст
 * позже). Иконка первой эпохи — сам предмет «Заметки учёного».
 *
 * <p>У каждой эпохи свой фон-панель контейнера ({@link #backgroundPath()}):
 * {@code notes_bg_era1.png}…{@code notes_bg_era5.png} — так каждая глава читается
 * визуально по-своему.
 */
public enum ScholarChapter {
    ERA_1("chapter_era1", "gonzotech:scholar_notes", "notes_bg_era1"),
    ERA_2("chapter_era2", "gonzotech:firebox", "notes_bg_era2"),
    ERA_3("chapter_era3", "gonzotech:stirling_generator", "notes_bg_era3"),
    ERA_4("chapter_era4", "gonzotech:first_wire", "notes_bg_era4"),
    ERA_5("chapter_era5", "gonzotech:chalkboard", "notes_bg_era5");

    private final String key;
    private final String iconItemId;
    private final String backgroundName;

    ScholarChapter(String key, String iconItemId, String backgroundName) {
        this.key = key;
        this.iconItemId = iconItemId;
        this.backgroundName = backgroundName;
    }

    /** lang-ключ названия главы (вкладки). */
    public String titleKey() {
        return "gui.gonzotech.notes." + key;
    }

    /** id предмета/блока для иконки корешка вкладки. */
    public String iconItemId() {
        return iconItemId;
    }

    /** Путь к фону-панели контейнера этой главы (textures/...). */
    public String backgroundPath() {
        return "textures/gui/notes/" + backgroundName + ".png";
    }
}

