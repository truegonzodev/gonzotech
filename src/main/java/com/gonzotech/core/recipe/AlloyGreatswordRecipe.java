package com.gonzotech.core.recipe;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.item.AlloyEquipmentStats;
import com.gonzotech.core.item.GreatswordItem;
import com.gonzotech.core.registry.GreatswordRecipeSerializers;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Composition-preserving 3×3 recipe for the alloy greatsword. All three alloy
 * ingots must have the same canonical composition; three titanium blocks and
 * one rebar complete the large-blade pattern.
 */
public final class AlloyGreatswordRecipe extends CustomRecipe {

    /** I = custom-alloy ingot, B = titanium block, R = rebar. */
    private static final String[] PATTERN = { " IB", "BIB", "RI " };

    public AlloyGreatswordRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return compositionFor(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        AlloyComposition composition = compositionFor(input);
        return composition == null ? ItemStack.EMPTY : GreatswordItem.createAlloyStack(composition);
    }

    @Override
    public RecipeSerializer<AlloyGreatswordRecipe> getSerializer() {
        return GreatswordRecipeSerializers.ALLOY_GREATSWORD.get();
    }

    private static AlloyComposition compositionFor(CraftingInput input) {
        if (input.width() < 3 || input.height() < 3) return null;
        for (int rowOffset = 0; rowOffset <= input.height() - 3; rowOffset++) {
            for (int columnOffset = 0; columnOffset <= input.width() - 3; columnOffset++) {
                AlloyComposition composition = matchesAt(input, rowOffset, columnOffset);
                if (composition != null) return composition;
            }
        }
        return null;
    }

    private static AlloyComposition matchesAt(CraftingInput input, int rowOffset, int columnOffset) {
        AlloyComposition composition = null;
        var titaniumBlock = ModItems.METAL_BLOCK_ITEMS.get("titanium_block");
        if (titaniumBlock == null) return null;

        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                char expected = expectedAt(row - rowOffset, column - columnOffset);
                ItemStack stack = input.getItem(row * input.width() + column);
                if (expected == 'I') {
                    if (!stack.is(ModItems.CUSTOM_ALLOY.get())) return null;
                    AlloyComposition candidate = stack.get(ModDataComponents.ALLOY_COMPOSITION.get());
                    if (candidate == null) return null;
                    if (composition == null) composition = candidate;
                    else if (!composition.equals(candidate)) return null;
                } else if (expected == 'B') {
                    if (!stack.is(titaniumBlock.get())) return null;
                } else if (expected == 'R') {
                    if (!stack.is(ModItems.REBAR.get())) return null;
                } else if (!stack.isEmpty()) {
                    return null;
                }
            }
        }
        return AlloyEquipmentStats.isSupported(composition) ? composition : null;
    }

    private static char expectedAt(int row, int column) {
        return row >= 0 && row < PATTERN.length && column >= 0 && column < 3
            ? PATTERN[row].charAt(column)
            : ' ';
    }
}
