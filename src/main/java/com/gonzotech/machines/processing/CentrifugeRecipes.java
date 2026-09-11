package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Серверный набор результатов промывки для центрифуги ЦФ1УР.
 *
 * <p>Это не data-driven crafting recipe: результат зависит от независимых
 * серверных бросков для трёх закреплённых output-слотов. Каждый рецепт принимает
 * ровно один raw-предмет соответствующей руды либо любой зарегистрированный
 * block-item той же руды. Специальные руды (sulfur/manganese/iodine/mercury)
 * намеренно отсутствуют до проектирования их отдельных цепочек.</p>
 */
public final class CentrifugeRecipes {

    /** 1000 = 100%; таблица концепта использует точность до 0.1%, поэтому 13.0% = 130. */
    private static final int CHANCE_SCALE = 1_000;

    /**
     * 22 разрешённых входа, в нормативном порядке §6.4 MATERIAL_PROCESSING_CONCEPT.
     * Main идёт в основной output slot, a/b/c — в свои фиксированные byproduct slots.
     */
    private static final List<Recipe> RECIPES = List.of(
        r("calcium", dust("calcium_dust"), b(vanilla(Items.IRON_NUGGET), 130), b(dust("magnesium_dust"), 85), b(vanilla(Items.FLINT), 175)),
        r("aluminum", dust("aluminum_dust"), b(vanilla(Items.IRON_NUGGET), 265), b(vanilla(Items.FLINT), 175), b(dust("titanium_dust"), 67)),
        r("magnesium", dust("magnesium_dust"), b(nugget("calcium_nugget"), 175), b(vanilla(Items.IRON_NUGGET), 130), b(vanilla(Items.FLINT), 130)),
        r("titanium", dust("titanium_dust"), b(vanilla(Items.IRON_NUGGET), 175), b(dust("zirconium_dust"), 40), b(vanilla(Items.FLINT), 85)),
        r("barium", dust("barium_dust"), b(dust("lead_dust"), 103), b(nugget("calcium_nugget"), 67), b(vanilla(Items.FLINT), 130)),
        r("zinc", dust("zinc_dust"), b(dust("iron_dust"), 220), b(dust("lead_dust"), 130), b(dust("rhenium_dust"), 40)),
        r("tin", dust("tin_dust"), b(vanilla(Items.IRON_NUGGET), 175), b(dust("tungsten_dust"), 67), b(raw("manganese"), 85)),
        r("boron", dust("boron_dust"), b(nugget("calcium_nugget"), 175), b(dust("lithium_dust"), 67), b(vanilla(Items.FLINT), 175)),
        r("chromium", dust("chromium_dust"), b(dust("iron_dust"), 220), b(nugget("magnesium_nugget"), 130), b(dust("nickel_dust"), 40)),
        r("nickel", dust("nickel_dust"), b(dust("iron_dust"), 265), b(dust("cobalt_dust"), 175), b(vanilla(Items.FLINT), 85)),
        r("cobalt", dust("cobalt_dust"), b(dust("nickel_dust"), 220), b(vanilla(Items.IRON_NUGGET), 130), b(vanilla(Items.FLINT), 85)),
        r("silver", dust("silver_dust"), b(dust("lead_dust"), 85), b(dust("copper_dust"), 67), b(vanilla(Items.GOLD_NUGGET), 22)),
        r("tungsten", dust("tungsten_dust"), b(dust("iron_dust"), 220), b(raw("manganese"), 175), b(dust("tin_dust"), 40)),
        r("uranium", dust("uranium_dust"), b(dust("lead_dust"), 175), b(dust("bismuth_dust"), 130), b(dust("radium_dust"), 85)),
        r("zirconium", dust("zirconium_dust"), b(vanilla(Items.FLINT), 265), b(dust("neodymium_dust"), 130), b(vanilla(Items.IRON_NUGGET), 67)),
        r("thorium", dust("thorium_dust"), b(dust("lead_dust"), 130), b(dust("bismuth_dust"), 85), b(dust("radium_dust"), 85)),
        r("platinum", dust("platinum_dust"), b(dust("palladium_dust"), 220), b(dust("nickel_dust"), 175), b(vanilla(Items.IRON_NUGGET), 40)),
        r("tellurium", dust("tellurium_dust"), b(dust("silver_dust"), 220), b(vanilla(Items.GOLD_NUGGET), 67), b(vanilla(Items.FLINT), 85)),
        r("palladium", dust("palladium_dust"), b(dust("platinum_dust"), 220), b(dust("nickel_dust"), 175), b(dust("copper_dust"), 67)),
        r("cesium", dust("cesium_dust"), b(dust("aluminum_dust"), 175), b(vanilla(Items.FLINT), 265), b(dust("lithium_dust"), 40)),
        r("iridium", dust("iridium_dust"), b(dust("platinum_dust"), 175), b(dust("osmium_dust"), 130), b(dust("nickel_dust"), 40)),
        r("osmium", dust("osmium_dust"), b(dust("iridium_dust"), 175), b(dust("platinum_dust"), 175), b(dust("nickel_dust"), 40))
    );

    private CentrifugeRecipes() {
    }

    /** Возвращает единственный рецепт для стека или {@code null}, если сырьё не поддерживается. */
    public static Recipe find(ItemStack input) {
        if (input.isEmpty()) return null;
        for (Recipe recipe : RECIPES) {
            if (recipe.matches(input)) return recipe;
        }
        return null;
    }

    private static Recipe r(String oreId, ItemRef main, Byproduct a, Byproduct b, Byproduct c) {
        return new Recipe(oreId, main, a, b, c);
    }

    private static Byproduct b(ItemRef item, int chancePermille) {
        return new Byproduct(item, chancePermille);
    }

    private static ItemRef dust(String id) {
        return () -> requireItem(ModItems.DUST_ITEMS, id, "dust").get();
    }

    private static ItemRef nugget(String id) {
        return () -> requireItem(ModItems.NUGGET_ITEMS, id, "nugget").get();
    }

    private static ItemRef raw(String oreId) {
        return () -> requireItem(ModItems.RAW_ORE_ITEMS, oreId, "raw ore").get();
    }

    private static ItemRef vanilla(Item item) {
        return () -> item;
    }

    private static <T extends Item> Supplier<T> requireItem(Map<String, ? extends Supplier<T>> items, String id, String kind) {
        Supplier<T> item = items.get(id);
        if (item == null) {
            throw new IllegalStateException("ЦФ1УР: отсутствует зарегистрированный " + kind + " " + id);
        }
        return item;
    }

    @FunctionalInterface
    private interface ItemRef extends Supplier<Item> {
        /** Новый одноштучный стек, чтобы никакие компоненты/счётчики не протекали между операциями. */
        default ItemStack one() {
            return new ItemStack(Objects.requireNonNull(get(), "ЦФ1УР output item"));
        }
    }

    /** Один рецепт с тремя независимыми дополнительными бросками. */
    public record Recipe(String oreId, ItemRef main, Byproduct first, Byproduct second, Byproduct third) {
        /** Поддерживаются raw_<oreId> и все host-варианты ore block-item. */
        public boolean matches(ItemStack input) {
            Supplier<? extends Item> raw = ModItems.RAW_ORE_ITEMS.get(oreId);
            if (raw != null && input.is(raw.get())) return true;
            Map<?, ? extends Supplier<? extends Item>> blocks = ModItems.ORE_BLOCK_ITEMS.get(oreId);
            if (blocks == null) return false;
            for (Supplier<? extends Item> blockItem : blocks.values()) {
                if (input.is(blockItem.get())) return true;
            }
            return false;
        }

        /**
         * Бросает все три процента отдельно. Индекс возвращённого массива жёстко
         * соответствует output-слоту: 0 = гарантированная пыль, 1..3 = побочки.
         */
        public ItemStack[] rollOutputs(RandomSource random) {
            return new ItemStack[] {
                main.one(),
                first.roll(random),
                second.roll(random),
                third.roll(random)
            };
        }

        /**
         * One stack for every possible dedicated result slot, without consuming RNG.
         * This lets the machine reserve output capacity before an operation makes its
         * three chance rolls, so a full inventory cannot eat a future rare roll.
         */
        public ItemStack[] outputCapacityPreview() {
            return new ItemStack[] {
                main.one(),
                first.item().one(),
                second.item().one(),
                third.item().one()
            };
        }
    }

    /** Побочный результат в своём закреплённом слоте. */
    public record Byproduct(ItemRef item, int chancePermille) {
        public Byproduct {
            if (chancePermille < 0 || chancePermille > CHANCE_SCALE) {
                throw new IllegalArgumentException("ЦФ1УР: шанс вне диапазона: " + chancePermille);
            }
        }

        public ItemStack roll(RandomSource random) {
            return random.nextInt(CHANCE_SCALE) < chancePermille ? item.one() : ItemStack.EMPTY;
        }
    }
}
