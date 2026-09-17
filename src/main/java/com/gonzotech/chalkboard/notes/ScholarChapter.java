package com.gonzotech.chalkboard.notes;

/**
 * Глава-«эпоха» «Заметок учёного» — боковая вкладка слева от рамки буклета.
 *
 * <p>Две КНИГИ:
 * <ul>
 *   <li><b>линейная</b> — четыре эпохи ({@link #ERA_1}, {@link #ERA_3},
 *       {@link #ERA_4}, {@link #ERA_5}) делят ОДНУ историю страниц от 1 до X:
 *       стрелки листают насквозь все открытые главы (в будущем — вплоть до
 *       сфер Дайсона в поздних эпохах);</li>
 *   <li><b>отдельная</b> — «Познание мира» ({@link #ERA_2}): СВОЯ линейная
 *       история от 1 до X по действиям игрока (флаги {@link ScholarNoteFlags});
 *       навигация не пересекается с линейной книгой, вкладка отделена от
 *       четырёх эпох.</li>
 * </ul>
 *
 * <p>У каждой главы свой фон-панель контейнера ({@link #backgroundPath()}).
 */
public enum ScholarChapter {
    ERA_1("chapter_era1", "gonzotech:scholar_notes", "notes_bg_era1", false),
    // Глава II «Познание мира»: отдельная книга, страницы открываются по действиям
    // игрока в мире (флаги ScholarNoteFlags), а не по «Открытиям». Иконка — багровый обсидиан.
    ERA_2("chapter_era2", "gonzotech:crimson_obsidian", "notes_bg_era2", true),
    ERA_3("chapter_era3", "gonzotech:stirling_generator", "notes_bg_era3", false),
    ERA_4("chapter_era4", "gonzotech:first_wire", "notes_bg_era4", false),
    ERA_5("chapter_era5", "gonzotech:chalkboard", "notes_bg_era5", false);

    private final String key;
    private final String iconItemId;
    private final String backgroundName;
    /** true — глава из отдельной книги («Познание мира»), не входит в линейную историю эпох. */
    private final boolean side;

    ScholarChapter(String key, String iconItemId, String backgroundName, boolean side) {
        this.key = key;
        this.iconItemId = iconItemId;
        this.backgroundName = backgroundName;
        this.side = side;
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

    /** Глава отдельной книги («Познание мира») — листуется своей линейной историей. */
    public boolean side() {
        return side;
    }
}
