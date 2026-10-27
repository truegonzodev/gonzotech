package com.gonzotech.core.recipe;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.item.AlloyEquipmentStats;
import com.gonzotech.core.item.HeatExchangerCellItem;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Eight identical custom-alloy ingots in a 3×3 ring form one composition-stamped cell. */
public final class HeatExchangerCellRecipe extends CustomRecipe {

    private static final String[] PATTERN = { "AAA", "A A", "AAA" };

    /** Display-only shape access; crafting still validates the real composition. */
    public static String[] pattern() { return PATTERN.clone(); }

    public HeatExchangerCellRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return compositionFor(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        AlloyComposition composition = compositionFor(input);
        return composition == null ? ItemStack.EMPTY : HeatExchangerCellItem.fromComposition(composition);
    }

    @Override
    public RecipeSerializer<HeatExchangerCellRecipe> getSerializer() {
        return ModRecipeSerializers.HEAT_EXCHANGER_CELL.get();
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

    /** Match the ring exactly: all eight positions share one alloy; the center and outside are empty. */
    private static AlloyComposition matchesAt(CraftingInput input, int rowOffset, int columnOffset) {
        AlloyComposition composition = null;
        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                char expected = expectedAt(row - rowOffset, column - columnOffset);
                ItemStack stack = input.getItem(row * input.width() + column);
                if (expected == 'A') {
                    if (!stack.is(ModItems.CUSTOM_ALLOY.get())) return null;
                    AlloyComposition candidate = stack.get(ModDataComponents.ALLOY_COMPOSITION.get());
                    if (candidate == null) return null;
                    if (composition == null) composition = candidate;
                    else if (!composition.equals(candidate)) return null;
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
