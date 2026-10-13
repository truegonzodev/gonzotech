package com.gonzotech.machines.processing;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable, JEI-agnostic snapshot of an item-processing recipe.
 *
 * <p>Each input slot contains its accepted stack variants; each output records
 * the chance in permille (1000 = guaranteed). Client recipe viewers can project
 * this data without owning or mutating the machine's authoritative recipe table.</p>
 */
public record ProcessingRecipeDisplay(String id, List<List<ItemStack>> inputSlots, List<Output> outputs) {

    public ProcessingRecipeDisplay {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) throw new IllegalArgumentException("Recipe display id must not be blank");
        inputSlots = copySlots(inputSlots);
        outputs = outputs.stream().map(Output::copy).toList();
    }

    @Override
    public List<List<ItemStack>> inputSlots() {
        return copySlots(inputSlots);
    }

    @Override
    public List<Output> outputs() {
        return outputs.stream().map(Output::copy).toList();
    }

    private static List<List<ItemStack>> copySlots(List<List<ItemStack>> slots) {
        List<List<ItemStack>> copy = new ArrayList<>(slots.size());
        for (List<ItemStack> slot : slots) {
            copy.add(slot.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList());
        }
        return List.copyOf(copy);
    }

    public record Output(ItemStack stack, int chancePermille) {
        public Output {
            Objects.requireNonNull(stack, "stack");
            if (stack.isEmpty()) throw new IllegalArgumentException("Output display stack must not be empty");
            if (chancePermille < 0 || chancePermille > 1_000) {
                throw new IllegalArgumentException("Output chance must be in permille");
            }
            stack = stack.copy();
        }

        private Output copy() {
            return new Output(stack, chancePermille);
        }

        @Override
        public ItemStack stack() {
            return stack.copy();
        }
    }
}
