package com.gonzotech.chalkboard.notes;

/**
 * Класс иллюстрации страницы «Заметок учёного» — переиспользуемый ШАБЛОН,
 * а не личная картинка на страницу (старая схема page_N.png отменена).
 *
 * <p>Шаблон — ПРОЗРАЧНЫЙ PNG 256×200 (или 512×400 — код сэмплирует в окно
 * страницы), наложенный поверх фона главы ({@code notes_bg_eraN.png}): в нём
 * только графика — панели, сетка, стрелки. Предметы в слотах и подписи
 * («Создание», «Структура») рендерит GUI (см.
 * {@link com.gonzotech.chalkboard.client.ScholarNotesScreen}): предметы — как
 * на витрине (с hover-тултипами), подписи — шрифтом из lang (локализуемо,
 * в PNG не запечено).
 *
 * <p>Раскладка слотов у каждого шаблона фиксирована в абсолютных координатах
 * страницы (256×200, от левого верхнего угла) — в {@code ScholarNotesScreen}.
 */
public enum NoteIllustrationKind {

    /** Сетка крафта 3×3 + результат — ПРАВАЯ половина (страницы «текст слева»). */
    CRAFTING_RIGHT("page_crafting_right.png"),

    /** Две сетки крафта 3×3 + результаты — левая и правая (страница на всю ширину). */
    CRAFTING_FULL("page_crafting_full.png"),

    /** Сетка крафта 3×3 + результат слева, панель структуры справа. */
    CRAFTING_STRUCTURE("page_crafting_structure.png"),

    /** Панель структуры справа (раскладка слотов структур — по мере описания). */
    STRUCTURE_RIGHT("page_structure_right.png"),

    /** Брожение: 4 пары «вход → выход» со стрелками (стр. «Брожение»). */
    FERMENTATION("page_fermentation.png");

    private final String textureName;

    NoteIllustrationKind(String textureName) {
        this.textureName = textureName;
    }

    /** Имя файла шаблона в {@code textures/gui/notes/}. */
    public String textureName() {
        return textureName;
    }
}
