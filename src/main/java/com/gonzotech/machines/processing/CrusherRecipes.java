package com.gonzotech.machines.processing;

import com.gonzotech.core.ore.OreDefinition;
import com.gonzotech.core.ore.OreDefinition.Host;
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
 * Серверная таблица дробилки. Для gonzotech-руд bonus зависит исключительно от
 * фактического host block item (stone/deepslate/nether/calcite), а не от металла.
 * Простые камни имеют собственные явно перечисленные строки.
 */
public final class CrusherRecipes {

    private static final int CHANCE_SCALE = 1_000;

    private static final List<InputRecipe> ROCK_RECIPES = List.of(
        r(block(Blocks.GRANITE), recipe(
            guaranteed(grit(ModItems.GRANITE_GRIT)), chance(nugget("lead_nugget"), 40), chance(nugget("aluminum_nugget"), 30), none())),
        r(block(Blocks.ANDESITE), recipe(
            guaranteed(grit(ModItems.ANDESITE_GRIT)), chance(nugget("lithium_nugget"), 40), chance(nugget("copper_nugget"), 30), none())),
        r(block(Blocks.DIORITE), recipe(
            guaranteed(grit(ModItems.DIORITE_GRIT)), chance(nugget("calcium_nugget"), 40), chance(vanilla(Items.QUARTZ), 100), none())),
        r(block(Blocks.CALCITE), recipe(
            guaranteed(vanilla(Items.BONE_MEAL)), chance(vanilla(Items.NAUTILUS_SHELL), 50), chance(nugget("calcium_nugget"), 100), none())),
        r(block(Blocks.BASALT), recipe(
            guaranteed(block(Blocks.ANDESITE)), chance(nugget("nickel_nugget"), 200), chance(vanilla(Items.IRON_NUGGET), 100), none())),
        r(block(Blocks.STONE), recipe(
            guaranteed(grit(ModItems.TRIO_GRIT), 2), chance(vanilla(Items.IRON_NUGGET), 50), chance(nugget("copper_nugget"), 20), none())),
        r(block(Blocks.COBBLESTONE), recipe(
            guaranteed(grit(ModItems.TRIO_GRIT), 2), chance(vanilla(Items.IRON_NUGGET), 50), chance(nugget("copper_nugget"), 20), none())),
        r(block(Blocks.DEEPSLATE), recipe(
            guaranteed(grit(ModItems.ANDESITE_GRIT)), chance(nugget("zirconium_nugget"), 40), chance(nugget("copper_nugget"), 40), none())),
        r(block(Blocks.COBBLED_DEEPSLATE), recipe(
            guaranteed(grit(ModItems.ANDESITE_GRIT)), chance(nugget("zirconium_nugget"), 40), chance(nugget("copper_nugget"), 40), none())),
        r(block(Blocks.NETHERRACK), recipe(
            none(), chance(vanilla(Items.FLINT), 200), chance(vanilla(Items.QUARTZ), 100), chance(vanilla(Items.GOLD_NUGGET), 50))),
        r(block(Blocks.TUFF), recipe(
            guaranteed(vanilla(Items.CLAY_BALL)), chance(nugget("magnesium_nugget"), 50), none(), none())),
        r(block(Blocks.SOUL_SAND), recipe(
            guaranteed(grit(ModItems.CLINKER_GRIT)), chance(raw("manganese"), 90), chance(nugget("copper_nugget"), 30), none())),
        r(block(Blocks.SOUL_SOIL), recipe(
            guaranteed(grit(ModItems.CLINKER_GRIT)), chance(raw("manganese"), 90), chance(nugget("copper_nugget"), 30), none()))
    );

    private CrusherRecipes() {
    }

    /**
     * Returns a recipe from the actual input item. Every registered Gonzo ore
     * block, including the four raw-dropping special ores, receives a host recipe.
     * This does not affect the centrifuge's separate block-only recipes.
     */
    public static Recipe find(ItemStack input) {
        if (input.isEmpty()) return null;

        for (OreDefinition ore : OreDefinition.ALL) {
            Map<Host, ? extends Supplier<? extends Item>> byHost = ModItems.ORE_BLOCK_ITEMS.get(ore.id());
            if (byHost == null) continue;
            for (Map.Entry<Host, ? extends Supplier<? extends Item>> entry : byHost.entrySet()) {
                if (input.is(entry.getValue().get())) {
                    return gonzoOreRecipe(ore.id(), entry.getKey());
                }
            }
        }

        for (InputRecipe recipe : ROCK_RECIPES) {
            if (input.is(recipe.input().get())) return recipe.recipe();
        }
        return null;
    }

    private static Recipe gonzoOreRecipe(String oreId, Host host) {
        ItemRef raw = raw(oreId);
        return switch (host) {
            case STONE -> recipe(guaranteed(raw), chance(raw, 200), guaranteed(grit(ModItems.TRIO_GRIT)), chance(nugget("lithium_nugget"), 200));
            case DEEPSLATE -> recipe(guaranteed(raw), chance(raw, 200), chance(nugget("titanium_nugget"), 100), chance(nugget("rhenium_nugget"), 50));
            case NETHER -> recipe(guaranteed(raw), chance(raw, 200), chance(nugget("lead_nugget"), 330), chance(nugget("platinum_nugget"), 50));
            // OreDefinition currently exposes only calcite_calcium_ore. Keeping
            // this branch host-based means a future calcite-host ore gets the
            // exact same outputs without an ore-specific client decision.
            case CALCITE -> recipe(guaranteed(raw), chance(raw, 200), chance(vanilla(Items.BONE_MEAL), 200), chance(nugget("neodymium_nugget"), 50));
        };
    }

    private static InputRecipe r(ItemRef input, Recipe recipe) {
        return new InputRecipe(input, recipe);
    }

    private static Recipe recipe(SlotResult one, SlotResult two, SlotResult three, SlotResult four) {
        return new Recipe(one, two, three, four);
    }

    private static SlotResult guaranteed(ItemRef item) {
        return guaranteed(item, 1);
    }

    private static SlotResult guaranteed(ItemRef item, int count) {
        return new SlotResult(item, count, CHANCE_SCALE);
    }

    private static SlotResult chance(ItemRef item, int chancePermille) {
        return new SlotResult(item, 1, chancePermille);
    }

    private static SlotResult none() {
        return new SlotResult(null, 0, 0);
    }

    private static ItemRef raw(String oreId) {
        return () -> requireItem(ModItems.RAW_ORE_ITEMS, oreId, "raw ore").get();
    }

    private static ItemRef nugget(String id) {
        return () -> requireItem(ModItems.NUGGET_ITEMS, id, "nugget").get();
    }

    private static ItemRef grit(Supplier<? extends Item> item) {
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
        if (item == null) throw new IllegalStateException("Дробилка: отсутствует " + kind + " " + id);
        return item;
    }

    @FunctionalInterface
    private interface ItemRef extends Supplier<Item> {
        default ItemStack stack(int count) {
            return count <= 0 ? ItemStack.EMPTY : new ItemStack(Objects.requireNonNull(get(), "Crusher item"), count);
        }
    }

    private record InputRecipe(ItemRef input, Recipe recipe) {
    }

    /** Four fixed output positions: base, extra raw, host byproduct 1, host byproduct 2. */
    public record Recipe(SlotResult first, SlotResult second, SlotResult third, SlotResult fourth) {
        public ItemStack[] outputCapacityPreview() {
            return new ItemStack[] {first.preview(), second.preview(), third.preview(), fourth.preview()};
        }

        /** Rolls every non-guaranteed listed outcome independently on the server. */
        public ItemStack[] rollOutputs(RandomSource random) {
            return new ItemStack[] {first.roll(random), second.roll(random), third.roll(random), fourth.roll(random)};
        }
    }

    /** Result associated with one dedicated output slot; null item means no output for that slot. */
    public record SlotResult(ItemRef item, int count, int chancePermille) {
        public SlotResult {
            if (count < 0 || chancePermille < 0 || chancePermille > CHANCE_SCALE) {
                throw new IllegalArgumentException("Дробилка: некорректный результат");
            }
            if ((item == null) != (count == 0)) {
                throw new IllegalArgumentException("Дробилка: пустой слот должен иметь count 0");
            }
            if (item == null && chancePermille != 0) {
                throw new IllegalArgumentException("Дробилка: пустой слот не может иметь шанс");
            }
        }

        public ItemStack preview() {
            return item == null ? ItemStack.EMPTY : item.stack(count);
        }

        public ItemStack roll(RandomSource random) {
            if (item == null) return ItemStack.EMPTY;
            return chancePermille >= CHANCE_SCALE || random.nextInt(CHANCE_SCALE) < chancePermille
                ? item.stack(count) : ItemStack.EMPTY;
        }
    }
}
