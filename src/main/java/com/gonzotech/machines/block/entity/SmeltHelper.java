package com.gonzotech.machines.block.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.Optional;

/**
 * Общая логика ванильной переплавки (SMELTING) для топки и электропечи.
 * <p>
 * Обе машины «плавят» как обычная печь: вход → результат из даталака рецептов.
 * Разница только в источнике энергии (уголь/GTH у топки, GTU у электропечи) —
 * а сам процесс подбора рецепта и укладки результата одинаков, поэтому вынесен сюда.
 */
public final class SmeltHelper {

    private SmeltHelper() {
    }

    /** Найти smelting-рецепт для стака во входном слоте (или пусто). */
    public static Optional<RecipeHolder<SmeltingRecipe>> find(ServerLevel level, ItemStack input) {
        if (input.isEmpty()) return Optional.empty();
        return level.recipeAccess().getRecipeFor(
            RecipeType.SMELTING, new SingleRecipeInput(input), level);
    }

    /** Сколько тиков плавить по рецепту (fallback — стандарт печи 200). */
    public static int cookTime(ServerLevel level, ItemStack input, int fallback) {
        return find(level, input)
            .map(h -> h.value().cookingTime())
            .orElse(fallback);
    }

    /**
     * Можно ли положить результат рецепта в выходной слот (пусто или тот же
     * предмет с запасом по стаку).
     */
    public static boolean canOutput(ServerLevel level, ItemStack input, ItemStack output) {
        Optional<RecipeHolder<SmeltingRecipe>> recipe = find(level, input);
        if (recipe.isEmpty()) return false;
        ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(input), level.registryAccess());
        if (result.isEmpty()) return false;
        if (output.isEmpty()) return true;
        if (!ItemStack.isSameItemSameComponents(output, result)) return false;
        return output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    /**
     * Завершить одну переплавку: убавить вход на 1, положить результат в выход.
     * Предполагается, что {@link #canOutput} уже вернул true.
     */
    public static void finish(ServerLevel level, NonNullList<ItemStack> items, int inputSlot, int outputSlot) {
        ItemStack input = items.get(inputSlot);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = find(level, input);
        if (recipe.isEmpty()) return;
        ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(input), level.registryAccess());
        ItemStack output = items.get(outputSlot);
        if (output.isEmpty()) {
            items.set(outputSlot, result.copy());
        } else if (ItemStack.isSameItemSameComponents(output, result)) {
            output.grow(result.getCount());
        }
        input.shrink(1);
    }
}
