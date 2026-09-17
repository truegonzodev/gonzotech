package com.gonzotech.chalkboard.notes;

import com.gonzotech.chalkboard.notes.ScholarPage.Layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Статичное оглавление «Заметок учёного»: линейный массив страниц в порядке
 * буклета. Боковые вкладки — лишь навигация по главам.
 *
 * <p>Глава I ({@link ScholarChapter#ERA_1}) — дневник Гонзо: 30 страниц
 * (1..30): стартовые 14 + турбина и редстоун («Открытие 1») + атомный пласт
 * (дробилка, измельчитель, металлургия, пресс, завод сплавов, центрифуга,
 * тир-2, логистика II — трубы/узел/предметы — и пароген, ядерная топка —
 * «Открытие 2»). Между текстовыми страницами — пустые страницы (16, 21, 27)
 * под будущие иллюстрации.
 *
 * <p>Иллюстрации — не личные PNG на страницу, а ШАБЛОНЫ
 * ({@link NoteIllustrationKind}): прозрачный оверлей (сетка крафта / панель
 * структуры / пары брожения) поверх фона главы; предметы в слотах и подписи
 * рендерит GUI (локализуемо, hover-тултипы). Привязаны страницы 1–30:
 * крафты машин/труб/узлов (реальные рецепты из data/gonzotech/recipe),
 * брожение (стр. 3, гибрид крафт+пара), структуры (пока пустые панели —
 * раскладка слотов по каждой структуре позже), стр. 27 — цикл кадров
 * (правое окно «остальные трубы тир-2» тикает и меняется).
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

    /** Полный список страниц в порядке буклета (35). Иллюстрации — шаблоны;
     *  сетки крафта — реальные рецепты из data/gonzotech/recipe (теги — конкретным
     *  предметом: плахи → дубовые доски; shapeless-рецепты — по порядку слотов). */
    public static final List<ScholarPage> PAGES = List.of(
            // 1 — «Привет я» (открыта сразу) — справа крафт доски резонанса
            new ScholarPage(1, ScholarChapter.ERA_1, ScholarUnlock.ALWAYS,
                    "gui.gonzotech.notes.p1.title",
                    "gui.gonzotech.notes.p1.body",
                    List.of("gonzotech:chalkboard"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:oak_planks", "minecraft:calcite", "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:calcite", "minecraft:oak_planks", "minecraft:oak_planks", "minecraft:calcite", "minecraft:oak_planks"), "gonzotech:chalkboard")),

            // 2 — «Наблюдение» (открыта сразу) — справа крафт прото-сусла
            new ScholarPage(2, ScholarChapter.ERA_1, ScholarUnlock.ALWAYS,
                    "gui.gonzotech.notes.p2.title",
                    "gui.gonzotech.notes.p2.body",
                    List.of("minecraft:apple", "minecraft:wheat", "minecraft:melon_slice",
                            "minecraft:glow_berries", "minecraft:sweet_berries", "minecraft:bone_meal",
                            "minecraft:wheat_seeds", "gonzotech:the_proto_mash", "gonzotech:the_fruit_mash"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:wheat", "minecraft:wheat_seeds", "minecraft:bone_meal", "", "", "", "", "", ""), "gonzotech:the_proto_mash")),

            // 3 — «Брожение» (наиграно >5 мин) — слева крафт фруктового сусла,
            // справа ферментация (гибрид-шаблон); сусло в паре выходов
            new ScholarPage(3, ScholarChapter.ERA_1, ScholarUnlock.PLAYTIME_5MIN,
                    "gui.gonzotech.notes.p3.title",
                    null,
                    List.of("minecraft:apple", "minecraft:wheat", "minecraft:melon_slice",
                            "minecraft:glow_berries", "minecraft:sweet_berries", "minecraft:bone_meal",
                            "minecraft:wheat_seeds", "gonzotech:the_fruit_mash"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingFermentation(List.of("minecraft:apple", "minecraft:wheat_seeds", "minecraft:bone_meal", "", "", "", "", "", ""), "gonzotech:the_fruit_mash",
                            List.of("minecraft:apple", "minecraft:melon_slice",
                                    "minecraft:glow_berries", "minecraft:sweet_berries"),
                            List.of("gonzotech:the_fruit_mash", "gonzotech:the_fruit_mash",
                                    "gonzotech:the_fruit_mash", "gonzotech:the_fruit_mash"))),

            // 4 — «Энергия» (после «Открытия 1») — шаблон: крафт справа (топка)
            new ScholarPage(4, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p4.title",
                    "gui.gonzotech.notes.p4.body",
                    List.of("gonzotech:firebox"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:furnace", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot"), "gonzotech:firebox")),

            // 5 — «Топка и котёл» (после «Открытия 1») — шаблон: крафт слева + структура
            // (одна подстраница «Сборка (1)» — котёл над топкой, сетка с гэпом)
            new ScholarPage(5, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p5.title",
                    null,
                    List.of("gonzotech:firebox", "gonzotech:boiler"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingStructure(List.of("gonzotech:cast_iron_ingot", "minecraft:iron_trapdoor", "gonzotech:cast_iron_ingot", "minecraft:copper_ingot", "minecraft:bucket", "minecraft:copper_ingot", "gonzotech:cast_iron_ingot", "minecraft:piston", "gonzotech:cast_iron_ingot"), "gonzotech:boiler",
                            StructureModel.fireboxBoiler())),

            // 6 — «Генератор Стирлинга» (после «Открытия 1») — справа крафт Стирлинга
            new ScholarPage(6, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p6.title",
                    "gui.gonzotech.notes.p6.body",
                    List.of("gonzotech:stirling_generator"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:copper_ingot", "minecraft:piston", "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:piston", "minecraft:copper_ingot"), "gonzotech:stirling_generator")),

            // 7 — «Конденсация» (после «Открытия 1») — справа крафт конденсатора
            new ScholarPage(7, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p7.title",
                    "gui.gonzotech.notes.p7.body",
                    List.of("gonzotech:condenser"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:iron_bars", "minecraft:iron_bars", "minecraft:iron_bars", "minecraft:iron_bars", "minecraft:copper_ingot", "minecraft:iron_bars", "minecraft:iron_bars", "minecraft:iron_bars", "minecraft:iron_bars"), "gonzotech:condenser")),

            // 8 — «Замкнутый цикл» (после «Открытия 1») — справа крафт помпы
            new ScholarPage(8, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p8.title",
                    "gui.gonzotech.notes.p8.body",
                    List.of("gonzotech:pump"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:iron_ingot", "minecraft:bucket", "minecraft:bucket", "minecraft:bucket", "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:iron_ingot"), "gonzotech:pump")),

            // 9 — «Электрическая плавка» (после «Открытия 1») — справа эл. печь 1
            new ScholarPage(9, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p9.title",
                    "gui.gonzotech.notes.p9.body",
                    List.of("gonzotech:electric_furnace", "gonzotech:accumulator"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "gonzotech:pseudo_coil", "minecraft:furnace", "gonzotech:pseudo_coil", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot"), "gonzotech:electric_furnace")),

            // 10 — «Логистика» (после «Открытия 1») — справа крафт гаечного ключа
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
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("", "gonzotech:cast_iron_ingot", "minecraft:lever", "minecraft:iron_ingot", "minecraft:redstone", "minecraft:iron_ingot", "minecraft:iron_sword", "minecraft:iron_ingot", ""), "gonzotech:wrench")),

            // 11 — «Генератор булыжника» (после «Открытия 1») — справа генератор 1
            new ScholarPage(11, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p11.title",
                    "gui.gonzotech.notes.p11.body",
                    List.of("gonzotech:cobble_generator"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:oak_planks", "minecraft:piston", "minecraft:chest", "minecraft:bucket", "", "minecraft:bucket", "gonzotech:cast_iron_ingot", "minecraft:piston", "minecraft:lever"), "gonzotech:cobble_generator")),

            // 12 — «Логистика предметов» (после «Открытия 1») — справа фильтр 1
            new ScholarPage(12, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p12.title",
                    "gui.gonzotech.notes.p12.body",
                    List.of("gonzotech:first_item_pipe", "gonzotech:first_item_node",
                            "gonzotech:item_filter", "gonzotech:item_scavenger"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("", "minecraft:copper_ingot", "", "minecraft:copper_ingot", "minecraft:chest", "minecraft:copper_ingot", "", "minecraft:hopper", ""), "gonzotech:item_filter")),

            // 13 — «Логистика предметов» (после «Открытия 1») — слева крафт отсеивателя,
            // справа плоская структура «Вид сверху»: отсеиватель + предметные трубы
            // + фильтр (статичный); на витрине отсеиватель
            new ScholarPage(13, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p13.title",
                    null,
                    List.of("gonzotech:item_scavenger"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingStructure(List.of("", "minecraft:copper_ingot", "minecraft:stick", "minecraft:cobblestone", "minecraft:dropper", "minecraft:cobblestone", "", "minecraft:copper_ingot", ""), "gonzotech:item_scavenger", 3,
                            List.of(List.of("", "gonzotech:item_scavenger", "gonzotech:first_item_pipe", "gonzotech:first_item_pipe", "gonzotech:item_filter", "", "", "gonzotech:first_item_pipe", "")),
                            NoteIllustration.CAPTION_VIEW_TOP)),

            // 14 — «Универсальный узел» (после «Открытия 1») — справа крафт узла
            new ScholarPage(14, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p14.title",
                    "gui.gonzotech.notes.p14.body",
                    List.of("gonzotech:first_universal_node"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:first_wire_node", "", "gonzotech:first_universal_fluid_pipe", "minecraft:copper_ingot", "minecraft:chest", "minecraft:copper_ingot", "gonzotech:first_heat_pipe", "", "gonzotech:first_item_pipe"), "gonzotech:first_universal_node")),

            // ─────────── продолжение эпохи I ───────────

            // 15 — «Паровая турбина» (после «Открытия 1») — справа крафт корпуса
            new ScholarPage(15, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p15.title",
                    "gui.gonzotech.notes.p15.body",
                    List.of("gonzotech:turbine_casing", "gonzotech:turbine_rotor"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:iron_bars", "gonzotech:cast_iron_ingot", "gonzotech:calcium_ingot", "minecraft:iron_bars", "minecraft:iron_ingot", "minecraft:iron_bars", "gonzotech:calcium_ingot", "gonzotech:cast_iron_ingot", "minecraft:iron_bars"), "gonzotech:turbine_casing")),

            // 16 — пустая страница после турбины — слева крафт ротора,
            // справа структура: МИНИМАЛЬНАЯ турбина 3×3×3 (24 корпуса, ротор
            // в центре, паровой узел слева и узел провода справа в среднем ряду
            // передней грани) — 3 подстраницы-СЛОЯ: Нижний/Средний/Верхний слой
            // Заголовок = продублированный «Паровая турбина» (стр. 15) —
            // страница-продолжение про турбину (автор 2026-09-18)
            new ScholarPage(16, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p15.title", null,
                    List.of("gonzotech:turbine_rotor"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingStructure(List.of("minecraft:iron_nugget", "gonzotech:cast_iron_ingot", "gonzotech:calcium_nugget", "gonzotech:cast_iron_ingot", "gonzotech:pseudo_coil", "gonzotech:cast_iron_ingot", "gonzotech:calcium_nugget", "gonzotech:cast_iron_ingot", "minecraft:iron_nugget"), "gonzotech:turbine_rotor",
                            StructureModel.turbineMinimum())),

            // 17 — «Механизмы и редстоун» (после «Открытия 1») — справа плоская
            // структура «Вид сверху»: [энергохранилище][компаратор][редстоун-пыль]
            new ScholarPage(17, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p17.title",
                    "gui.gonzotech.notes.p17.body",
                    List.of("minecraft:comparator", "gonzotech:firebox",
                            "gonzotech:accumulator", "gonzotech:nuclear_firebox"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.structureRightFlat(3,
                            List.of("gonzotech:accumulator", "minecraft:comparator", "minecraft:redstone"),
                            NoteIllustration.CAPTION_VIEW_TOP)),

            // 18 — «Дробилка руды» (первая запись после «Открытия 2») — справа дробилка
            new ScholarPage(18, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p18.title",
                    "gui.gonzotech.notes.p18.body",
                    List.of("gonzotech:crusher", "minecraft:stone", "minecraft:iron_ore",
                            "minecraft:andesite", "minecraft:diorite", "minecraft:granite",
                            "minecraft:basalt", "minecraft:soul_sand", "minecraft:netherrack",
                            "minecraft:deepslate"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:iron_ingot", "minecraft:flint", "minecraft:iron_ingot", "minecraft:piston", "minecraft:hopper", "minecraft:piston", "minecraft:iron_ingot", "minecraft:redstone", "minecraft:iron_ingot"), "gonzotech:crusher")),

            // 19 — «Измельчитель II» (после «Открытия 2») — справа измельчитель
            new ScholarPage(19, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p19.title",
                    "gui.gonzotech.notes.p19.body",
                    List.of("gonzotech:second_grinder", "gonzotech:iron_dust", "gonzotech:copper_dust"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:iron_ingot", "gonzotech:cast_iron_block", "minecraft:iron_trapdoor", "minecraft:flint", "minecraft:bucket", "minecraft:diamond", "gonzotech:first_wire_node", "minecraft:iron_block", "gonzotech:pseudo_coil"), "gonzotech:second_grinder")),

            // 20 — «Простая металлургия» (после «Открытия 2») — справа стальная пыль
            new ScholarPage(20, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p20.title",
                    "gui.gonzotech.notes.p20.body",
                    List.of("gonzotech:iron_dust", "minecraft:coal", "gonzotech:steel_dust"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:iron_dust", "gonzotech:iron_dust", "gonzotech:iron_dust", "minecraft:coal", "", "", "", "", ""), "gonzotech:steel_dust")),

            // 21 — пустая страница после металлургии — слева ферро-пыль, справа нержавейка;
            // витрина — пыли сплавов
            // Заголовок = продублированный «Простая металлургия» (стр. 20) —
            // страница-продолжение про сплавы (автор 2026-09-18)
            new ScholarPage(21, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p20.title", null,
                    List.of("gonzotech:cantor_dust", "gonzotech:ferromagnetic_dust",
                            "gonzotech:stainless_steel_dust", "gonzotech:nitinol_dust",
                            "gonzotech:invar_dust", "gonzotech:vr20_dust",
                            "gonzotech:alnico_dust", "gonzotech:telluride_dust"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingFull(List.of("gonzotech:iron_dust", "gonzotech:iron_dust", "gonzotech:iron_dust", "gonzotech:cobalt_dust", "gonzotech:nickel_dust", "", "", "", ""), "gonzotech:ferromagnetic_dust",
                            List.of("gonzotech:steel_dust", "gonzotech:steel_dust", "gonzotech:steel_dust", "gonzotech:steel_dust", "gonzotech:chromium_dust", "gonzotech:chromium_dust", "gonzotech:nickel_dust", "", ""), "gonzotech:stainless_steel_dust")),

            // 22 — «Пресс II» (после «Открытия 2») — справа пресс
            new ScholarPage(22, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p22.title",
                    "gui.gonzotech.notes.p22.body",
                    List.of("gonzotech:second_press", "gonzotech:plate_form",
                            "gonzotech:ingot_form", "gonzotech:core_form",
                            "gonzotech:flat_punch", "gonzotech:wedge_punch"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:cast_iron_ingot", "minecraft:minecart", "gonzotech:pseudo_coil", "minecraft:piston", "gonzotech:boiler", "minecraft:piston", "gonzotech:cast_iron_block", "gonzotech:cast_iron_block", "gonzotech:cast_iron_block"), "gonzotech:second_press")),

            // 23 — «Завод сплавов» (после «Открытия 2») — справа завод сплавов
            new ScholarPage(23, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p23.title",
                    "gui.gonzotech.notes.p23.body",
                    List.of("gonzotech:second_alloy_foundry", "gonzotech:custom_alloy"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:steel_plate", "gonzotech:tungsten_ingot", "gonzotech:steel_plate", "gonzotech:inductive_module", "gonzotech:boiler", "gonzotech:inductive_module", "gonzotech:first_wire_node", "gonzotech:steel_plate", "gonzotech:iron_plate"), "gonzotech:second_alloy_foundry")),

            // 24 — «Центрифуга ЦФ1УР» (после «Открытия 2») — справа центрифуга
            new ScholarPage(24, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p24.title",
                    "gui.gonzotech.notes.p24.body",
                    List.of("gonzotech:centrifuge", "gonzotech:lead_nugget",
                            "gonzotech:rhenium_nugget", "gonzotech:neodymium_nugget",
                            "gonzotech:bismuth_nugget", "gonzotech:lithium_nugget",
                            "gonzotech:radium_nugget"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("minecraft:bucket", "gonzotech:turbine_rotor", "gonzotech:steel_plate", "gonzotech:inductive_module", "minecraft:bucket", "gonzotech:inductive_module", "gonzotech:first_wire_node", "gonzotech:steel_plate", "gonzotech:iron_plate"), "gonzotech:centrifuge")),

            // 25 — «Машины второго поколения» (после «Открытия 2») — справа индуктивный модуль
            new ScholarPage(25, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p25.title",
                    "gui.gonzotech.notes.p25.body",
                    List.of("gonzotech:second_accumulator", "gonzotech:second_electric_furnace",
                            "gonzotech:second_pump", "gonzotech:second_cobble_generator",
                            "gonzotech:coil", "gonzotech:inductive_module"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:condenser", "gonzotech:ferromagnetic_ingot", "gonzotech:copper_wire", "gonzotech:coil", "gonzotech:ferromagnetic_ingot", "gonzotech:coil", "gonzotech:copper_wire", "gonzotech:ferromagnetic_ingot", "gonzotech:condenser"), "gonzotech:inductive_module")),

            // 26 — «Логистика второго уровня» (после «Машины второго поколения»)
            // — справа универсальный узел 2
            new ScholarPage(26, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p26.title",
                    "gui.gonzotech.notes.p26.body",
                    List.of("gonzotech:second_wire", "gonzotech:second_heat_pipe",
                            "gonzotech:second_water_pipe", "gonzotech:second_steam_pipe",
                            "gonzotech:second_wire_node", "gonzotech:second_heat_node",
                            "gonzotech:second_water_node", "gonzotech:second_steam_node",
                            "gonzotech:second_universal_fluid_pipe",
                            "gonzotech:second_universal_fluid_node", "gonzotech:second_universal_node"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:second_wire_node", "", "gonzotech:second_universal_fluid_pipe", "gonzotech:nickel_plate", "minecraft:hopper", "gonzotech:nickel_plate", "gonzotech:second_heat_pipe", "", "gonzotech:second_item_pipe"), "gonzotech:second_universal_node")),

            // 27 — пустая страница после логистики 2 — ЦИКЛ: слева провод 2 (4 варианта:
            // медь/алюминий/золото/серебро), справа остальные трубы тир-2 (3 с на рецепт)
            // Заголовок = продублированный «Логистика второго поколения» (стр. 26)
            // — страница-продолжение про логистику тир-2 (автор 2026-09-18)
            new ScholarPage(27, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p26.title", null, List.of(), Layout.TEXT_LEFT,
                    NoteIllustration.craftingFullCycling(60,
                            List.of(
                                    new NoteIllustration.Craft(List.of("minecraft:paper", "minecraft:paper", "minecraft:paper", "gonzotech:copper_wire", "gonzotech:copper_wire", "gonzotech:copper_wire", "gonzotech:nickel_plate", "gonzotech:nickel_plate", "minecraft:paper"), "gonzotech:second_wire"),
                                    new NoteIllustration.Craft(List.of("minecraft:paper", "minecraft:paper", "minecraft:paper", "gonzotech:aluminum_wire", "gonzotech:aluminum_wire", "gonzotech:aluminum_wire", "gonzotech:nickel_plate", "gonzotech:nickel_plate", "gonzotech:nickel_plate"), "gonzotech:second_wire"),
                                    new NoteIllustration.Craft(List.of("minecraft:paper", "minecraft:paper", "minecraft:paper", "gonzotech:gold_wire", "gonzotech:gold_wire", "gonzotech:gold_wire", "gonzotech:nickel_plate", "minecraft:paper", "minecraft:paper"), "gonzotech:second_wire"),
                                    new NoteIllustration.Craft(List.of("minecraft:paper", "minecraft:paper", "minecraft:paper", "gonzotech:silver_wire", "gonzotech:silver_wire", "gonzotech:silver_wire", "gonzotech:nickel_plate", "minecraft:paper", "minecraft:paper"), "gonzotech:second_wire")),
                            List.of(
                                    new NoteIllustration.Craft(List.of("", "gonzotech:cast_iron_ingot", "", "gonzotech:steel_ingot", "minecraft:water_bucket", "gonzotech:steel_ingot", "", "gonzotech:cast_iron_ingot", ""), "gonzotech:second_heat_pipe"),
                                    new NoteIllustration.Craft(List.of("", "", "", "gonzotech:cast_iron_ingot", "minecraft:iron_trapdoor", "gonzotech:cast_iron_ingot", "gonzotech:steel_ingot", "", ""), "gonzotech:second_water_pipe"),
                                    new NoteIllustration.Craft(List.of("", "", "gonzotech:steel_ingot", "gonzotech:cast_iron_ingot", "minecraft:iron_trapdoor", "gonzotech:cast_iron_ingot", "", "", ""), "gonzotech:second_steam_pipe"),
                                    new NoteIllustration.Craft(List.of("", "gonzotech:steel_ingot", "", "minecraft:hopper", "gonzotech:cast_iron_ingot", "minecraft:chest", "", "gonzotech:steel_ingot", ""), "gonzotech:second_item_pipe"),
                                    new NoteIllustration.Craft(List.of("", "minecraft:iron_trapdoor", "gonzotech:steel_ingot", "gonzotech:cast_iron_ingot", "minecraft:bucket", "gonzotech:cast_iron_ingot", "gonzotech:steel_ingot", "", ""), "gonzotech:second_universal_fluid_pipe")))),

            // 28 — «Логистика предметов 2» (после пустой страницы логистики)
            // — справа отсеиватель 2
            new ScholarPage(28, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p28.title",
                    "gui.gonzotech.notes.p28.body",
                    List.of("gonzotech:second_item_pipe", "gonzotech:second_item_node",
                            "gonzotech:second_item_filter", "gonzotech:second_item_scavenger"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("", "gonzotech:cast_iron_ingot", "", "minecraft:chest", "minecraft:iron_trapdoor", "minecraft:hopper", "", "gonzotech:cast_iron_ingot", ""), "gonzotech:second_item_scavenger")),

            // 29 — «Продвинутый парогенератор» (после «Открытия 2») — справа ядро парогена
            new ScholarPage(29, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p29.title",
                    "gui.gonzotech.notes.p29.body",
                    List.of("gonzotech:steamgen_casing", "gonzotech:steamgen_core"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:cast_iron_ingot", "minecraft:iron_trapdoor", "minecraft:iron_bars", "gonzotech:steel_plate", "gonzotech:boiler", "gonzotech:steel_plate", "gonzotech:condenser", "minecraft:iron_trapdoor", "gonzotech:cast_iron_ingot"), "gonzotech:steamgen_core")),

            // 30 — «Сердце парогенератора» (после «Продвинутого парогенератора»,
            // перед «Ядерной топкой») — текст слева; справа «колода»: 5 слоёв
            // 5×5×5 (снизу вверх): низ/верх — чистые корпуса; три средних
            // «тикают» (случайно: ядро/драгоценный блок/пустота); в самом
            // среднем три порта вместо корпусов: пар (спереди), вода (слева),
            // GTH (справа)
            new ScholarPage(30, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p30.title",
                    "gui.gonzotech.notes.p30.body",
                    List.of("gonzotech:steamgen_casing", "gonzotech:steamgen_core",
                            "gonzotech:first_steam_node", "gonzotech:first_water_node",
                            "gonzotech:first_heat_node", "gonzotech:platinum_block"),
                    Layout.TEXT_LEFT,
                    structureSteamGen()),

            // 31 — «Ядерная топка» (после «Открытия 2») — справа ядерная топка
            new ScholarPage(31, ScholarChapter.ERA_1, ScholarUnlock.DISCOVERY_2,
                    "gui.gonzotech.notes.p31.title",
                    "gui.gonzotech.notes.p31.body",
                    List.of("gonzotech:nuclear_firebox", "gonzotech:uranium_ingot"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(List.of("gonzotech:lead_ingot", "gonzotech:steel_plate", "gonzotech:lead_ingot", "gonzotech:lead_block", "gonzotech:firebox", "gonzotech:tungsten_ingot", "gonzotech:lead_ingot", "gonzotech:steel_plate", "gonzotech:lead_ingot"), "gonzotech:nuclear_firebox")),

            // ─────────── глава II «Познание мира» (по действиям) ───────────

            // 32 — «Редстоун и лава» (по «Открытию 1» — базовое знание о мире);
            // реакция — с БЛОКОМ редстоуна (не с пылью/проводом); справа «Вид сверху»
            // (тикает, 2 кадра): ведро лавы+блок редстоуна+поршень → багряный
            // обсидиан (гонзotech — ванильный без предметной формы)+головка поршня
            new ScholarPage(32, ScholarChapter.ERA_2, ScholarUnlock.DISCOVERY_1,
                    "gui.gonzotech.notes.p32.title",
                    "gui.gonzotech.notes.p32.body",
                    List.of("gonzotech:crimson_obsidian", "minecraft:redstone_block",
                            "minecraft:lava_bucket"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.structureRightFlat(60, 3,
                            List.of(
                                    List.of("minecraft:lava_bucket", "", "minecraft:lava_bucket", "", "minecraft:redstone_block", "", "", "minecraft:piston", ""),
                                    List.of("gonzotech:crimson_obsidian", "minecraft:redstone_block", "gonzotech:crimson_obsidian", "", "minecraft:piston_head", "", "", "minecraft:piston", "")),
                            NoteIllustration.CAPTION_VIEW_TOP)),

            // 33 — «Вольфрам, большой абсорбер» (впервые добыт вольфрамовый блок);
            // справа «Вид сбоку» (тикает, 4 кадра): теплотруба 1 → узел 1 →
            // теплотруба 2 → узел 2; вольфрам посередине; сверхплотный лёд
            // появляется/исчезает
            new ScholarPage(33, ScholarChapter.ERA_2, ScholarUnlock.FLAG_WOLFRAM,
                    "gui.gonzotech.notes.p33.title",
                    "gui.gonzotech.notes.p33.body",
                    List.of("gonzotech:tungsten_block", "gonzotech:superdense_ice"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.structureRightFlat(60, 3,
                            List.of(
                                    List.of("gonzotech:first_heat_pipe", "gonzotech:tungsten_block", "gonzotech:superdense_ice"),
                                    List.of("gonzotech:first_heat_node", "gonzotech:tungsten_block", ""),
                                    List.of("gonzotech:second_heat_pipe", "gonzotech:tungsten_block", "gonzotech:superdense_ice"),
                                    List.of("gonzotech:second_heat_node", "gonzotech:tungsten_block", "")),
                            NoteIllustration.CAPTION_VIEW_SIDE)),

            // 34 — «Цезий, обещание взрыва» (впервые добыта любая форма цезия);
            // текст на ВСЮ страницу, в витрине — все формы цезия
            new ScholarPage(34, ScholarChapter.ERA_2, ScholarUnlock.FLAG_CESIUM,
                    "gui.gonzotech.notes.p34.title",
                    "gui.gonzotech.notes.p34.body",
                    List.of("gonzotech:raw_cesium", "gonzotech:cesium_nugget",
                            "gonzotech:cesium_dust", "gonzotech:cesium_ingot",
                            "gonzotech:cesium_block", "gonzotech:cesium_ore",
                            "gonzotech:deepslate_cesium_ore"),
                    Layout.TEXT_FULL,
                    null),

            // 35 — «Угасание солнца» (впервые увиден угасший свет)
            new ScholarPage(35, ScholarChapter.ERA_2, ScholarUnlock.FLAG_SUN_FADE,
                    "gui.gonzotech.notes.p35.title",
                    "gui.gonzotech.notes.p35.body",
                    List.of(),
                    Layout.TEXT_FULL,
                    null),

            // 36–40 — раздел «Глубокая металлургия» (автор 2026-09-18):
            // открывается аттачментом «Открытие 3» (предмет discovery_3).
            // 36 — введение: что такое статы материалов, где их видно;
            // 37–40 — «несмешиваемые» пресеты завода (есть только там,
            // пыли/самородков у них нет в каталоге крафта): кортен, стеллит,
            // витрелой, полупроводник. Иллюстраций нет — TEXT_FULL + витрина.
            new ScholarPage(36, ScholarChapter.ERA_2, ScholarUnlock.FLAG_DISCOVERY_3,
                    "gui.gonzotech.notes.p36.title",
                    "gui.gonzotech.notes.p36.body",
                    List.of("gonzotech:second_alloy_foundry", "gonzotech:custom_alloy",
                            "gonzotech:steel_ingot", "gonzotech:corten_steel_ingot"),
                    Layout.TEXT_FULL,
                    null),
            new ScholarPage(37, ScholarChapter.ERA_2, ScholarUnlock.FLAG_DISCOVERY_3,
                    "gui.gonzotech.notes.p37.title",
                    "gui.gonzotech.notes.p37.body",
                    List.of("gonzotech:corten_steel_ingot", "minecraft:iron_ingot",
                            "minecraft:copper_ingot", "gonzotech:chromium_ingot",
                            "gonzotech:nickel_ingot"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(
                            List.of("minecraft:iron_ingot", "minecraft:copper_ingot",
                                    "gonzotech:chromium_ingot", "gonzotech:nickel_ingot",
                                    "", "", "", "", ""),
                            "gonzotech:corten_steel_ingot",
                            NoteIllustration.CAPTION_FOUNDRY)),
            new ScholarPage(38, ScholarChapter.ERA_2, ScholarUnlock.FLAG_DISCOVERY_3,
                    "gui.gonzotech.notes.p38.title",
                    "gui.gonzotech.notes.p38.body",
                    List.of("gonzotech:stellite_ingot", "gonzotech:cobalt_ingot",
                            "gonzotech:chromium_ingot", "gonzotech:tungsten_ingot",
                            "gonzotech:neodymium_ingot"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(
                            List.of("gonzotech:cobalt_ingot", "gonzotech:chromium_ingot",
                                    "gonzotech:tungsten_ingot", "minecraft:coal",
                                    "gonzotech:neodymium_ingot", "", "", "", ""),
                            "gonzotech:stellite_ingot",
                            NoteIllustration.CAPTION_FOUNDRY)),
            new ScholarPage(39, ScholarChapter.ERA_2, ScholarUnlock.FLAG_DISCOVERY_3,
                    "gui.gonzotech.notes.p39.title",
                    "gui.gonzotech.notes.p39.body",
                    List.of("gonzotech:vitreloy_ingot", "minecraft:diamond",
                            "gonzotech:zirconium_ingot", "gonzotech:ferromagnetic_ingot",
                            "gonzotech:nickel_ingot"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(
                            List.of("minecraft:diamond", "gonzotech:nickel_ingot",
                                    "gonzotech:ferromagnetic_ingot", "gonzotech:zirconium_ingot",
                                    "", "", "", "", ""),
                            "gonzotech:vitreloy_ingot",
                            NoteIllustration.CAPTION_FOUNDRY)),
            new ScholarPage(40, ScholarChapter.ERA_2, ScholarUnlock.FLAG_DISCOVERY_3,
                    "gui.gonzotech.notes.p40.title",
                    "gui.gonzotech.notes.p40.body",
                    List.of("gonzotech:semiconductor_ingot", "gonzotech:silicon",
                            "gonzotech:neodymium_ingot", "minecraft:gold_ingot",
                            "minecraft:copper_ingot"),
                    Layout.TEXT_LEFT,
                    NoteIllustration.craftingRight(
                            List.of("gonzotech:neodymium_ingot", "gonzotech:radium_ingot",
                                    "gonzotech:silicon", "minecraft:gold_ingot",
                                    "minecraft:copper_ingot", "minecraft:clay",
                                    "", "", ""),
                            "gonzotech:semiconductor_ingot",
                            NoteIllustration.CAPTION_FOUNDRY))
    );

    /**
     * Слой «колонды» 5×5: 16 клеток внешнего кольца (по часовой, начиная с
     * (0,0)) + середина 3×3 — зафиксирована ({@code innerFixed}) либо не
     * фиксирована (случайно из {@code pool}).
     */
    private static NoteIllustration.DeckLayer deckLayer5(List<String> ring, String innerFixed, List<String> pool) {
        List<String> out = new ArrayList<>();
        String in = innerFixed == null ? "" : innerFixed;
        out.add(ring.get(0)); out.add(ring.get(1)); out.add(ring.get(2)); out.add(ring.get(3)); out.add(ring.get(4));
        out.add(ring.get(15)); out.add(in); out.add(in); out.add(in); out.add(ring.get(5));
        out.add(ring.get(14)); out.add(in); out.add(in); out.add(in); out.add(ring.get(6));
        out.add(ring.get(13)); out.add(in); out.add(in); out.add(in); out.add(ring.get(7));
        out.add(ring.get(12)); out.add(ring.get(11)); out.add(ring.get(10)); out.add(ring.get(9)); out.add(ring.get(8));
        return new NoteIllustration.DeckLayer(out, pool);
    }

    /**
     * «Колода» продвинутого парогенератора 5×5×5 (стр. 30): 5 слоёв снизу
     * вверх. Низ и верх — чистые корпуса; три средних — «сердце»: середина
     * 3×3 тикает (случайно: ядро / драгоценный блок-теплообменник / пустота),
     * кольцо — корпус; в самом среднем кольце три порта вместо корпусов:
     * паровой (2,0 — спереди), GTH-тепловой (4,2 — справа), водяной (0,2 — слева).
     */
    private static NoteIllustration structureSteamGen() {
        String casing = "gonzotech:steamgen_casing";
        List<String> plain = new ArrayList<>();
        for (int i = 0; i < 16; i++) plain.add(casing);
        List<String> nodes = new ArrayList<>(plain);
        nodes.set(2, "gonzotech:first_steam_node");
        nodes.set(6, "gonzotech:first_heat_node");
        nodes.set(14, "gonzotech:first_water_node");
        // Пул «драгоценных»: ядро + ванильные и мод-блоки металлов (авторский
        // состав 2026-09-18; плутоний убран) + пусто. Любая клетка — визуально,
        // валидатору машины пул не обязателен.
        List<String> pool = List.of(
                "gonzotech:steamgen_core",
                "minecraft:gold_block", "minecraft:iron_block",
                "minecraft:copper_block", "minecraft:diamond_block",
                "gonzotech:tungsten_block", "gonzotech:alnico_block",
                "gonzotech:nitinol_block", "gonzotech:telluride_block",
                "gonzotech:bismuth_block", "gonzotech:rhenium_block",
                "gonzotech:steel_block", "gonzotech:invar_block",
                "gonzotech:corten_steel_block", "gonzotech:iodine_block",
                "gonzotech:manganese_block", "gonzotech:cobalt_block",
                "gonzotech:platinum_block", "");
        return NoteIllustration.structureRightDeck(60, 5, List.of(
                deckLayer5(plain, casing, null),
                deckLayer5(plain, null, pool),
                deckLayer5(nodes, null, pool),
                deckLayer5(plain, null, pool),
                deckLayer5(plain, casing, null)),
                NoteIllustration.CAPTION_VIEW_TOP, "gonzotech:steamgen_core", 2);
    }

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
