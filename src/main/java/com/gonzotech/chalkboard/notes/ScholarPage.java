package com.gonzotech.chalkboard.notes;

import java.util.List;

/**
 * Одна страница «Заметок учёного» (вариант 2 — фон/схемы = рисованный PNG на
 * страницу, весь ТЕКСТ рисуется через шрифт ради локализации).
 *
 * <p>Буклет — это просто линейный массив страниц; боковые вкладки-«главы» лишь
 * навигация. Файлы иллюстраций нумеруются по порядку: {@code page_1.png},
 * {@code page_2.png}, … (см. {@link #backgroundPath()}).
 *
 * <p>Разблокировка идёт по {@link ScholarUnlock} (сразу / наиграно >5 мин /
 * после «Открытия 1»).
 *
 * @param number       порядковый номер страницы (1-based) — задаёт {@code page_N.png}.
 * @param chapter      глава-владелец (вкладка слева).
 * @param unlock       условие разблокировки страницы.
 * @param titleKey     lang-ключ заголовка страницы (рисуется жирным).
 * @param bodyKey      lang-ключ тела страницы; {@code null} для страниц-иллюстраций.
 * @param showcaseItems id предметов/блоков (namespace c префиксом) для нижней витрины.
 * @param layout       раскладка страницы (текст во всю ширину / слева / только картинка).
 */
public record ScholarPage(
        int number,
        ScholarChapter chapter,
        ScholarUnlock unlock,
        String titleKey,
        String bodyKey,
        List<String> showcaseItems,
        Layout layout
) {
    /** Раскладка содержимого страницы. */
    public enum Layout {
        /** Текст во всю ширину страницы. */
        TEXT_FULL,
        /** Текст в левой половине; правая — под иллюстрацию из PNG. */
        TEXT_LEFT,
        /** Только иллюстрация во весь лист (текста нет). */
        IMAGE_FULL
    }

    /** Путь PNG-иллюстрации страницы: {@code textures/gui/notes/page_N.png}. */
    public String backgroundPath() {
        return "textures/gui/notes/page_" + number + ".png";
    }

    /** Есть ли у страницы текстовое тело. */
    public boolean hasBody() {
        return bodyKey != null && layout != Layout.IMAGE_FULL;
    }
}
