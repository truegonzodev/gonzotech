package com.gonzotech.chalkboard.notes;

import java.util.List;

/**
 * Данные иллюстрации страницы: шаблон ({@link #kind()}) + наполнение слотов.
 *
 * <p>Предметы по id (namespace-префикс, как в витрине) рендерит GUI в
 * фиксированные слоты шаблона; пустая строка в сетке = ПУСТОЙ слот рецепта
 * (например «пустая форма» у пресса). {@code null}-списки — слоты,
 * принадлежащие другим режимам шаблона (не используются).
 *
 * @param kind        шаблон (определяет PNG и раскладку слотов).
 * @param leftGrid    сетка 3×3 (9 штук, слева направо, сверху вниз):
 *                    одиночная справа (CRAFTING_RIGHT), левый рецепт
 *                    (CRAFTING_FULL), рецепт (CRAFTING_STRUCTURE).
 * @param leftResult  результат левого/одиночного крафта.
 * @param rightGrid   правый рецепт (только CRAFTING_FULL).
 * @param rightResult результат правого крафта (только CRAFTING_FULL).
 * @param fermInputs  входы брожения, 4 пары (только FERMENTATION).
 * @param fermOutputs выходы брожения, 4 пары (только FERMENTATION).
 */
public record NoteIllustration(
        NoteIllustrationKind kind,
        List<String> leftGrid,
        String leftResult,
        List<String> rightGrid,
        String rightResult,
        List<String> fermInputs,
        List<String> fermOutputs
) {

    /** Один крафт справа (страница «текст слева»). */
    public static NoteIllustration craftingRight(List<String> grid, String result) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_RIGHT,
                grid, result, null, null, null, null);
    }

    /** Два крафта: левый + правый (страница на всю ширину). */
    public static NoteIllustration craftingFull(List<String> gridLeft, String resultLeft,
                                                List<String> gridRight, String resultRight) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_FULL,
                gridLeft, resultLeft, gridRight, resultRight, null, null);
    }

    /** Крафт слева + панель структуры справа (структурные слоты — позже). */
    public static NoteIllustration craftingStructure(List<String> grid, String result) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_STRUCTURE,
                grid, result, null, null, null, null);
    }

    /** Только панель структуры справа (раскладка слотов будет по каждой структуре). */
    public static NoteIllustration structureRight() {
        return new NoteIllustration(NoteIllustrationKind.STRUCTURE_RIGHT,
                null, null, null, null, null, null);
    }

    /** Брожение: 4 пары «вход → выход». */
    public static NoteIllustration fermentation(List<String> inputs, List<String> outputs) {
        return new NoteIllustration(NoteIllustrationKind.FERMENTATION,
                null, null, null, null, inputs, outputs);
    }
}
