package com.gonzotech.chalkboard.advancement;

import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;
import java.util.Map;

/**
 * Показ рецептов в книге рецептов по «Открытиям» (Фаза 3).
 * <p>
 * Эти crafting-рецепты ФИЗИЧЕСКИ доступны всегда (файлы в data/.../recipe),
 * но их подсказки в КНИГЕ скрыты до соответствующего «Открытия»: здесь они
 * выдаются игроку через {@code awardRecipesByKey}. Исключение — отдельные
 * рецепты с физическим crafting-гейтом из {@code Phase3Events}; у некоторых
 * гейт СОСТАВНОЙ по «И» (солнечные часы: «Открытие 2» + встреченный багровый
 * день — см. {@link #grantSolarWatchIfReady}).
 * <p>
 * Доска резонанса открыта априори собственным reward-advancement и здесь не
 * фигурирует. Эл. печь до «Открытия 1» ещё и физически «закрыта» гейтом крафта
 * ({@code Phase3Events}); её рецепт в книге открывается тем же «Открытием 1».
 */
public final class RecipeUnlocks {

    /** tier «Открытия» -> ключи рецептов (по namespace/path), открываемых в книге. */
    private static final Map<Integer, List<String>> RECIPES_BY_TIER = Map.of(
        1, List.of(
            // Машины
            "gonzotech:firebox",
            "gonzotech:boiler",
            "gonzotech:stirling_generator",
            "gonzotech:condenser",
            "gonzotech:first_electric_furnace",
            "gonzotech:first_pump",
            "gonzotech:first_accumulator",
            "gonzotech:first_cobble_generator",
            // Части паровой турбины
            "gonzotech:first_turbine_casing",
            "gonzotech:first_turbine_rotor",
            // Логистика: инструмент
            "gonzotech:wrench",
            // Логистика: трубы
            "gonzotech:first_wire",
            "gonzotech:first_heat_pipe",
            "gonzotech:first_water_pipe",
            "gonzotech:first_steam_pipe",
            "gonzotech:first_universal_fluid_pipe",
            "gonzotech:first_item_pipe",
            // Логистика: узлы
            "gonzotech:first_wire_node",
            "gonzotech:first_heat_node",
            "gonzotech:first_water_node",
            "gonzotech:first_steam_node",
            "gonzotech:first_item_node",
            "gonzotech:first_universal_fluid_node",
            "gonzotech:first_universal_node",
            // Логистика: сортировка предметов
            "gonzotech:first_item_filter",
            "gonzotech:first_item_scavenger",
            // Строительные материалы II. Рецепты физически доступны всегда,
            // но в книге появляются вместе с Открытием 1.
            "gonzotech:trio_grit",
            "gonzotech:clinker_grit_from_trio_grit_smelting",
            "gonzotech:armor_mix",
            "gonzotech:andesite_silicate_clinker",
            "gonzotech:white_porcelain_batch",
            "gonzotech:rebar",
            "gonzotech:armor_concrete",
            "gonzotech:durable_concrete",
            "gonzotech:porcelain",
            "gonzotech:industrial_concrete",
            "gonzotech:reinforced_armor_concrete",
            "gonzotech:reinforced_industrial_concrete",
            "gonzotech:slag_concrete",
            // Радиационный экран (автор 21.09): экранирующий замес → бариевый
            // бетон и боросиликатный замес → борное стекло.
            "gonzotech:barium_mix",
            "gonzotech:barium_concrete",
            "gonzotech:bore_silicate_mix",
            "gonzotech:bore_stained_glass_smelting",
            "gonzotech:bore_stained_glass_blasting"
        ),
        2, List.of(
            // Пылевые сплавы верстака. Они физически крафтятся по обычным
            // shapeless-рецептам, но появляются в книге только после Открытия 2.
            "gonzotech:steel_dust_from_iron_dust_and_coal",
            "gonzotech:stainless_steel_dust_from_metal_dusts",
            "gonzotech:nitinol_dust_from_metal_dusts",
            "gonzotech:invar_dust_from_metal_dusts",
            "gonzotech:ferromagnetic_dust_from_metal_dusts",
            "gonzotech:cantor_dust_from_metal_dusts",
            "gonzotech:vr20_dust_from_metal_dusts",
            "gonzotech:alnico_dust_from_metal_dusts",
            "gonzotech:telluride_dust_from_metal_dusts",
            // Расходник к шкале облучения + хазмат I (автор 22.09): крафт доступен
            // всегда, но в книге появляется после Открытия 2.
            "gonzotech:rad_absorbent",
            "gonzotech:hazmat_helmet",
            "gonzotech:hazmat_chestplate",
            "gonzotech:hazmat_leggings",
            "gonzotech:hazmat_boots",
            // Ампулы (автор 24.09): крафт доступен всегда, показ в книге — с Открытия 2.
            "gonzotech:empty_ampoule",
            "gonzotech:durable_ampoule"
        ),
        6, List.of(
            // Физически крафтится всегда, в книге появляется с Открытием 6.
            "gonzotech:superdense_ice"
        )
    );

    /**
     * Рецепты, доступные и видимые в книге АПРИОРИ (без «Открытий»). Выдаём их
     * безусловно при каждом входе — так их «подсказка» появляется в книге сразу,
     * не завязываясь на срабатывание recipe-advancement'ов. Идемпотентно.
     */
    private static final List<String> RECIPES_ALWAYS = List.of(
        "gonzotech:chalkboard",
        "gonzotech:pseudo_coil",
        "gonzotech:scholar_notes",
        // Намеренно крафтовый «сломанный механизм» всегда должен быть виден.
        "gonzotech:botched_mechanism",
        // Взаимная конверсия смолы и сгустка смолы (доступна всегда).
        "gonzotech:resin_from_resin_clump",
        "gonzotech:resin_clump_from_resin"
    );

    /** 20 minutes of accumulated Minecraft play time. */
    private static final int TWENTY_MINUTES_PLAY_TIME_TICKS = 20 * 60 * 20;

    /** Recipes deliberately revealed only after twenty minutes in this world. */
    private static final List<String> RECIPES_AFTER_TWENTY_MINUTES = List.of(
        "gonzotech:radioactive_slime_block",
        "gonzotech:charcoal_from_dead_log",
        "gonzotech:plastic_waste"
    );

    private RecipeUnlocks() {
    }

    /** Выдать игроку рецепты, доступные априори (вызывать при каждом входе). */
    public static void grantAlwaysUnlocked(ServerPlayer player) {
        grant(player, RECIPES_ALWAYS);
    }

    /**
     * Reveal the three time-gated recipe-book entries after this player's own
     * accumulated {@link Stats#PLAY_TIME play time}; this is not world time.
     * Safe to call on every server player tick and at login.
     */
    public static void grantAfterTwentyMinutesPlayed(ServerPlayer player) {
        int playTime = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        if (playTime >= TWENTY_MINUTES_PLAY_TIME_TICKS) {
            grant(player, RECIPES_AFTER_TWENTY_MINUTES);
        }
    }

    /** Выдать игроку рецепты для всех уже активированных «Открытий». Идемпотентно. */
    public static void grantForUnlockedTiers(ServerPlayer player) {
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        for (Map.Entry<Integer, List<String>> e : RECIPES_BY_TIER.entrySet()) {
            if (progress.isRecipeTierUnlocked(e.getKey())) {
                grant(player, e.getValue());
            }
        }
    }

    /** Выдать рецепты конкретного «Открытия» (при активации свитка). */
    public static void grantForTier(ServerPlayer player, int tier) {
        List<String> recipes = RECIPES_BY_TIER.get(tier);
        if (recipes != null) {
            grant(player, recipes);
        }
    }

    /**
     * Пере-выдача уже пройденного тира при входе в мир (автор 22.09.2026).
     *
     * <p>«Гейт открыт навсегда» означает не только «не закрывается задним числом»,
     * но и «догоняет будущее»: список {@link #RECIPES_BY_TIER} растёт от версии к
     * версии, а выдача идёт один раз в момент активации «Открытия». Игрок, активировавший
     * тир до обновления мода, новых рецептов иначе не увидел бы НИКОГДА. У тира 2 такая
     * страховка уже есть ({@code TierTwoCrafting} пере-выдаёт своё на каждом входе),
     * здесь — то же самое для тира 1 (вызов на {@code PlayerLoggedInEvent}).
     * {@code awardRecipesByKey} идемпотентен: повторная выдача ничего не ломает.</p>
     */
    public static void regrantOnLogin(ServerPlayer player) {
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (progress.isRecipeTierUnlocked(1)) {
            grantForTier(player, 1);
        }
        grantSolarWatchIfReady(player);
    }

    /**
     * Солнечные часы: показ рецепта открывается по ДВУМ условиям, соединённым «И»
     * (автор 22.09.2026) — «Открытие 2» активировано И игрок уже видел багровый день
     * (флаг {@link com.gonzotech.chalkboard.notes.ScholarNoteFlags#SUN_EVENT}).
     * Точно такой же гейт стоит на ФИЗИЧЕСКОМ крафте часов
     * ({@code Phase3Events.extraGateMet}); страница заметок — на составном
     * {@code ScholarUnlock.SUN_EVENT_AND_DISCOVERY_2}.
     *
     * <p>Зовётся на всех входах, через которые условия могут стать истинными: вход
     * в мир (условия уже были выполнены раньше), использование «Открытия»
     * ({@code DiscoveryItem}) и наступление багрового дня
     * ({@code SunEventServer}). {@code awardRecipesByKey} идемпотентен.</p>
     */
    public static void grantSolarWatchIfReady(ServerPlayer player) {
        PlayerChalkboardProgress progress = player.getData(ModAttachments.CHALKBOARD_PROGRESS);
        if (!progress.isRecipeTierUnlocked(2)) return;
        if (!progress.hasNoteFlag(com.gonzotech.chalkboard.notes.ScholarNoteFlags.SUN_EVENT)) return;
        grant(player, List.of("gonzotech:solar_watch"));
    }

    private static void grant(ServerPlayer player, List<String> ids) {
        List<ResourceKey<Recipe<?>>> keys = ids.stream()
            .map(id -> ResourceKey.<Recipe<?>>create(Registries.RECIPE, ResourceLocation.parse(id)))
            .toList();
        player.awardRecipesByKey(keys);
    }
}
