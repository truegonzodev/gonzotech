package com.gonzotech.machines.block.entity;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
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

    /** Опыт за переплавку одного предмета по рецепту (как в ваниле), 0 если рецепта нет. */
    public static float experience(ServerLevel level, ItemStack input) {
        return find(level, input)
            .map(h -> h.value().experience())
            .orElse(0.0F);
    }

    /**
     * Завершить одну переплавку: убавить вход на 1, положить результат в выход.
     * Предполагается, что {@link #canOutput} уже вернул true.
     *
     * @return опыт (как в ваниле) за эту переплавку — вызывающий копит его и
     *         выдаёт игроку при заборе результата из выходного слота.
     */
    public static float finish(ServerLevel level, NonNullList<ItemStack> items, int inputSlot, int outputSlot) {
        ItemStack input = items.get(inputSlot);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = find(level, input);
        if (recipe.isEmpty()) return 0.0F;
        ItemStack result = recipe.get().value().assemble(new SingleRecipeInput(input), level.registryAccess());
        ItemStack output = items.get(outputSlot);
        if (output.isEmpty()) {
            items.set(outputSlot, result.copy());
        } else if (ItemStack.isSameItemSameComponents(output, result)) {
            output.grow(result.getCount());
        }
        input.shrink(1);
        return recipe.get().value().experience();
    }

    /**
     * Выдать игроку накопленный опыт переплавки так же, как ванильная печь при
     * заборе результата: целую часть — орбами, дробную — вероятностно. Возвращает
     * ОСТАВШИЙСЯ (невыданный) дробный опыт, который вызывающий должен сохранить.
     */
    public static float awardExperience(ServerLevel level, Player player, float stored) {
        if (stored <= 0.0F) return stored;
        int whole = Mth.floor(stored);
        float frac = stored - whole;
        if (frac != 0.0F && level.random.nextFloat() < frac) {
            whole++;
        }
        if (whole > 0) {
            ExperienceOrb.award(level, player.position(), whole);
        }
        return 0.0F;
    }
}
