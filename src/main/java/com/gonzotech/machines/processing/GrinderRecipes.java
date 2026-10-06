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

    private static final Map<Item, Item> RECIPES = buildRecipes();

    private GrinderRecipes() {
    }

    /** Returns the output stack for one input item, or {@code null} when this input has no grinder recipe. */
    public static ItemStack find(ItemStack input) {
        if (input.isEmpty()) return null;
        if (input.is(ItemTags.WOOL)) return new ItemStack(Items.STRING, 3);
        Item output = RECIPES.get(input.getItem());
        return output == null ? null : new ItemStack(output);
    }

    /** Shared by the machine's GUI slot and every automated insertion path. */
    public static boolean accepts(ItemStack input) {
        return find(input) != null;
    }

    private static Map<Item, Item> buildRecipes() {
        Map<Item, Item> recipes = new LinkedHashMap<>();

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

        return Map.copyOf(recipes);
    }

    private static Item require(Map<String, ? extends net.neoforged.neoforge.registries.DeferredItem<Item>> items,
                                String id) {
        var item = items.get(id);
        if (item == null) throw new IllegalStateException("Missing registered grinder material: " + id);
        return item.get();
    }

    private static void put(Map<Item, Item> recipes, Item input, Item output) {
        if (recipes.put(input, output) != null) {
            throw new IllegalStateException("Duplicate grinder input: " + input);
        }
    }
}
