package com.gonzotech.chalkboard.notes;

import java.util.List;

/**
 * Одна страница «Заметок учёного» (вариант 2 — фон/схемы = PNG, весь ТЕКСТ
 * рисуется через шрифт ради локализации).
 *
 * <p>Буклет — это просто линейный массив страниц; боковые вкладки-«главы»
 * лишь навигация.
 *
 * <p>База страницы — фон главы ({@code notes_bg_eraN.png}, бумага); личная
 * картинка на страницу (старая {@code page_N.png}) УБРАНА. Вместо неё —
 * опциональный ШАБЛОН иллюстрации ({@link #illustration()}, см.
 * {@link NoteIllustrationKind}): прозрачный PNG-оверлей (сетки/панели) +
 * предметы, которые GUI рендерит в слоты как на витрине (hover-тултипы).
 *
 * <p>Разблокировка идёт по {@link ScholarUnlock} (сразу / наиграно >5 мин /
 * после «Открытия» / по действию).
 *
 * @param number       порядковый номер страницы (1-based) — только для порядка в буклете.
 * @param chapter      глава-владелец (вкладка слева).
 * @param unlock       условие разблокировки страницы.
 * @param titleKey     lang-ключ заголовка страницы (рисуется жирным);
 *                     {@code null} — пустая страница (текста нет вообще).
 * @param bodyKey      lang-ключ тела страницы; {@code null} для страниц без текста.
 * @param showcaseItems id предметов/блоков (namespace c префиксом) для нижней витрины.
 * @param layout       раскладка ТЕКСТА (во всю ширину / слева).
 * @param illustration шаблон-иллюстрация + наполнение слотов; {@code null} — чистый лист.
 */
public record ScholarPage(
        int number,
        ScholarChapter chapter,
        ScholarUnlock unlock,
        String titleKey,
        String bodyKey,
        List<String> showcaseItems,
        Layout layout,
        NoteIllustration illustration
) {
    /** Раскладка текстового тела страницы. */
    public enum Layout {
        /** Текст во всю ширину страницы. */
        TEXT_FULL,
        /** Текст в левой половине; правая — под иллюстрацию (шаблон). */
        TEXT_LEFT
    }

    /** Есть ли у страницы текстовое тело. */
    public boolean hasBody() {
        return bodyKey != null;
    }
}
