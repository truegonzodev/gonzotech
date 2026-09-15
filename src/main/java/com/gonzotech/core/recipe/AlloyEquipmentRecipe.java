package com.gonzotech.core.recipe;

import com.gonzotech.core.component.AlloyComposition;
import com.gonzotech.core.item.AlloyEquipmentStats;
import com.gonzotech.core.registry.ModDataComponents;
import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.registry.ModRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Dynamic shaped crafting for equipment made from one exact custom-alloy
 * composition. Unlike an event interception, this is a normal crafting recipe
 * that only recognises its three explicit custom-alloy-and-stick shapes.
 */
public abstract class AlloyEquipmentRecipe extends CustomRecipe {

    private final AlloyEquipmentStats.Kind kind;

    protected AlloyEquipmentRecipe(CraftingBookCategory category, AlloyEquipmentStats.Kind kind) {
        super(category);
        this.kind = kind;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return compositionFor(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        AlloyComposition composition = compositionFor(input);
        return composition == null ? ItemStack.EMPTY : AlloyEquipmentStats.create(kind, composition);
    }

    private AlloyComposition compositionFor(CraftingInput input) {
        String[] pattern = kind.pattern();
        int patternHeight = pattern.length;
        int patternWidth = pattern[0].length();
        if (input.width() < patternWidth || input.height() < patternHeight) return null;

        for (int rowOffset = 0; rowOffset <= input.height() - patternHeight; rowOffset++) {
            for (int columnOffset = 0; columnOffset <= input.width() - patternWidth; columnOffset++) {
                AlloyComposition composition = matchesAt(input, pattern, rowOffset, columnOffset);
                if (composition != null) return composition;
            }
        }
        return null;
    }

    /** Match one standard shaped-recipe position and reject all extra items. */
    private static AlloyComposition matchesAt(CraftingInput input, String[] pattern, int rowOffset, int columnOffset) {
        AlloyComposition composition = null;
        for (int row = 0; row < input.height(); row++) {
            for (int column = 0; column < input.width(); column++) {
                char expected = expectedAt(pattern, row - rowOffset, column - columnOffset);
                ItemStack stack = input.getItem(row * input.width() + column);
                if (expected == 'A') {
                    if (!stack.is(ModItems.CUSTOM_ALLOY.get())) return null;
                    AlloyComposition candidate = stack.get(ModDataComponents.ALLOY_COMPOSITION.get());
                    if (candidate == null) return null;
                    if (composition == null) composition = candidate;
                    else if (!composition.equals(candidate)) return null;
                } else if (expected == 'S') {
                    if (!stack.is(Items.STICK)) return null;
                } else if (!stack.isEmpty()) {
                    return null;
                }
            }
        }
        return composition != null && AlloyEquipmentStats.isSupported(composition) ? composition : null;
    }

    private static char expectedAt(String[] pattern, int row, int column) {
        return row >= 0 && row < pattern.length && column >= 0 && column < pattern[0].length()
            ? pattern[row].charAt(column)
            : ' ';
    }

    /** Standard 3×3 pickaxe shape: 3 matching alloy ingots and 2 sticks. */
    public static final class Pickaxe extends AlloyEquipmentRecipe {
        public Pickaxe(CraftingBookCategory category) {
            super(category, AlloyEquipmentStats.Kind.PICKAXE);
        }

        @Override
        public RecipeSerializer<Pickaxe> getSerializer() {
            return ModRecipeSerializers.ALLOY_PICKAXE.get();
        }
    }

    /** Standard vertical sword shape: 2 matching alloy ingots and 1 stick. */
    public static final class Sword extends AlloyEquipmentRecipe {
        public Sword(CraftingBookCategory category) {
            super(category, AlloyEquipmentStats.Kind.SWORD);
        }

        @Override
        public RecipeSerializer<Sword> getSerializer() {
            return ModRecipeSerializers.ALLOY_SWORD.get();
        }
    }

    /** Standard chestplate shape: 8 matching alloy ingots. */
    public static final class Chestplate extends AlloyEquipmentRecipe {
        public Chestplate(CraftingBookCategory category) {
            super(category, AlloyEquipmentStats.Kind.CHESTPLATE);
        }

        @Override
        public RecipeSerializer<Chestplate> getSerializer() {
            return ModRecipeSerializers.ALLOY_CHESTPLATE.get();
        }
    }
}
