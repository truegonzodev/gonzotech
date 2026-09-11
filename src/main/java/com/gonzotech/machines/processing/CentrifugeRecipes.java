package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Серверный набор результатов промывки для центрифуги ЦФ1УР.
 *
 * <p>Это не data-driven crafting recipe: результат зависит от независимых
 * серверных бросков для закреплённых output-слотов. Рецепты обычных руд
 * принимают raw и все варианты ore block-item; специальные и ванильные строки
 * используют только явно перечисленные стабильные предметы.</p>
 */
public final class CentrifugeRecipes {

    /** 1000 = 100%; таблицы используют точность до 0.1%, поэтому 13.0% = 130. */
    private static final int CHANCE_SCALE = 1_000;

    /** 22 базовые строки ЦФ1УР из §6.4 MATERIAL_PROCESSING_CONCEPT. */
    private static final List<Recipe> RECIPES = List.of(
        ore("calcium", main(dust("calcium_dust")), b(vanilla(Items.IRON_NUGGET), 130), b(dust("magnesium_dust"), 85), b(vanilla(Items.FLINT), 175)),
        ore("aluminum", main(dust("aluminum_dust")), b(vanilla(Items.IRON_NUGGET), 265), b(vanilla(Items.FLINT), 175), b(dust("titanium_dust"), 67)),
        ore("magnesium", main(dust("magnesium_dust")), b(nugget("calcium_nugget"), 175), b(vanilla(Items.IRON_NUGGET), 130), b(vanilla(Items.FLINT), 130)),
        ore("titanium", main(dust("titanium_dust")), b(vanilla(Items.IRON_NUGGET), 175), b(dust("zirconium_dust"), 40), b(vanilla(Items.FLINT), 85)),
        ore("barium", main(dust("barium_dust")), b(dust("lead_dust"), 103), b(nugget("calcium_nugget"), 67), b(vanilla(Items.FLINT), 130)),
        ore("zinc", main(dust("zinc_dust")), b(dust("iron_dust"), 220), b(dust("lead_dust"), 130), b(dust("rhenium_dust"), 40)),
        ore("tin", main(dust("tin_dust")), b(vanilla(Items.IRON_NUGGET), 175), b(dust("tungsten_dust"), 67), b(raw("manganese"), 85)),
        ore("boron", main(dust("boron_dust")), b(nugget("calcium_nugget"), 175), b(dust("lithium_dust"), 67), b(vanilla(Items.FLINT), 175)),
        ore("chromium", main(dust("chromium_dust")), b(dust("iron_dust"), 220), b(nugget("magnesium_nugget"), 130), b(dust("nickel_dust"), 40)),
        ore("nickel", main(dust("nickel_dust")), b(dust("iron_dust"), 265), b(dust("cobalt_dust"), 175), b(vanilla(Items.FLINT), 85)),
        ore("cobalt", main(dust("cobalt_dust")), b(dust("nickel_dust"), 220), b(vanilla(Items.IRON_NUGGET), 130), b(vanilla(Items.FLINT), 85)),
        ore("silver", main(dust("silver_dust")), b(dust("lead_dust"), 85), b(dust("copper_dust"), 67), b(vanilla(Items.GOLD_NUGGET), 22)),
        ore("tungsten", main(dust("tungsten_dust")), b(dust("iron_dust"), 220), b(raw("manganese"), 175), b(dust("tin_dust"), 40)),
        ore("uranium", main(dust("uranium_dust")), b(dust("lead_dust"), 175), b(dust("bismuth_dust"), 130), b(dust("radium_dust"), 85)),
        ore("zirconium", main(dust("zirconium_dust")), b(vanilla(Items.FLINT), 265), b(dust("neodymium_dust"), 130), b(vanilla(Items.IRON_NUGGET), 67)),
        ore("thorium", main(dust("thorium_dust")), b(dust("lead_dust"), 130), b(dust("bismuth_dust"), 85), b(dust("radium_dust"), 85)),
        ore("platinum", main(dust("platinum_dust")), b(dust("palladium_dust"), 220), b(dust("nickel_dust"), 175), b(vanilla(Items.IRON_NUGGET), 40)),
        ore("tellurium", main(dust("tellurium_dust")), b(dust("silver_dust"), 220), b(vanilla(Items.GOLD_NUGGET), 67), b(vanilla(Items.FLINT), 85)),
        ore("palladium", main(dust("palladium_dust")), b(dust("platinum_dust"), 220), b(dust("nickel_dust"), 175), b(dust("copper_dust"), 67)),
        ore("cesium", main(dust("cesium_dust")), b(dust("aluminum_dust"), 175), b(vanilla(Items.FLINT), 265), b(dust("lithium_dust"), 40)),
        ore("iridium", main(dust("iridium_dust")), b(dust("platinum_dust"), 175), b(dust("osmium_dust"), 130), b(dust("nickel_dust"), 40)),
        ore("osmium", main(dust("osmium_dust")), b(dust("iridium_dust"), 175), b(dust("platinum_dust"), 175), b(dust("nickel_dust"), 40)),

        // Четыре специальных минерала: ЦФ принимает именно рудный block item,
        // а не уже добытый raw. У всех — 1 raw и независимый шанс 5% свинца.
        oreBlocksOnly("sulfur", main(raw("sulfur")), b(nugget("lead_nugget"), 50), none(), none()),
        oreBlocksOnly("manganese", main(raw("manganese")), b(nugget("lead_nugget"), 50), none(), none()),
        oreBlocksOnly("iodine", main(raw("iodine")), b(nugget("lead_nugget"), 50), none(), none()),
        oreBlocksOnly("mercury", main(raw("mercury")), b(nugget("lead_nugget"), 50), none(), none()),

        // Ванильные руды и их явно названные raw/block-варианты.
        explicit(main(dust("copper_dust")), List.of(block(Blocks.COPPER_ORE), block(Blocks.DEEPSLATE_COPPER_ORE), vanilla(Items.RAW_COPPER)),
            b(dust("boron_dust"), 150), b(raw("sulfur"), 50), none()),
        explicit(main(dust("iron_dust")), List.of(block(Blocks.IRON_ORE), block(Blocks.DEEPSLATE_IRON_ORE), vanilla(Items.RAW_IRON)),
            b(dust("aluminum_dust"), 220), b(nugget("cast_iron_nugget"), 100), b(nugget("neodymium_nugget"), 50)),
        explicit(main(vanilla(Items.GOLD_NUGGET), 9), List.of(block(Blocks.GOLD_ORE), block(Blocks.DEEPSLATE_GOLD_ORE),
                block(Blocks.NETHER_GOLD_ORE), block(Blocks.GILDED_BLACKSTONE), vanilla(Items.RAW_GOLD)),
            b(nugget("platinum_nugget"), 50), b(nugget("copper_nugget"), 150), none()),
        explicit(main(vanilla(Items.DIAMOND)), List.of(block(Blocks.DIAMOND_ORE), block(Blocks.DEEPSLATE_DIAMOND_ORE)),
            b(vanilla(Items.QUARTZ), 50), b(modItem(ModItems.SILICON), 100), none()),
        explicit(main(vanilla(Items.COAL), 3), List.of(block(Blocks.COAL_ORE), block(Blocks.DEEPSLATE_COAL_ORE)),
            b(nugget("thorium_nugget"), 50), b(nugget("rhenium_nugget"), 30), none()),
        explicit(main(vanilla(Items.LAPIS_LAZULI), 3), List.of(block(Blocks.LAPIS_ORE), block(Blocks.DEEPSLATE_LAPIS_ORE)),
            b(nugget("cobalt_nugget"), 200), b(vanilla(Items.AMETHYST_SHARD), 50), none()),
        explicit(main(vanilla(Items.REDSTONE), 3), List.of(block(Blocks.REDSTONE_ORE), block(Blocks.DEEPSLATE_REDSTONE_ORE)),
            b(nugget("mercury_nugget"), 100), none(), none()),
        explicit(main(vanilla(Items.EMERALD), 2), List.of(block(Blocks.EMERALD_ORE), block(Blocks.DEEPSLATE_EMERALD_ORE)),
            b(vanilla(Items.SLIME_BALL), 300), b(dust("aluminum_dust"), 200), none()),
        explicit(main(vanilla(Items.QUARTZ), 3), List.of(block(Blocks.NETHER_QUARTZ_ORE)),
            b(vanilla(Items.AMETHYST_SHARD), 300), b(nugget("copper_nugget"), 50), none())
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

    private static Recipe ore(String oreId, Result main, Byproduct a, Byproduct b, Byproduct c) {
        return new Recipe(oreId, true, List.of(), main, a, b, c);
    }

    private static Recipe oreBlocksOnly(String oreId, Result main, Byproduct a, Byproduct b, Byproduct c) {
        return new Recipe(oreId, false, List.of(), main, a, b, c);
    }

    private static Recipe explicit(Result main, List<ItemRef> inputs, Byproduct a, Byproduct b, Byproduct c) {
        return new Recipe(null, false, inputs, main, a, b, c);
    }

    private static Result main(ItemRef item) {
        return main(item, 1);
    }

    private static Result main(ItemRef item, int count) {
        return new Result(item, count);
    }

    private static Byproduct b(ItemRef item, int chancePermille) {
        return new Byproduct(item, chancePermille);
    }

    private static Byproduct none() {
        return new Byproduct(null, 0);
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

    private static ItemRef modItem(Supplier<? extends Item> item) {
        return item::get;
    }

    private static ItemRef vanilla(Item item) {
        return () -> item;
    }

    private static ItemRef block(Block block) {
        return block::asItem;
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
        default ItemStack stack(int count) {
            return new ItemStack(Objects.requireNonNull(get(), "ЦФ1УР output/input item"), count);
        }
    }

    /** Гарантированный основной output, который может содержать больше одного предмета. */
    public record Result(ItemRef item, int count) {
        public Result {
            if (count <= 0) throw new IllegalArgumentException("ЦФ1УР: count должен быть положительным");
        }

        public ItemStack stack() {
            return item.stack(count);
        }
    }

    /** Один рецепт с тремя независимыми дополнительными бросками. */
    public record Recipe(String oreId, boolean acceptsRaw, List<ItemRef> explicitInputs,
                         Result main, Byproduct first, Byproduct second, Byproduct third) {
        /** Поддерживаются raw + все host ore-blocks либо список явно заданных предметов. */
        public boolean matches(ItemStack input) {
            for (ItemRef explicit : explicitInputs) {
                if (input.is(explicit.get())) return true;
            }
            if (oreId == null) return false;

            if (acceptsRaw) {
                Supplier<? extends Item> raw = ModItems.RAW_ORE_ITEMS.get(oreId);
                if (raw != null && input.is(raw.get())) return true;
            }
            Map<?, ? extends Supplier<? extends Item>> blocks = ModItems.ORE_BLOCK_ITEMS.get(oreId);
            if (blocks == null) return false;
            for (Supplier<? extends Item> blockItem : blocks.values()) {
                if (input.is(blockItem.get())) return true;
            }
            return false;
        }

        /** Индексы массива соответствуют output-slots 1..4. */
        public ItemStack[] rollOutputs(RandomSource random) {
            return new ItemStack[] {
                main.stack(), first.roll(random), second.roll(random), third.roll(random)
            };
        }

        /** Все возможные выходы без обращения к RNG — для атомарного reserve slots. */
        public ItemStack[] outputCapacityPreview() {
            return new ItemStack[] {
                main.stack(), first.preview(), second.preview(), third.preview()
            };
        }
    }

    /** Побочный результат в своём закреплённом слоте; {@code item == null} означает пустой slot. */
    public record Byproduct(ItemRef item, int chancePermille) {
        public Byproduct {
            if (chancePermille < 0 || chancePermille > CHANCE_SCALE) {
                throw new IllegalArgumentException("ЦФ1УР: шанс вне диапазона: " + chancePermille);
            }
            if (item == null && chancePermille != 0) {
                throw new IllegalArgumentException("ЦФ1УР: пустой output не может иметь шанс");
            }
        }

        public ItemStack preview() {
            return item == null ? ItemStack.EMPTY : item.stack(1);
        }

        public ItemStack roll(RandomSource random) {
            return item != null && random.nextInt(CHANCE_SCALE) < chancePermille ? item.stack(1) : ItemStack.EMPTY;
        }
    }
}
