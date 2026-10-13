package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Рецепты ПЦФСОЗ (0.3.89, автор 04.10.2026) — изотопное разделение топливного
 * цикла. Первый рецепт: жёлтый кек → уран-238 + уран-235 (10 %).
 *
 * <p>Механика бросков — копия {@link CentrifugeRecipes}: результаты
 * выбрасываются серверным RNG один раз на старте операции и хранятся в NBT
 * до публикации, поэтому перезапуск/выгрузка чанка не перебрасывает шанс.</p>
 */
public final class PCFSOZRecipes {

    /** 1000 = 100 %. */
    private static final int CHANCE_SCALE = 1_000;

    private static final List<Recipe> RECIPES = List.of(
        new Recipe(
            List.of(() -> ModItems.YELLOW_CAKE.get()),
            new Result(() -> ModItems.URANIUM_238.get(), 1),
            new Byproduct(() -> ModItems.URANIUM_235.get(), 100), // 10 %
            Byproduct.NONE, Byproduct.NONE));

    private PCFSOZRecipes() {
    }

    /** Read-only item recipe view for client recipe integrations. */
    public static List<ProcessingRecipeDisplay> displayRecipes() {
        List<ProcessingRecipeDisplay> displays = new java.util.ArrayList<>(RECIPES.size());
        for (Recipe recipe : RECIPES) {
            List<ItemStack> inputs = recipe.inputs().stream().map(input -> input.stack(1)).toList();
            List<ProcessingRecipeDisplay.Output> outputs = new java.util.ArrayList<>(4);
            outputs.add(new ProcessingRecipeDisplay.Output(recipe.main().stack(), CHANCE_SCALE));
            addDisplayOutput(outputs, recipe.first());
            addDisplayOutput(outputs, recipe.second());
            addDisplayOutput(outputs, recipe.third());
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(inputs.getFirst().getItem()).toString();
            displays.add(new ProcessingRecipeDisplay(id, List.of(inputs), outputs));
        }
        return List.copyOf(displays);
    }

    private static void addDisplayOutput(List<ProcessingRecipeDisplay.Output> outputs, Byproduct byproduct) {
        if (byproduct.item() != null) {
            outputs.add(new ProcessingRecipeDisplay.Output(byproduct.preview(), byproduct.chancePermille()));
        }
    }

    /** Единственный рецепт для стека или {@code null}, если сырьё не поддерживается. */
    public static Recipe find(ItemStack input) {
        if (input.isEmpty()) return null;
        for (Recipe recipe : RECIPES) {
            if (recipe.matches(input)) return recipe;
        }
        return null;
    }

    @FunctionalInterface
    public interface ItemRef extends Supplier<Item> {
        default ItemStack stack(int count) {
            return new ItemStack(Objects.requireNonNull(get(), "ПЦФСОЗ output/input item"), count);
        }
    }

    /** Гарантированный основной output. */
    public record Result(ItemRef item, int count) {
        public Result {
            if (count <= 0) throw new IllegalArgumentException("ПЦФСОЗ: count должен быть положительным");
        }

        public ItemStack stack() {
            return item.stack(count);
        }
    }

    /** Побочный результат своего слота; {@code item == null} — пустой слот. */
    public record Byproduct(ItemRef item, int chancePermille) {
        public static final Byproduct NONE = new Byproduct(null, 0);

        public Byproduct {
            if (chancePermille < 0 || chancePermille > CHANCE_SCALE) {
                throw new IllegalArgumentException("ПЦФСОЗ: шанс вне диапазона: " + chancePermille);
            }
            if (item == null && chancePermille != 0) {
                throw new IllegalArgumentException("ПЦФСОЗ: пустой output не может иметь шанс");
            }
        }

        public ItemStack preview() {
            return item == null ? ItemStack.EMPTY : item.stack(1);
        }

        public ItemStack roll(RandomSource random) {
            return item != null && random.nextInt(CHANCE_SCALE) < chancePermille ? item.stack(1) : ItemStack.EMPTY;
        }
    }

    /** Один рецепт: явные входы + основной выход + три независимых побочных. */
    public record Recipe(List<ItemRef> inputs, Result main,
                         Byproduct first, Byproduct second, Byproduct third) {

        public boolean matches(ItemStack input) {
            for (ItemRef explicit : inputs) {
                if (input.is(explicit.get())) return true;
            }
            return false;
        }

        /** Индексы массива соответствуют output-слотам 1..4. */
        public ItemStack[] rollOutputs(RandomSource random) {
            return new ItemStack[] {
                main.stack(), first.roll(random), second.roll(random), third.roll(random)
            };
        }

        /** Все возможные выходы без RNG — для атомарного reserve слотов. */
        public ItemStack[] outputCapacityPreview() {
            return new ItemStack[] {
                main.stack(), first.preview(), second.preview(), third.preview()
            };
        }
    }
}
