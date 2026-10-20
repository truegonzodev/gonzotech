package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.Metals;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact one-input conversions for the tier-two Grinder.
 *
 * <p>Most metal recipes are one-to-one: every GonzoTech ingot for which
 * {@link Metals#hasDust(String)} is true (including manganese), plus vanilla iron
 * and copper, becomes its registered dust. Iodine, sulfur, and mercury deliberately
 * have no dust items in the material catalogue. Explicit wood recipes remain
 * listed below; any wool item becomes three string.</p>
 */
public final class GrinderRecipes {

    private static final Map<Item, Output> RECIPES = buildRecipes();

    private GrinderRecipes() {
    }

    /** Read-only item recipe view for client recipe integrations. */
    public static java.util.List<ProcessingRecipeDisplay> displayRecipes() {
        java.util.List<ProcessingRecipeDisplay> displays = new java.util.ArrayList<>();
        java.util.List<Map.Entry<Item, Output>> entries = new java.util.ArrayList<>(RECIPES.entrySet());
        entries.sort(java.util.Comparator.comparing(entry ->
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey()).toString()));
        for (Map.Entry<Item, Item> entry : entries) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(entry.getKey()).toString();
            displays.add(new ProcessingRecipeDisplay(id,
                java.util.List.of(java.util.List.of(new ItemStack(entry.getKey()))),
                java.util.List.of(new ProcessingRecipeDisplay.Output(entry.getValue().stack(), 1_000))));
        }

        java.util.List<ItemStack> wool = new java.util.ArrayList<>();
        for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                ItemStack stack = new ItemStack(item);
                if (stack.is(ItemTags.WOOL)) wool.add(stack);
            }
        }
        wool.sort(java.util.Comparator.comparing(stack ->
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        if (wool.isEmpty()) wool.add(new ItemStack(Items.WHITE_WOOL));
        displays.add(new ProcessingRecipeDisplay("tag/wool",
            java.util.List.of(wool),
            java.util.List.of(new ProcessingRecipeDisplay.Output(new ItemStack(Items.STRING, 3), 1_000))));
        return java.util.List.copyOf(displays);
    }

    /** Returns the output stack for one input item, or {@code null} when this input has no grinder recipe. */
    public static ItemStack find(ItemStack input) {
        if (input.isEmpty()) return null;
        if (input.is(ItemTags.WOOL)) return new ItemStack(Items.STRING, 3);
        Output output = RECIPES.get(input.getItem());
        return output == null ? null : output.stack();
    }

    /** Shared by the machine's GUI slot and every automated insertion path. */
    public static boolean accepts(ItemStack input) {
        return find(input) != null;
    }

    private static Map<Item, Output> buildRecipes() {
        Map<Item, Output> recipes = new LinkedHashMap<>();

        // The material registry is the source of truth: a matching dust must
        // already be registered before an ingot can be accepted by the Grinder.
        for (String ingotId : Metals.INGOT_IDS) {
            if (!Metals.hasDust(ingotId)) continue;
            String dustId = Metals.base(ingotId) + "_dust";
            Item ingot = require(ModItems.INGOT_ITEMS, ingotId);
            Item dust = require(ModItems.DUST_ITEMS, dustId);
            put(recipes, ingot, dust);
        }

        // Vanilla metal ingots use GonzoTech's two matching dust items.
        put(recipes, Items.COPPER_INGOT, ModItems.COPPER_DUST.get());
        put(recipes, Items.IRON_INGOT, ModItems.IRON_DUST.get());

        // Explicit non-ingot processing exceptions.
        put(recipes, require(ModItems.RAW_ORE_ITEMS, "manganese"), ModItems.DUST_ITEMS.get("manganese_dust").get());
        put(recipes, Items.QUARTZ, Items.SAND);

        // Древесина в смолистые и твердосмолистые опилки (автор 23.09.2026).
        Item hardSawdust = ModItems.HARD_RESINOUS_SAWDUST.get();
        put(recipes, Items.SPRUCE_LOG, hardSawdust);
        put(recipes, Items.STRIPPED_SPRUCE_LOG, hardSawdust);
        put(recipes, Items.ACACIA_LOG, hardSawdust);
        put(recipes, Items.STRIPPED_ACACIA_LOG, hardSawdust);

        Item resinSawdust = ModItems.RESINOUS_SAWDUST.get();
        put(recipes, Items.PALE_OAK_LOG, resinSawdust);
        put(recipes, Items.STRIPPED_PALE_OAK_LOG, resinSawdust);
        put(recipes, Items.MANGROVE_LOG, resinSawdust);
        put(recipes, Items.STRIPPED_MANGROVE_LOG, resinSawdust);

        // Отработка (0.3.250): отработанный ТВЭЛ измельчается в крайне опасные шлаки.
        put(recipes, ModItems.DEPRESSED_UF_TVEL.get(), ModItems.EXTREMELY_DANGEROUS_SLAG.get());

        // 0.3.253 (автор): новые рецепты обоих измельчителей.
        put(recipes, Items.RAW_IRON, ModItems.CORUNDUM_GRAIN.get());
        put(recipes, Items.FLINT, ModItems.SILICON_CRUMBS.get());
        put(recipes, Items.DIAMOND, ModItems.DIAMOND_DUST.get());
        put(recipes, Items.BREAD, ModItems.THE_PROTO_MASH.get(), 2);
        put(recipes, Items.ICE, Items.SNOWBALL, 5);
        put(recipes, Items.PACKED_ICE, Items.SNOWBALL, 5);
        put(recipes, Items.GLOWSTONE, Items.GLOWSTONE_DUST, 3);
        put(recipes, Items.NETHER_BRICKS, Items.SOUL_SAND);
        put(recipes, Items.BLAZE_ROD, Items.BLAZE_POWDER, 3);

        return Map.copyOf(recipes);
    }

    private static Item require(Map<String, ? extends net.neoforged.neoforge.registries.DeferredItem<Item>> items,
                                String id) {
        var item = items.get(id);
        if (item == null) throw new IllegalStateException("Missing registered grinder material: " + id);
        return item.get();
    }

    private static void put(Map<Item, Output> recipes, Item input, Item output) {
        put(recipes, input, output, 1);
    }

    private static void put(Map<Item, Output> recipes, Item input, Item output, int count) {
        if (count <= 0) throw new IllegalArgumentException("Grinder output count must be positive");
        if (recipes.put(input, new Output(output, count)) != null) {
            throw new IllegalStateException("Duplicate grinder input: " + input);
        }
    }

    /** Выход измельчения: предмет и их число за одну операцию (0.3.253). */
    private record Output(Item item, int count) {
        ItemStack stack() {
            return new ItemStack(item, count);
        }
    }
}
