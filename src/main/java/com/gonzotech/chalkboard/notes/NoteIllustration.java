package com.gonzotech.chalkboard.notes;

import java.util.List;

/**
 * Данные иллюстрации страницы: шаблон ({@link #kind()}) + наполнение слотов.
 *
 * <p>Предметы по id (namespace-префикс, как в витрине) рендерит GUI в
 * фиксированные слоты шаблона; пустая строка в сетке = ПУСТОЙ слот рецепта
 * (например «пустая форма» у пресса). Нетиповые ингредиенты в сетке —
 * конкретным предметом (тег плах → дубовые доски и т.п.).
 *
 * <p>ЦИКЛ: если заданы {@code leftSequence}/{@code rightSequence} (кадр —
 * сетка+результат) и {@code cycleTicks} > 0, GUI листает кадры по времени
 * (оба окна на одном таймере) — «тикает, меняется».
 *
 * <p>СТРУКТУРА: {@code structure} (nullable) — модель многоблока для панели
 * структуры: подстраницы (изо-вид + слои «вид сверху»), клик-тоггл видимости
 * блоков, тултипы (см. {@link StructureModel} и ScholarNotesScreen).
 *
 * @param kind          шаблон (определяет PNG и раскладку слотов).
 * @param leftGrid      статичная сетка 3×3 (9, слева направо, сверху вниз):
 *                      одиночная справа (CRAFTING_RIGHT), левый рецепт
 *                      (CRAFTING_FULL / CRAFTING_STRUCTURE / CRAFTING_FERMENTATION).
 * @param leftResult    результат левого/одиночного крафта.
 * @param rightGrid     статичный правый рецепт (только CRAFTING_FULL).
 * @param rightResult   результат правого крафта (только CRAFTING_FULL).
 * @param fermInputs    входы брожения, 4 пары (FERMENTATION / CRAFTING_FERMENTATION).
 * @param fermOutputs   выходы брожения, 4 пары (FERMENTATION / CRAFTING_FERMENTATION).
 * @param cycleTicks    период смены кадра в тиках (0 — без цикла).
 * @param leftSequence  кадры левого окна (non-null → цикл; 1 кадр = статично).
 * @param rightSequence кадры правого окна (non-null → цикл; 1 кадр = статично).
 * @param structure     модель структуры для панели (CRAFTING_STRUCTURE / STRUCTURE_RIGHT).
 */
public record NoteIllustration(
        NoteIllustrationKind kind,
        List<String> leftGrid,
        String leftResult,
        List<String> rightGrid,
        String rightResult,
        List<String> fermInputs,
        List<String> fermOutputs,
        int cycleTicks,
        List<Craft> leftSequence,
        List<Craft> rightSequence,
        StructureModel structure
) {

    /** Кадр крафта: сетка 3×3 + результат. */
    public record Craft(List<String> grid, String result) {
    }

    /** Один крафт справа (страница «текст слева»). */
    public static NoteIllustration craftingRight(List<String> grid, String result) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_RIGHT,
                grid, result, null, null, null, null, 0, null, null, null);
    }

    /** Два статичных крафта: левый + правый (страница на всю ширину). */
    public static NoteIllustration craftingFull(List<String> gridLeft, String resultLeft,
                                                List<String> gridRight, String resultRight) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_FULL,
                gridLeft, resultLeft, gridRight, resultRight, null, null, 0, null, null, null);
    }

    /** Два ЦИКЛИЧЕСКИХ окна (оба на одном таймере {@code cycleTicks} тиков). */
    public static NoteIllustration craftingFullCycling(int cycleTicks,
                                                       List<Craft> leftSequence,
                                                       List<Craft> rightSequence) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_FULL,
                null, null, null, null, null, null, cycleTicks, leftSequence, rightSequence, null);
    }

    /** Крафт слева + панель структуры справа (модель — null, пока пустая). */
    public static NoteIllustration craftingStructure(List<String> grid, String result) {
        return craftingStructure(grid, result, null);
    }

    /** Крафт слева + панель структуры справа с моделью (подстраницы). */
    public static NoteIllustration craftingStructure(List<String> grid, String result,
                                                     StructureModel structure) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_STRUCTURE,
                grid, result, null, null, null, null, 0, null, null, structure);
    }

    /** Только панель структуры справа (модель — null, пока пустая). */
    public static NoteIllustration structureRight() {
        return structureRight(null);
    }

    /** Только панель структуры справа с моделью (подстраницы). */
    public static NoteIllustration structureRight(StructureModel structure) {
        return new NoteIllustration(NoteIllustrationKind.STRUCTURE_RIGHT,
                null, null, null, null, null, null, 0, null, null, structure);
    }

    /** Брожение: 4 пары «вход → выход». */
    public static NoteIllustration fermentation(List<String> inputs, List<String> outputs) {
        return new NoteIllustration(NoteIllustrationKind.FERMENTATION,
                null, null, null, null, inputs, outputs, 0, null, null, null);
    }

    /** Крафт фруктового сусла слева + пары брожения справа. */
    public static NoteIllustration craftingFermentation(List<String> grid, String result,
                                                        List<String> inputs, List<String> outputs) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_FERMENTATION,
                grid, result, null, null, inputs, outputs, 0, null, null, null);
    }
}
