package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/**
 * Первые серверные рецепты Завода сплавов.
 *
 * <p>Станок намеренно пока не пытается выдавать {@code custom_alloy}: в этом
 * проходе включены только утверждённые именные рецепты чугуна и стали. Класс
 * отделён от BlockEntity, чтобы следующий проход мог заменить эти проверки
 * каталогом нормализованных составов/Data Components, не меняя инвентарь или
 * атомарность выдачи.</p>
 */
public final class AlloyFoundryRecipes {

    private AlloyFoundryRecipes() {
    }

    /**
     * Находит одну следующую атомарную плавку среди содержимого 5×5 сетки.
     * Все непустые ячейки должны состоять только из ингредиентов конкретного
     * рецепта: лишний предмет никогда не будет тихо проигнорирован или съеден.
     *
     * <p>Приоритет большой стальной плавки перед малой сохраняет заданный
     * рецепт {@code 4 dust + 2 ingot + 2 coal -> 6 steel} как единую операцию.
     * Обычная сталь затем берётся пакетами {@code 3 iron + 1 coal -> 3 steel}.
     * Чугун — временный беспитательный bootstrap-процесс: один железный слиток
     * превращается в один чугунный; ванильные furnace/blasting-пути не меняются.</p>
     */
    public static Batch find(Iterable<ItemStack> contents) {
        Counts counts = count(contents);
        if (counts.total == 0) return null;

        int iron = counts.of(Items.IRON_INGOT);
        int ironDust = counts.of(ModItems.IRON_DUST.get());
        int coal = counts.of(Items.COAL);

        // Steel recipes are checked first because their only extra material is
        // coal, which is an alloying input and does not affect output quantity.
        if (counts.only(Items.IRON_INGOT, ModItems.IRON_DUST.get(), Items.COAL)) {
            if (ironDust >= 4 && iron >= 2 && coal >= 2) {
                return new Batch(ModItems.INGOT_ITEMS.get("steel_ingot").get(), 6,
                    Map.of(ModItems.IRON_DUST.get(), 4, Items.IRON_INGOT, 2, Items.COAL, 2));
            }
            if (iron >= 3 && coal >= 1) {
                return new Batch(ModItems.INGOT_ITEMS.get("steel_ingot").get(), 3,
                    Map.of(Items.IRON_INGOT, 3, Items.COAL, 1));
            }
        }

        // The currently agreed cast-iron bootstrap conversion intentionally
        // accepts iron only and preserves it 1:1.
        if (counts.only(Items.IRON_INGOT) && iron >= 1) {
            return new Batch(ModItems.INGOT_ITEMS.get("cast_iron_ingot").get(), 1,
                Map.of(Items.IRON_INGOT, 1));
        }
        return null;
    }

    /** Один рецепт, уже проверенный на наличие всех входов. */
    public record Batch(Item output, int outputCount, Map<Item, Integer> ingredients) {
        public Batch {
            if (output == null || outputCount <= 0 || ingredients.isEmpty()) {
                throw new IllegalArgumentException("Invalid alloy foundry batch");
            }
        }
    }

    private static Counts count(Iterable<ItemStack> contents) {
        Map<Item, Integer> byItem = new HashMap<>();
        int total = 0;
        for (ItemStack stack : contents) {
            if (stack.isEmpty()) continue;
            byItem.merge(stack.getItem(), stack.getCount(), Integer::sum);
            total += stack.getCount();
        }
        return new Counts(byItem, total);
    }

    private record Counts(Map<Item, Integer> byItem, int total) {
        int of(Item item) {
            return byItem.getOrDefault(item, 0);
        }

        boolean only(Item... allowed) {
            if (byItem.isEmpty()) return false;
            outer: for (Item present : byItem.keySet()) {
                for (Item candidate : allowed) {
                    if (present == candidate) continue outer;
                }
                return false;
            }
            return true;
        }
    }
}
