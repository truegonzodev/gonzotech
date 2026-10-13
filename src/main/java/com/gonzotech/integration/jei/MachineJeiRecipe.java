package com.gonzotech.integration.jei;

import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** One display-only recipe for a machine category; the machine remains authoritative. */
public final class MachineJeiRecipe {
    private final ResourceLocation id;
    private final List<Slot> slots;
    private final List<Component> notes;

    public MachineJeiRecipe(ResourceLocation id, List<Slot> slots, List<Component> notes) {
        this.id = Objects.requireNonNull(id, "id");
        this.slots = List.copyOf(slots);
        this.notes = List.copyOf(notes);
    }

    public ResourceLocation id() {
        return id;
    }

    public List<Slot> slots() {
        return slots;
    }

    public List<Component> notes() {
        return notes;
    }

    public static Slot items(RecipeIngredientRole role, int x, int y, List<ItemStack> stacks) {
        return new Slot(role, x, y, stacks, List.of(), null, 1_000, List.of(), 1_000);
    }

    public static Slot item(RecipeIngredientRole role, int x, int y, ItemStack stack) {
        return items(role, x, y, List.of(stack));
    }

    public static Slot item(RecipeIngredientRole role, int x, int y, ItemLike item) {
        return item(role, x, y, new ItemStack(item));
    }

    /** A deliberately blank machine slot, useful for showing an unused reusable tool position. */
    public static Slot empty(RecipeIngredientRole role, int x, int y) {
        return new Slot(role, x, y, List.of(), List.of(), null, 1_000, List.of(), 1_000);
    }

    public static Slot fluids(RecipeIngredientRole role, int x, int y, List<FluidAmount> fluids, long rendererCapacity) {
        return new Slot(role, x, y, List.of(), fluids, null, 1_000, List.of(), rendererCapacity);
    }

    public static Slot fluid(RecipeIngredientRole role, int x, int y, Fluid fluid, long amount) {
        return fluids(role, x, y, List.of(new FluidAmount(fluid, amount)), Math.max(1_000L, amount));
    }

    public static Slot display(RecipeIngredientRole role, int x, int y, SlotDisplay display) {
        return new Slot(role, x, y, List.of(), List.of(), display, 1_000, List.of(), 1_000);
    }

    public record FluidAmount(Fluid fluid, long amount) {
        public FluidAmount {
            Objects.requireNonNull(fluid, "fluid");
            if (amount <= 0) throw new IllegalArgumentException("Fluid amount must be positive");
        }
    }

    public record Slot(
        RecipeIngredientRole role,
        int x,
        int y,
        List<ItemStack> itemStacks,
        List<FluidAmount> fluids,
        @Nullable SlotDisplay display,
        int chancePermille,
        List<Component> tooltips,
        long rendererCapacity
    ) {
        public Slot {
            Objects.requireNonNull(role, "role");
            itemStacks = itemStacks.stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
            fluids = List.copyOf(fluids);
            tooltips = List.copyOf(tooltips);
            int contentKinds = (!itemStacks.isEmpty() ? 1 : 0) + (!fluids.isEmpty() ? 1 : 0) + (display != null ? 1 : 0);
            if (contentKinds > 1) throw new IllegalArgumentException("A JEI slot can contain only one ingredient kind");
            if (chancePermille < 0 || chancePermille > 1_000) throw new IllegalArgumentException("Chance must be in permille");
            if (!fluids.isEmpty() && rendererCapacity <= 0) throw new IllegalArgumentException("Fluid slots need a positive capacity");
        }

        @Override
        public List<ItemStack> itemStacks() {
            return itemStacks.stream().map(ItemStack::copy).toList();
        }

        public Slot withChance(int permille) {
            return new Slot(role, x, y, itemStacks, fluids, display, permille, tooltips, rendererCapacity);
        }

        public Slot withTooltip(Component tooltip) {
            java.util.ArrayList<Component> next = new java.util.ArrayList<>(tooltips);
            next.add(Objects.requireNonNull(tooltip, "tooltip"));
            return new Slot(role, x, y, itemStacks, fluids, display, chancePermille, next, rendererCapacity);
        }
    }
}
