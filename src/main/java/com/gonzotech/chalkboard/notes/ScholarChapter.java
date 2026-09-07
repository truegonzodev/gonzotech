package com.gonzotech.chalkboard.notes;

/**
 * Глава-«эпоха» «Заметок учёного» — вертикальная вкладка слева от рамки буклета.
 * Всего 5 эпох (каждая со временем вместит ~20 страниц). Сейчас наполнена только
 * первая; остальные — заглушки-плейсхолдеры (точные тексты/иконки автор задаст
 * позже). Иконка первой эпохи — сам предмет «Заметки учёного».
 */
public enum ScholarChapter {
    ERA_1("chapter_era1", "gonzotech:scholar_notes"),
    ERA_2("chapter_era2", "gonzotech:firebox"),
    ERA_3("chapter_era3", "gonzotech:stirling_generator"),
    ERA_4("chapter_era4", "gonzotech:first_wire"),
    ERA_5("chapter_era5", "gonzotech:chalkboard");

    private final String key;
    private final String iconItemId;

    ScholarChapter(String key, String iconItemId) {
        this.key = key;
        this.iconItemId = iconItemId;
    }

    /** lang-ключ названия главы (вкладки). */
    public String titleKey() {
        return "gui.gonzotech.notes." + key;
    }

    /** id предмета/блока для иконки корешка вкладки. */
    public String iconItemId() {
        return iconItemId;
    }
}
