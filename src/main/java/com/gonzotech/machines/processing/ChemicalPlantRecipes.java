package com.gonzotech.machines.processing;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.item.AmpouleItem;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Рецепты Химического завода (тир 3):
 * 11 утверждённых автором химических синтезов в сетке 3×3.
 *
 * <p>Катализаторы (платиновые и палладиевые самородки). Правила автора 24.09.2026:</p>
 * <ul>
 *   <li>обычные рецепты: для старта заняты ВСЕ 3 слота катализаторов; по завершении
 *       случайно тратится от 0 до 3 самородков;</li>
 *   <li>опилочные рецепты (смолистые/твердо­смольные опилки): исключение — для старта
 *       нужен всего 1 самородок; по завершении в 90 % случаев не тратится ничего,
 *       в 10 % — тратится 1 самородок.</li>
 * </ul>
 */
public final class ChemicalPlantRecipes {

    public interface IngredientMatcher {
        boolean matches(ItemStack stack);
        int count();
    }

    public static class ItemMatcher implements IngredientMatcher {
        private final Supplier<? extends Item> itemSupplier;
        private final int count;

        public ItemMatcher(Supplier<? extends Item> itemSupplier, int count) {
            this.itemSupplier = itemSupplier;
            this.count = count;
        }

        public ItemMatcher(Item item, int count) {
            this(() -> item, count);
        }

        @Override
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && stack.is(itemSupplier.get());
        }

        @Override
        public int count() {
            return count;
        }
    }

    public record AmpouleMatcher(String fluid, int count) implements IngredientMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            // Любая наполненная ампула (обычная или стойкая) — 24.09.2026.
            if (stack.isEmpty() || !AmpouleItem.isFilledAmpoule(stack)) return false;
            String stored = AmpouleItem.getStoredFluid(stack);
            if ("rectificate".equals(fluid) || "ethanol".equals(fluid)) {
                return "rectificate".equals(stored) || "ethanol".equals(stored);
            }
            return fluid.equals(stored);
        }
    }

    public record ChemicalRecipe(
        String id,
        List<IngredientMatcher> ingredients,
        Supplier<ItemStack> outputSupplier,
        int catalystRequired,
        boolean softCatalysts
    ) {
        public ChemicalRecipe(String id, List<IngredientMatcher> ingredients, Supplier<ItemStack> outputSupplier) {
            this(id, ingredients, outputSupplier, 3, false);
        }

        public ItemStack output() {
            return outputSupplier.get().copy();
        }
    }

    public static final List<ChemicalRecipe> RECIPES = new ArrayList<>();

    public static void init() {
        if (!RECIPES.isEmpty()) return;

        // 1. ЭДТА (гранулы): ампула формальдегида + 2 лазурита + 1 жемчуг эндера + 1 сахар = 3 ЭДТА
        RECIPES.add(new ChemicalRecipe("edta", List.of(
            new AmpouleMatcher("formaldehyde", 1),
            new ItemMatcher(Items.LAPIS_LAZULI, 2),
            new ItemMatcher(Items.ENDER_PEARL, 1),
            new ItemMatcher(Items.SUGAR, 1)
        ), () -> new ItemStack(ModItems.EDTA.get(), 3)));

        // 2. Цианат натрия: 2 соли + 1 редстоун + 1 зелёный краситель + ампула серной кислоты = 2 цианата натрия
        RECIPES.add(new ChemicalRecipe("sodium_cyanate", List.of(
            new ItemMatcher(ModItems.SALT::get, 2),
            new ItemMatcher(Items.REDSTONE, 1),
            new ItemMatcher(Items.GREEN_DYE, 1),
            new AmpouleMatcher("sulfuric_acid", 1)
        ), () -> new ItemStack(ModItems.SODIUM_CYANATE.get(), 2)));

        // 3. Полиэтилен: 2 ампулы этилена = 1 полиэтилен (гранулы)
        RECIPES.add(new ChemicalRecipe("polyethylene", List.of(
            new AmpouleMatcher("ethylene", 2)
        ), () -> new ItemStack(ModItems.POLYETHYLENE.get(), 1)));

        // 4. ПВХ: 2 хлорида кальция + 2 соли + 1 ампула серной кислоты = 2 ПВХ гранулы
        RECIPES.add(new ChemicalRecipe("polyvinyl_chloride", List.of(
            new ItemMatcher(ModItems.CALCIUM_CHLORIDE::get, 2),
            new ItemMatcher(ModItems.SALT::get, 2),
            new AmpouleMatcher("sulfuric_acid", 1)
        ), () -> new ItemStack(ModItems.POLYVINYL_CHLORIDE.get(), 2)));

        // 5. Сгусток смолы: 2 смолистых опилок + ампула ректификата = resin_clump
        //    (опилочный рецепт: старт от 1 самородка, расход 90 % — 0 / 10 % — 1)
        RECIPES.add(new ChemicalRecipe("resin_clump", List.of(
            new ItemMatcher(ModItems.RESINOUS_SAWDUST::get, 2),
            new AmpouleMatcher("rectificate", 1)
        ), () -> new ItemStack(Items.RESIN_CLUMP, 1), 1, true));

        // 6. Твердосмольные опилки: 2 твердосмольных опилок + ампула ректификата = resin
        //    (опилочный рецепт: старт от 1 самородка, расход 90 % — 0 / 10 % — 1)
        RECIPES.add(new ChemicalRecipe("resin", List.of(
            new ItemMatcher(ModItems.HARD_RESINOUS_SAWDUST::get, 2),
            new AmpouleMatcher("rectificate", 1)
        ), () -> new ItemStack(ModItems.RESIN.get(), 1), 1, true));

        // 7. Целлулоид: 1 бумажная ткань + 1 ампула серной кислоты + 1 ампула ректификата + 1 соль = 2 целлулоида
        RECIPES.add(new ChemicalRecipe("celluloid", List.of(
            new ItemMatcher(ModItems.PAPER_FABRIC::get, 1),
            new AmpouleMatcher("sulfuric_acid", 1),
            new AmpouleMatcher("rectificate", 1),
            new ItemMatcher(ModItems.SALT::get, 1)
        ), () -> new ItemStack(ModItems.CELLULOID.get(), 2)));

        // 8. Резина: 2 смолы + 1 сера = 2 резины
        RECIPES.add(new ChemicalRecipe("rubber", List.of(
            new ItemMatcher(ModItems.RESIN::get, 2),
            new ItemMatcher(() -> ModItems.RAW_ORE_ITEMS.get("sulfur").get(), 1)
        ), () -> new ItemStack(ModItems.RUBBER.get(), 2)));

        // 9. Цистамин: 2 ампулы аминоблейзатанола + 1 пустая ампула + 2 серы = 1 цистамин
        RECIPES.add(new ChemicalRecipe("cysteamine", List.of(
            new AmpouleMatcher("aminoblazeethanol", 2),
            new ItemMatcher(ModItems.EMPTY_AMPOULE::get, 1),
            new ItemMatcher(() -> ModItems.RAW_ORE_ITEMS.get("sulfur").get(), 2)
        ), () -> new ItemStack(ModItems.CYSTEAMINE.get(), 1)));

        // 10. Пентацин: 1 ЭДТА + 1 цианат натрия + 1 ферромагнитная пыль + 1 водка + 1 золотой самородок = 1 пентацин
        RECIPES.add(new ChemicalRecipe("pentacin", List.of(
            new ItemMatcher(ModItems.EDTA::get, 1),
            new ItemMatcher(ModItems.SODIUM_CYANATE::get, 1),
            new ItemMatcher(() -> ModItems.DUST_ITEMS.get("ferromagnetic_dust").get(), 1),
            new ItemMatcher(ModItems.VODKA_BOTTLE::get, 1),
            new ItemMatcher(Items.GOLD_NUGGET, 1)
        ), () -> new ItemStack(ModItems.PENTACIN.get(), 1)));

        // 11. ДТПА: 2 ЭДТА + 1 хлорид кальция + 2 сырого йода + 1 водка + 1 морской огурец = 1 ДТПА
        RECIPES.add(new ChemicalRecipe("dtpa", List.of(
            new ItemMatcher(ModItems.EDTA::get, 2),
            new ItemMatcher(ModItems.CALCIUM_CHLORIDE::get, 1),
            new ItemMatcher(() -> ModItems.RAW_ORE_ITEMS.get("iodine").get(), 2),
            new ItemMatcher(ModItems.VODKA_BOTTLE::get, 1),
            new ItemMatcher(Items.SEA_PICKLE, 1)
        ), () -> new ItemStack(ModItems.DTPA.get(), 1)));
    }

    /**
     * Проверяет, является ли предмет допустимым катализатором (платиновый или палладиевый самородок).
     */
    public static boolean isCatalyst(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var plat = ModItems.NUGGET_ITEMS.get("platinum_nugget");
        var pal = ModItems.NUGGET_ITEMS.get("palladium_nugget");
        if (plat != null && stack.is(plat.get())) return true;
        if (pal != null && stack.is(pal.get())) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(GonzoTechMod.MOD_ID)
                && (id.getPath().equals("platinum_nugget") || id.getPath().equals("palladium_nugget"));
    }

    /**
     * Поиск подходящего рецепта по ингредиентам в рабочей сетке 3×3.
     */
    public static ChemicalRecipe findRecipe(Container container, int gridStart, int gridCount) {
        init();
        for (ChemicalRecipe r : RECIPES) {
            // 1. Проверяем наличие всех ингредиентов рецепта в сетке
            boolean allPresent = true;
            for (IngredientMatcher m : r.ingredients()) {
                int total = 0;
                for (int s = gridStart; s < gridStart + gridCount; s++) {
                    ItemStack st = container.getItem(s);
                    if (m.matches(st)) {
                        total += st.getCount();
                    }
                }
                if (total < m.count()) {
                    allPresent = false;
                    break;
                }
            }
            if (!allPresent) continue;

            // 2. Проверяем, что в сетке нет лишних посторонних предметов
            boolean hasForeign = false;
            for (int s = gridStart; s < gridStart + gridCount; s++) {
                ItemStack st = container.getItem(s);
                if (st.isEmpty()) continue;
                boolean partOfRecipe = false;
                for (IngredientMatcher m : r.ingredients()) {
                    if (m.matches(st)) {
                        partOfRecipe = true;
                        break;
                    }
                }
                if (!partOfRecipe) {
                    hasForeign = true;
                    break;
                }
            }
            if (hasForeign) continue;

            return r;
        }
        return null;
    }

    /**
     * Поглощение ингредиентов рецепта из сетки 3×3.
     */
    public static void consumeInputs(Container container, int gridStart, int gridCount, ChemicalRecipe recipe) {
        for (IngredientMatcher m : recipe.ingredients()) {
            int need = m.count();
            for (int s = gridStart; s < gridStart + gridCount; s++) {
                ItemStack st = container.getItem(s);
                if (!st.isEmpty() && m.matches(st)) {
                    int take = Math.min(need, st.getCount());
                    st.shrink(take);
                    need -= take;
                    if (need <= 0) break;
                }
            }
        }
    }

    /**
     * Расход катализаторов по завершении синтеза (правила автора 24.09.2026):
     * обычный рецепт — случайно 0–3 самородка; опилочный (softCatalysts) —
     * 90 % ничего, 10 % один самородок.
     */
    public static void consumeCatalystsRandomly(Container container, int catStart, int catCount, RandomSource random, boolean softCatalysts) {
        int toConsume = softCatalysts
                ? (random.nextDouble() < 0.10 ? 1 : 0)   // опилочные: 90 % 0, 10 % 1
                : random.nextInt(4);                      // обычные: 0, 1, 2 или 3 самородка
        for (int i = 0; i < toConsume; i++) {
            List<Integer> availableSlots = new ArrayList<>();
            for (int c = catStart; c < catStart + catCount; c++) {
                ItemStack st = container.getItem(c);
                if (!st.isEmpty() && isCatalyst(st)) {
                    availableSlots.add(c);
                }
            }
            if (availableSlots.isEmpty()) break;
            int pickedSlot = availableSlots.get(random.nextInt(availableSlots.size()));
            container.getItem(pickedSlot).shrink(1);
        }
    }

    private ChemicalPlantRecipes() {
    }
}
