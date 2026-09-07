package com.gonzotech.chalkboard.notes;

import com.gonzotech.chalkboard.notes.ScholarPage.Layout;

import java.util.List;

/**
 * Статичное оглавление «Заметок учёного»: линейный массив страниц в порядке
 * буклета. Боковые вкладки — лишь навигация по главам-эпохам.
 *
 * <p>Первая эпоха ({@link ScholarChapter#ERA_1}) — 10 стартовых страниц дневника
 * Гонзо. Разблокировка страниц: {@link ScholarUnlock} (сразу / наиграно >5 мин /
 * после «Открытия 1»). Остальные 4 эпохи пока пусты (заглушки-вкладки).
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
                    Layout.TEXT_LEFT)
    );

    /** Доступна ли страница при данном состоянии игрока. */
    public static boolean isUnlocked(ScholarPage page, long playtimeTicks, boolean tier1Unlocked) {
        return page.unlock().isMet(playtimeTicks, tier1Unlocked);
    }

    /** Индекс первой доступной страницы (для стартового экрана). */
    public static int firstUnlockedIndex(long playtimeTicks, boolean tier1Unlocked) {
        for (int i = 0; i < PAGES.size(); i++) {
            if (isUnlocked(PAGES.get(i), playtimeTicks, tier1Unlocked)) {
                return i;
            }
        }
        return 0;
    }
}
