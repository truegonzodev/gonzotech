package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Рецепты измельчителя отработки (0.3.250): отработанный ТВЭЛ + пустой
 * корпус → «в меру опасные шлаки» + бочка отходов. Обычный измельчитель без
 * корпуса даёт «крайне опасные шлаки» (см. {@link GrinderRecipes}), разделение
 * шлаков идёт в центрифуге (см. {@link CentrifugeRecipes}).
 */
public final class ReprocessingRecipes {

    /** Пара выходов операции: шлаки в слот шлаков, бочка — в слот бочек. */
    public record Outputs(ItemStack slag, ItemStack barrel) {
    }

    private ReprocessingRecipes() {
    }

    /** Returns the two outputs for a TVEL+hull pair, or {@code null} when inputs do not match. */
    public static Outputs find(ItemStack tvel, ItemStack hull) {
        if (!acceptsTvel(tvel) || !acceptsHull(hull)) return null;
        return new Outputs(
            new ItemStack(ModItems.MODERATELY_DANGEROUS_SLAG.get()),
            new ItemStack(ModItems.WASTE_BARREL_ITEM.get()));
    }

    /** Отработанный ТВЭЛ — единственный топливный вход измельчителя отработки. */
    public static boolean acceptsTvel(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.DEPRESSED_UF_TVEL.get());
    }

    /** Пустой корпус — расходник отдельного слота. */
    public static boolean acceptsHull(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.EMPTY_HULL.get());
    }

    /** Read-only recipe view for client recipe integrations. */
    public static List<ProcessingRecipeDisplay> displayRecipes() {
        return List.of(new ProcessingRecipeDisplay("third_grinder",
            List.of(
                List.of(new ItemStack(ModItems.DEPRESSED_UF_TVEL.get())),
                List.of(new ItemStack(ModItems.EMPTY_HULL.get()))),
            List.of(
                new ProcessingRecipeDisplay.Output(
                    new ItemStack(ModItems.MODERATELY_DANGEROUS_SLAG.get()), 1_000),
                new ProcessingRecipeDisplay.Output(
                    new ItemStack(ModItems.WASTE_BARREL_ITEM.get()), 1_000))));
    }
}
