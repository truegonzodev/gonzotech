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
 * сетка+результат) или {@code flatView} с >1 кадром и {@code cycleTicks} > 0,
 * GUI листает кадры по времени (всё на одном таймере) — «тикает, меняется».
 *
 * <p>СТРУКТУРА — два вида (взаимоисключаемы):
 * <ul>
 *   <li>{@code structure} — модель многоблока: подстраницы (сборка / слои
 *       «вид сверху», подписи «Сборка (1)»/«Слой (N)»);</li>
 *   <li>{@code flatView} — ПЛОСКИЙ вид 2D-сетки («Вид сверху»/«Вид сбоку»),
 *       1+ кадра, тикающие; точка привязки — центр сетки.</li>
 * </ul>
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
 * @param flatView      плоский вид структуры («Вид сверху»/«Вид сбоку»): кадры
 *                      2D-сетки (CRAFTING_STRUCTURE / STRUCTURE_RIGHT).
 * @param deckView      плоская «колода» структуры (STRUCTURE_RIGHT): стопка
 *                      слоёв-подстраниц, нефиксированные клетки случайно из
 *                      пула («тикают»).
 * @param craftCaptionKey lang-ключ подписи над крафтом вместо стандартной
 *                      «Создание» (только CRAFTING_RIGHT; {@code null} —
 *                      стандартная).
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
        StructureModel structure,
        FlatView flatView,
        DeckView deckView,
        String craftCaptionKey
) {

    /** Подписи плоских видов (lang). */
    public static final String CAPTION_VIEW_TOP = "gui.gonzotech.notes.illustration.view_top";
    public static final String CAPTION_VIEW_SIDE = "gui.gonzotech.notes.illustration.view_side";

    /** Подпись «Завод сплавов» вместо «Создания» (страницы foundry-сплавов). */
    public static final String CAPTION_FOUNDRY = "gui.gonzotech.notes.illustration.foundry";

    /** Кадр крафта: сетка 3×3 + результат. */
    public record Craft(List<String> grid, String result) {
    }

    /**
     * Плоский вид структуры («Вид сверху» / «Вид сбоку»): кадры 2D-сетки.
     * Кадр — РОВНЯЯ строка id предметов (row-major: строки сверху вниз,
     * в строке — слева направо), пустая строка = пустая клетка;
     * {@code cols} — ширина сетки (длина строки), число строк = id / cols.
     * 1 кадр — статично; 2+ кадра и {@code cycleTicks} > 0 — «тикает»
     * (общий таймер, как цикл крафта).
     */
    public record FlatView(int cols, List<List<String>> frames, String captionKey) {
    }

    /**
     * Слой плоской «колонды» структуры: РОВНЯЯ строка id (row-major: строки
     * сверху вниз, в строке — слева направо). Клетка с пустой строкой НЕ
     * ФИКСИРОВАНА — рисуется случайно из {@code pool} (перемешивается каждые
     * {@code cycleTicks} — «тикает»); {@code pool} = null — случайности нет.
     */
    public record DeckLayer(List<String> fixed, List<String> pool) {
        public DeckLayer(List<String> fixed) {
            this(fixed, null);
        }
    }

    /**
     * Плоская «колода» структуры: стопка слоёв = подстраницы (СНИЗУ ВВЕРХ),
     * каждый слой — сетка {@code cols}×строк. Нефиксированные клетки слоя
     * случайно из пула; стрелки листают слои, таймер перемешивает.
     * {@code guaranteedId} + {@code minGuaranteed} — среди случайных клеток
     * ВСЕЙ колоды гарантируется минимум столько клеток с этим id (например,
     * ядра парогена всегда видны, а не только «драгоценные» блоки).
     */
    public record DeckView(int cols, List<DeckLayer> layers, String captionKey,
                           String guaranteedId, int minGuaranteed) {
        public int subpageCount() {
            return layers.size();
        }
    }

    /** Один крафт справа (страница «текст слева»). */
    public static NoteIllustration craftingRight(List<String> grid, String result) {
        return craftingRight(grid, result, null);
    }

    /** Один крафт справа со СВОЕЙ подписью над сеткой (например, «Завод сплавов»). */
    public static NoteIllustration craftingRight(List<String> grid, String result, String captionKey) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_RIGHT,
                grid, result, null, null, null, null, 0, null, null, null, null, null,
                captionKey);
    }

    /** Два статичных крафта: левый + правый (страница на всю ширину). */
    public static NoteIllustration craftingFull(List<String> gridLeft, String resultLeft,
                                                List<String> gridRight, String resultRight) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_FULL,
                gridLeft, resultLeft, gridRight, resultRight, null, null, 0, null, null, null, null, null, null);
    }

    /** Два ЦИКЛИЧЕСКИХ окна (оба на одном таймере {@code cycleTicks} тиков). */
    public static NoteIllustration craftingFullCycling(int cycleTicks,
                                                       List<Craft> leftSequence,
                                                       List<Craft> rightSequence) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_FULL,
                null, null, null, null, null, null, cycleTicks, leftSequence, rightSequence, null, null, null, null);
    }

    /** Крафт слева + панель структуры справа (наполнение — null, пока пустая). */
    public static NoteIllustration craftingStructure(List<String> grid, String result) {
        return craftingStructure(grid, result, null);
    }

    /** Крафт слева + панель структуры справа с моделью (подстраницы). */
    public static NoteIllustration craftingStructure(List<String> grid, String result,
                                                     StructureModel structure) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_STRUCTURE,
                grid, result, null, null, null, null, 0, null, null, structure, null, null, null);
    }

    /** Крафт слева + ПЛОСКАЯ структура справа («Вид сверху», 1+ кадр). */
    public static NoteIllustration craftingStructure(List<String> grid, String result, int cols,
                                                     List<List<String>> frames, String captionKey) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_STRUCTURE,
                grid, result, null, null, null, null, 0, null, null, null,
                new FlatView(cols, frames, captionKey), null, null);
    }

    /** Только панель структуры справа (наполнение — null, пока пустая). */
    public static NoteIllustration structureRight() {
        return structureRight(null);
    }

    /** Только панель структуры справа с моделью (подстраницы). */
    public static NoteIllustration structureRight(StructureModel structure) {
        return new NoteIllustration(NoteIllustrationKind.STRUCTURE_RIGHT,
                null, null, null, null, null, null, 0, null, null, structure, null, null, null);
    }

    /** Только панель структуры справа: плоский вид, один статичный кадр. */
    public static NoteIllustration structureRightFlat(int cols, List<String> frame, String captionKey) {
        return structureRightFlat(0, cols, List.of(frame), captionKey);
    }

    /** Только панель структуры справа: плоский вид, 2+ кадра — «тикает»
     *  ({@code cycleTicks} тиков на кадр). */
    public static NoteIllustration structureRightFlat(int cycleTicks, int cols, List<List<String>> frames,
                                                      String captionKey) {
        return new NoteIllustration(NoteIllustrationKind.STRUCTURE_RIGHT,
                null, null, null, null, null, null, cycleTicks, null, null, null,
                new FlatView(cols, frames, captionKey), null, null);
    }

    /** Только панель структуры справа: плоская «колода» — стопка слоёв
     *  (подстраницы снизу вверх); нефиксированные клетки случайно из пула,
     *  {@code cycleTicks} > 0 — перемешиваются («тикают»). */
    public static NoteIllustration structureRightDeck(int cycleTicks, int cols,
                                                      List<DeckLayer> layers, String captionKey,
                                                      String guaranteedId, int minGuaranteed) {
        return new NoteIllustration(NoteIllustrationKind.STRUCTURE_RIGHT,
                null, null, null, null, null, null, cycleTicks, null, null, null, null,
                new DeckView(cols, layers, captionKey, guaranteedId, minGuaranteed), null);
    }

    /** Брожение: 4 пары «вход → выход». */
    public static NoteIllustration fermentation(List<String> inputs, List<String> outputs) {
        return new NoteIllustration(NoteIllustrationKind.FERMENTATION,
                null, null, null, null, inputs, outputs, 0, null, null, null, null, null, null);
    }

    /** Крафт фруктового сусла слева + пары брожения справа. */
    public static NoteIllustration craftingFermentation(List<String> grid, String result,
                                                        List<String> inputs, List<String> outputs) {
        return new NoteIllustration(NoteIllustrationKind.CRAFTING_FERMENTATION,
                grid, result, null, null, inputs, outputs, 0, null, null, null, null, null, null);
    }
}
