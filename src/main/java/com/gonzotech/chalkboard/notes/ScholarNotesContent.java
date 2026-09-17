package com.gonzotech.chalkboard.notes;

import com.gonzotech.chalkboard.notes.ScholarPage.Layout;

import java.util.List;

/**
 * Статичное оглавление «Заметок учёного»: линейный массив страниц в порядке
 * буклета. Боковые вкладки — лишь навигация по главам.
 *
 * <p>Глава I ({@link ScholarChapter#ERA_1}) — дневник Гонзо: 30 страниц
 * (1..30): стартовые 14 + турбина и редстоун («Открытие 1») + атомный пласт
 * (дробилка, измельчитель, металлургия, пресс, завод сплавов, центрифуга,
 * тир-2, логистика II — трубы/узел/предметы — и пароген, ядерная топка —
 * «Открытие 2»). Между текстовыми страницами — пустые страницы-иллюстрации
 * (16, 21, 27): фон-лист под арт без текста.
 *
 * <p>Книга делится на две ЧАСТИ:
 * <ul>
 *   <li><b>линейная</b> — главы-эпохи (I, III, IV, V) делят ОДНУ историю
 *       страниц от 1 до X: стрелки листают насквозь все открытые главы
 *       (в будущем — вплоть до сфер Дайсона в поздних эпохах);</li>
 *   <li><b>отдельная</b> — «Познание мира» ({@link ScholarChapter#ERA_2}):
 *       СВОЯ линейная история от 1 до X по <b>действиям</b> игрока в мире
 *       (флаги {@link ScholarNoteFlags}), а не по «Открытиям»; закрытые
 *       страницы пропускаются навигацией, пока событие не произошло;
 *       стрелки НЕ пересекаются с линейной книгой.</li>
 * </ul>
 *
 * <p>Главы III–V пока пусты (заглушки-вкладки).
 */
public final class ScholarNotesContent {

    private ScholarNotesContent() {
    }

    /** Полный список страниц в порядке буклета (page_N.png ↔ number). */
    public static final List<ScholarPage> PAGES = List.of(
            // 1 — «Привет я» (открыта сразу)
            new ScholarPage(1, ScholarChapter.ERA_1, ScholarUnlock.ALWAYS,
                    "gui.gonzotech.notes.p1.title",
                    "gui.gonzotech.notes.p1.body",
                    List.of(),
                    Layout.TEXT_LEFT),

            // 2 — «Наблюдение» (открыта сразу)
            new ScholarPage(2, ScholarChapter.ERA_1, ScholarUnlock.ALWAYS,
                    "gui.gonzotech.notes.p2.title",
                    "gui.gonzotech.notes.p2.body",
                    List.of(),
                    Layout.TEXT_LEFT),

            // 3 — «Брожение» (наиграно >5 мин) — только иллюстрация на весь лист
            new ScholarPage(3, ScholarChapter.ERA_1, ScholarUnlock.PLAYTIME_5MIN,
                    "gui.gonzotech.notes.p3.title",
                    null,
                    List.of("minecraft:apple", "minecraft:wheat", "minecraft:melon_slice",
                            "minecraft:glow_berries", "minecraft:sweet_berries", "minecraft:bone_meal",
                            "minecraft:wheat_seeds", "gonzotech:the_proto_mash", "gonzotech:the_fruit_mash"),
                    Layout.IMAGE_FULL),

            // 4 — «Энергия» (после «Открытия 1»)
            new ScholarPage(4, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p4.title",
                    "gui.gonzotech.notes.p4.body",
                    List.of("gonzotech:firebox"),
                    Layout.TEXT_LEFT),

            // 5 — «Топка и котёл» (после «Открытия 1») — иллюстрация на весь лист
            new ScholarPage(5, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p5.title",
                    null,
                    List.of("gonzotech:firebox", "gonzotech:boiler"),
                    Layout.IMAGE_FULL),

            // 6 — «Генератор Стирлинга» (после «Открытия 1»)
            new ScholarPage(6, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p6.title",
                    "gui.gonzotech.notes.p6.body",
                    List.of("gonzotech:stirling_generator"),
                    Layout.TEXT_LEFT),

            // 7 — «Конденсация» (после «Открытия 1»)
            new ScholarPage(7, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p7.title",
                    "gui.gonzotech.notes.p7.body",
                    List.of("gonzotech:condenser"),
                    Layout.TEXT_LEFT),

            // 8 — «Замкнутый цикл» (после «Открытия 1»)
            new ScholarPage(8, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p8.title",
                    "gui.gonzotech.notes.p8.body",
                    List.of("gonzotech:pump"),
                    Layout.TEXT_LEFT),

            // 9 — «Электрическая плавка» (после «Открытия 1»)
            new ScholarPage(9, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p9.title",
                    "gui.gonzotech.notes.p9.body",
                    List.of("gonzotech:electric_furnace", "gonzotech:accumulator"),
                    Layout.TEXT_LEFT),

            // 10 — «Логистика» (после «Открытия 1»)
            new ScholarPage(10, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p10.title",
                    "gui.gonzotech.notes.p10.body",
                    List.of("gonzotech:wrench",
                            "gonzotech:first_wire", "gonzotech:first_heat_pipe",
                            "gonzotech:first_water_pipe", "gonzotech:first_steam_pipe",
                            "gonzotech:first_wire_node", "gonzotech:first_heat_node",
                            "gonzotech:first_water_node", "gonzotech:first_steam_node",
                            "gonzotech:first_universal_fluid_pipe",
                            "gonzotech:first_universal_fluid_node"),
                    Layout.TEXT_LEFT),

            // 11 — «Генератор булыжника» (после «Открытия 1»)
            new ScholarPage(11, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p11.title",
                    "gui.gonzotech.notes.p11.body",
                    List.of("gonzotech:cobble_generator"),
                    Layout.TEXT_LEFT),

            // 12 — «Логистика предметов» (после «Открытия 1»)
            new ScholarPage(12, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p12.title",
                    "gui.gonzotech.notes.p12.body",
                    List.of("gonzotech:first_item_pipe", "gonzotech:first_item_node",
                            "gonzotech:item_filter", "gonzotech:item_scavenger"),
                    Layout.TEXT_LEFT),

            // 13 — «Логистика предметов» (после «Открытия 1») — иллюстрация на весь лист
            new ScholarPage(13, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p13.title",
                    null,
                    List.of(),
                    Layout.IMAGE_FULL),

            // 14 — «Универсальный узел» (после «Открытия 1»)
            new ScholarPage(14, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p14.title",
                    "gui.gonzotech.notes.p14.body",
                    List.of("gonzotech:first_universal_node"),
                    Layout.TEXT_LEFT),

            // ─────────── продолжение эпохи I ───────────

            // 15 — «Паровая турбина» (после «Открытия 1»)
            new ScholarPage(15, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p15.title",
                    "gui.gonzotech.notes.p15.body",
                    List.of("gonzotech:turbine_casing", "gonzotech:turbine_rotor"),
                    Layout.TEXT_LEFT),

            // 16 — пустая страница под иллюстрацию турбины (после «Паровой турбины»)
            new ScholarPage(16, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    null, null, List.of(), Layout.IMAGE_FULL),

            // 17 — «Механизмы и редстоун» (после «Открытия 1»)
            new ScholarPage(17, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p17.title",
                    "gui.gonzotech.notes.p17.body",
                    List.of("minecraft:comparator", "gonzotech:firebox",
                            "gonzotech:accumulator", "gonzotech:nuclear_firebox"),
                    Layout.TEXT_LEFT),

            // 18 — «Дробилка руды» (первая запись после «Открытия 2»)
            new ScholarPage(18, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p18.title",
                    "gui.gonzotech.notes.p18.body",
                    List.of("gonzotech:crusher", "minecraft:stone", "minecraft:iron_ore",
                            "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
                            "minecraft:basalt", "minecraft:soul_sand", "minecraft:netherrack",
                            "minecraft:deepslate"),
                    Layout.TEXT_LEFT),

            // 19 — «Измельчитель II» (после «Открытия 2»)
            new ScholarPage(19, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p19.title",
                    "gui.gonzotech.notes.p19.body",
                    List.of("gonzotech:second_grinder", "gonzotech:iron_dust"),
                    Layout.TEXT_LEFT),

            // 20 — «Простая металлургия» (после «Открытия 2»)
            new ScholarPage(20, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p20.title",
                    "gui.gonzotech.notes.p20.body",
                    List.of("gonzotech:iron_dust", "minecraft:coal", "gonzotech:steel_dust"),
                    Layout.TEXT_LEFT),

            // 21 — пустая страница под иллюстрацию металлургии (после «Простой металлургии»)
            new ScholarPage(21, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    null, null, List.of(), Layout.IMAGE_FULL),

            // 22 — «Пресс II» (после «Открытия 2»)
            new ScholarPage(22, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p22.title",
                    "gui.gonzotech.notes.p22.body",
                    List.of("gonzotech:second_press", "gonzotech:plate_form",
                            "gonzotech:ingot_form", "gonzotech:core_form",
                            "gonzotech:flat_punch", "gonzotech:wedge_punch"),
                    Layout.TEXT_LEFT),

            // 23 — «Завод сплавов» (после «Открытия 2»)
            new ScholarPage(23, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p23.title",
                    "gui.gonzotech.notes.p23.body",
                    List.of("gonzotech:second_alloy_foundry", "gonzotech:custom_alloy"),
                    Layout.TEXT_LEFT),

            // 24 — «Центрифуга ЦФ1УР» (после «Открытия 2»)
            new ScholarPage(24, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p24.title",
                    "gui.gonzotech.notes.p24.body",
                    List.of("gonzotech:centrifuge"),
                    Layout.TEXT_LEFT),

            // 25 — «Машины второго поколения» (после «Открытия 2») — без проводa и узла
            // (логистике тир-2 — свои страницы 26-28)
            new ScholarPage(25, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p25.title",
                    "gui.gonzotech.notes.p25.body",
                    List.of("gonzotech:second_accumulator", "gonzotech:second_electric_furnace",
                            "gonzotech:second_pump", "gonzotech:second_cobble_generator"),
                    Layout.TEXT_LEFT),

            // 26 — «Логистика второго уровня» (после «Машины второго поколения»)
            new ScholarPage(26, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p26.title",
                    "gui.gonzotech.notes.p26.body",
                    List.of("gonzotech:second_wire", "gonzotech:second_heat_pipe",
                            "gonzotech:second_water_pipe", "gonzotech:second_steam_pipe",
                            "gonzotech:second_wire_node", "gonzotech:second_heat_node",
                            "gonzotech:second_water_node", "gonzotech:second_steam_node",
                            "gonzotech:second_universal_fluid_pipe",
                            "gonzotech:second_universal_fluid_node"),
                    Layout.TEXT_LEFT),

            // 27 — пустая страница под иллюстрацию логистики второго уровня
            new ScholarPage(27, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    null, null, List.of(), Layout.IMAGE_FULL),

            // 28 — «Логистика предметов 2» (после пустой страницы логистики)
            new ScholarPage(28, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p28.title",
                    "gui.gonzotech.notes.p28.body",
                    List.of("gonzotech:second_item_pipe", "gonzotech:second_item_node",
                            "gonzotech:second_item_filter", "gonzotech:second_item_scavenger"),
                    Layout.TEXT_LEFT),

            // 29 — «Продвинутый парогенератор» (после «Открытия 2»)
            new ScholarPage(29, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p29.title",
                    "gui.gonzotech.notes.p29.body",
                    List.of("gonzotech:steamgen_casing", "gonzotech:steamgen_core"),
                    Layout.TEXT_LEFT),

            // 30 — «Ядерная топка» (после «Открытия 2»)
            new ScholarPage(30, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p30.title",
                    "gui.gonzotech.notes.p30.body",
                    List.of("gonzotech:nuclear_firebox", "gonzotech:uranium_ingot"),
                    Layout.TEXT_LEFT),

            // ─────────── глава II «Познание мира» (по действиям) ───────────

            // 31 — «Редстоун и лава» (по «Открытию 1» — базовое знание о мире);
            // реакция — с БЛОКОМ редстоуна (не с пылью/проводом)
            new ScholarPage(31, ScholarChapter.ERA_2, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p31.title",
                    "gui.gonzotech.notes.p31.body",
                    List.of("gonzotech:crimson_obsidian", "minecraft:redstone_block",
                            "minecraft:lava_bucket"),
                    Layout.TEXT_LEFT),

            // 32 — «Вольфрам, большой абсорбер» (впервые добыт вольфрамовый блок)
            new ScholarPage(32, ScholarChapter.ERA_2, ScholarUnlock.FLAG_WOLFRAM,
                    "gui.gonzotech.notes.p32.title",
                    "gui.gonzotech.notes.p32.body",
                    List.of("gonzotech:tungsten_block", "gonzotech:superdense_ice"),
                    Layout.TEXT_LEFT),

            // 33 — пустая страница под иллюстрацию вольфрамового абсорбера
            // (работа + крафты; вольфрам — ДВЕ страницы)
            new ScholarPage(33, ScholarChapter.ERA_2, ScholarUnlock.FLAG_WOLFRAM,
                    null, null, List.of(), Layout.IMAGE_FULL),

            // 34 — «Цезий, обещание взрыва» (впервые добыта любая форма цезия);
            // текст на ВСЮ страницу, в витрине — все формы цезия
            new ScholarPage(34, ScholarChapter.ERA_2, ScholarUnlock.FLAG_CESIUM,
                    "gui.gonzotech.notes.p34.title",
                    "gui.gonzotech.notes.p34.body",
                    List.of("gonzotech:raw_cesium", "gonzotech:cesium_nugget",
                            "gonzotech:cesium_dust", "gonzotech:cesium_ingot",
                            "gonzotech:cesium_block", "gonzotech:cesium_ore",
                            "gonzotech:deepslate_cesium_ore"),
                    Layout.TEXT_FULL),

            // 35 — «Угасание солнца» (впервые увиден угасший свет)
            new ScholarPage(35, ScholarChapter.ERA_2, ScholarUnlock.FLAG_SUN_FADE,
                    "gui.gonzotech.notes.p35.title",
                    "gui.gonzotech.notes.p35.body",
                    List.of(),
                    Layout.TEXT_FULL)
    );

    /** Доступна ли страница при данном состоянии игрока. */
    public static boolean isUnlocked(ScholarPage page, NotesState state) {
        return page.unlock().isMet(state);
    }

    /** Индекс первой доступной страницы ЛИНЕЙНОЙ книги (эпохи) — стартовый
     *  экран: страница 1 открыта всегда, так что фактически всегда 0. */
    public static int firstUnlockedIndex(NotesState state) {
        for (int i = 0; i < PAGES.size(); i++) {
            if (isUnlocked(PAGES.get(i), state) && !PAGES.get(i).chapter().side()) {
                return i;
            }
        }
        return 0;
    }
}
