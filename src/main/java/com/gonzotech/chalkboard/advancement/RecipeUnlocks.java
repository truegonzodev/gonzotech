package com.gonzotech.chalkboard.advancement;

import com.gonzotech.chalkboard.progress.ModAttachments;
import com.gonzotech.chalkboard.progress.PlayerChalkboardProgress;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;
import java.util.Map;

/**
 * Показ рецептов машин в книге рецептов по «Открытиям» (Фаза 3).
 * <p>
 * Сами рецепты машин ФИЗИЧЕСКИ доступны всегда (файлы в data/.../recipe) —
 * котёл/топку/стирлинг/конденсатор можно скрафтить руками до любого «Открытия».
 * Но в КНИГЕ рецептов они скрыты, пока не активировано «Открытие 1»: тогда мы
 * выдаём (awardRecipesByKey) весь набор рецептов машин игроку.
 * <p>
 * Доска резонанса открыта априори собственным reward-advancement и здесь не
 * фигурирует. Эл. печь до «Открытия 1» ещё и физически «закрыта» гейтом крафта
 * ({@code Phase3Events}); её рецепт в книге открывается тем же «Открытием 1».
 */
public final class RecipeUnlocks {

    /** tier «Открытия» -> ключи рецептов (по namespace/path), открываемых в книге. */
    private static final Map<Integer, List<String>> RECIPES_BY_TIER = Map.of(
        1, List.of(
            "gonzotech:firebox",
            "gonzotech:boiler",
            "gonzotech:stirling_generator",
            "gonzotech:condenser",
            "gonzotech:electric_furnace"
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
        "gonzotech:scholar_notes"
    );

    private RecipeUnlocks() {
    }

    /** Выдать игроку рецепты, доступные априори (вызывать при каждом входе). */
    public static void grantAlwaysUnlocked(ServerPlayer player) {
        grant(player, RECIPES_ALWAYS);
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

    private static void grant(ServerPlayer player, List<String> ids) {
        List<ResourceKey<Recipe<?>>> keys = ids.stream()
            .map(id -> ResourceKey.<Recipe<?>>create(Registries.RECIPE, ResourceLocation.parse(id)))
            .toList();
        player.awardRecipesByKey(keys);
    }
}
