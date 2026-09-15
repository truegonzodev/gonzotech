package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Strict die/punch recipes for the tier-two Press.
 *
 * <p>The form and punch are mode selectors, not ingredients. A recipe is valid
 * only for one of the four combinations specified here; notably, the wedge
 * punch requires an empty form slot. No fallback conversion exists for another
 * tool combination.</p>
 */
public final class PressRecipes {

    private static final List<Recipe> PLATES = List.of(
        recipe(Items.COPPER_INGOT, ModItems.COPPER_PLATE.get(), 1),
        recipe(ingot("aluminum_ingot"), ModItems.ALUMINUM_PLATE.get(), 1),
        recipe(Items.IRON_INGOT, ModItems.IRON_PLATE.get(), 1),
        recipe(ingot("steel_ingot"), ModItems.STEEL_PLATE.get(), 1),
        recipe(ingot("nickel_ingot"), ModItems.NICKEL_PLATE.get(), 1),
        recipe(ingot("stainless_steel_ingot"), ModItems.STAINLESS_STEEL_PLATE.get(), 1),
        recipe(Items.GOLD_INGOT, ModItems.GOLD_PLATE.get(), 1),
        recipe(Items.REDSTONE, ModItems.REDSTONE_PLATE.get(), 1),
        recipe(ingot("titanium_ingot"), ModItems.TITANIUM_PLATE.get(), 1),
        recipe(ingot("semiconductor_ingot"), ModItems.SEMICONDUCTOR_PLATE.get(), 1)
    );

    private static final List<Recipe> WIRES = List.of(
        recipe(Items.COPPER_INGOT, ModItems.COPPER_WIRE.get(), 4),
        recipe(ingot("aluminum_ingot"), ModItems.ALUMINUM_WIRE.get(), 4),
        recipe(Items.GOLD_INGOT, ModItems.GOLD_WIRE.get(), 4),
        recipe(ingot("silver_ingot"), ModItems.SILVER_WIRE.get(), 4)
    );

    private static final List<Recipe> CORES = List.of(
        recipe(Items.REDSTONE, ModItems.REDSTONE_CORE.get(), 1),
        recipe(ingot("semiconductor_ingot"), ModItems.SEMICONDUCTOR_CORE.get(), 1)
    );

    private static final List<Recipe> RAW_INGOTS = List.of(
        recipe(raw("manganese"), ingot("manganese_ingot"), 1),
        recipe(raw("sulfur"), ingot("sulfur_ingot"), 1),
        recipe(raw("iodine"), ingot("iodine_ingot"), 1)
    );

    private PressRecipes() {
    }

    /**
     * Finds a recipe for exactly the current punch/form combination. The result
     * is a fresh stack, so neither the selectors nor source components can leak
     * into the output.
     */
    public static ItemStack find(ItemStack input, ItemStack form, ItemStack punch) {
        if (input.isEmpty()) return null;
        List<Recipe> table = tableFor(form, punch);
        if (table == null) return null;
        for (Recipe recipe : table) {
            if (input.is(recipe.input())) return recipe.output();
        }
        return null;
    }

    /** A stack may enter the input slot when it appears in any declared recipe. */
    public static boolean acceptsInput(ItemStack input) {
        return hasInput(PLATES, input) || hasInput(WIRES, input)
            || hasInput(CORES, input) || hasInput(RAW_INGOTS, input);
    }

    public static boolean isForm(ItemStack stack) {
        return stack.is(ModItems.INGOT_FORM.get()) || stack.is(ModItems.PLATE_FORM.get())
            || stack.is(ModItems.CORE_FORM.get());
    }

    public static boolean isPunch(ItemStack stack) {
        return stack.is(ModItems.FLAT_PUNCH.get()) || stack.is(ModItems.WEDGE_PUNCH.get());
    }

    private static List<Recipe> tableFor(ItemStack form, ItemStack punch) {
        if (punch.is(ModItems.FLAT_PUNCH.get())) {
            if (form.is(ModItems.PLATE_FORM.get())) return PLATES;
            if (form.is(ModItems.INGOT_FORM.get())) return RAW_INGOTS;
            if (form.is(ModItems.CORE_FORM.get())) return CORES;
            return null;
        }
        if (punch.is(ModItems.WEDGE_PUNCH.get()) && form.isEmpty()) return WIRES;
        return null;
    }

    private static boolean hasInput(List<Recipe> recipes, ItemStack input) {
        for (Recipe recipe : recipes) {
            if (input.is(recipe.input())) return true;
        }
        return false;
    }

    private static Recipe recipe(Item input, Item output, int count) {
        return new Recipe(input, new ItemStack(output, count));
    }

    private static Item ingot(String id) {
        var item = ModItems.INGOT_ITEMS.get(id);
        if (item == null) throw new IllegalStateException("Missing registered press ingot: " + id);
        return item.get();
    }

    private static Item raw(String id) {
        var item = ModItems.RAW_ORE_ITEMS.get(id);
        if (item == null) throw new IllegalStateException("Missing registered press raw material: " + id);
        return item.get();
    }

    private record Recipe(Item input, ItemStack output) {
        Recipe {
            output = output.copy();
        }

        public ItemStack output() {
            return output.copy();
        }
    }
}
