package com.gonzotech.machines.processing;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single authoritative table of every material accepted by a procedural alloy.
 *
 * <p>Each registered Gonzo ingot has an equivalent dust (when that item exists)
 * and ten-nugget form. Vanilla iron/copper/gold, diamond and redstone are also
 * source materials. Coal and clay are deliberately recipe-only additives: they
 * may match an explicit named preset but can never silently become generic alloy
 * mass.</p>
 */
public final class AlloyMaterialCatalog {

    /** Ten material units are one ordinary ingot/dust portion. */
    public static final int UNITS_PER_PORTION = 10;

    private static final Catalog CATALOG = createCatalog();

    private AlloyMaterialCatalog() {
    }

    public static Ingredient ingredient(ItemStack stack) {
        return stack.isEmpty() ? null : CATALOG.byItem.get(stack.getItem());
    }

    public static Material material(ResourceLocation id) {
        return CATALOG.byId.get(id);
    }

    public static Map<ResourceLocation, Material> materials() {
        return CATALOG.byId;
    }

    private static Catalog createCatalog() {
        Builder builder = new Builder();

        // 26 primary Gonzo ore metals.
        builder.gonzo("calcium", 20, 75, 10, 15, 15, 30, 18, 0xD4D7C5, ToolTier.STONE);
        builder.gonzo("aluminum", 38, 30, 55, 60, 38, 80, 27, 0xB8C3CE, ToolTier.IRON);
        builder.gonzo("magnesium", 24, 60, 20, 38, 20, 55, 17, 0xC9D5D7, ToolTier.IRON);
        builder.gonzo("sulfur", 5, 95, 5, 0, 5, 10, 12, 0xE9D63D, ToolTier.IRON);
        builder.gonzo("manganese", 64, 78, 28, 15, 60, 38, 52, 0x8E8A8A, ToolTier.IRON);
        builder.gonzo("titanium", 76, 25, 82, 18, 80, 57, 45, 0xA7B2BC, ToolTier.IRON);
        builder.gonzo("barium", 15, 75, 10, 12, 15, 35, 62, 0x9DB98E, ToolTier.IRON);
        builder.gonzo("zinc", 30, 40, 45, 55, 25, 62, 43, 0x9FB1B0, ToolTier.IRON);
        builder.gonzo("tin", 25, 30, 55, 45, 25, 72, 46, 0xBFC5C4, ToolTier.IRON);
        builder.gonzo("boron", 70, 85, 80, 2, 95, 12, 21, 0x3D3534, ToolTier.DIAMOND);
        builder.gonzo("chromium", 70, 45, 95, 35, 85, 42, 55, 0xC7D1D3, ToolTier.IRON);
        builder.gonzo("nickel", 64, 30, 88, 55, 72, 65, 58, 0xB9B37D, ToolTier.IRON);
        builder.gonzo("cobalt", 75, 45, 75, 50, 88, 43, 61, 0x375B9B, ToolTier.DIAMOND);
        builder.gonzo("silver", 25, 20, 75, 100, 30, 78, 49, 0xDCE5EA, ToolTier.IRON);
        builder.gonzo("iodine", 5, 90, 10, 5, 10, 14, 30, 0x362552, ToolTier.IRON);
        builder.gonzo("tungsten", 100, 75, 92, 30, 100, 20, 92, 0x718088, ToolTier.DIAMOND);
        builder.gonzo("mercury", 3, 95, 15, 80, 5, 95, 54, 0xA5B4BC, ToolTier.IRON);
        builder.gonzo("uranium", 40, 55, 35, 25, 80, 35, 76, 0x749E48, ToolTier.IRON);
        builder.gonzo("zirconium", 75, 35, 90, 25, 85, 54, 50, 0xE4DDD0, ToolTier.IRON);
        builder.gonzo("thorium", 45, 50, 40, 20, 82, 40, 73, 0x7D8D5B, ToolTier.IRON);
        builder.gonzo("platinum", 40, 20, 100, 75, 75, 70, 69, 0xE5E1CC, ToolTier.IRON);
        builder.gonzo("tellurium", 20, 90, 75, 5, 50, 20, 57, 0x8C8C74, ToolTier.DIAMOND);
        builder.gonzo("palladium", 50, 25, 98, 70, 65, 68, 66, 0xC7D2CF, ToolTier.DIAMOND);
        builder.gonzo("cesium", 5, 90, 0, 25, 5, 80, 13, 0xD9BF71, ToolTier.IRON);
        builder.gonzo("iridium", 95, 50, 100, 35, 100, 35, 95, 0xE6E6F0, ToolTier.DIAMOND);
        builder.gonzo("osmium", 98, 70, 99, 25, 100, 25, 100, 0x738092, ToolTier.DIAMOND);

        // 21 existing alloy/extra ingots. These are material rows for custom
        // alloys; named-preset products themselves do not receive components.
        builder.gonzo("steel", 68, 28, 62, 28, 74, 58, 62, 0x68737D, ToolTier.IRON);
        builder.gonzo("stainless_steel", 72, 22, 96, 26, 86, 62, 63, 0xA9B6BA, ToolTier.IRON);
        builder.gonzo("corten_steel", 66, 30, 86, 30, 78, 55, 64, 0xA45B3A, ToolTier.IRON);
        builder.gonzo("cast_iron", 70, 72, 52, 18, 80, 24, 71, 0x3B4147, ToolTier.IRON);
        builder.gonzo("plutonium", 42, 58, 30, 22, 78, 33, 77, 0x627F45, ToolTier.IRON);
        builder.gonzo("nitinol", 78, 18, 84, 35, 82, 92, 53, 0xB4B7A3, ToolTier.IRON);
        builder.gonzo("invar", 65, 20, 83, 25, 72, 68, 65, 0x808982, ToolTier.DIAMOND);
        builder.gonzo("lead", 18, 22, 60, 22, 30, 84, 88, 0x59616B, ToolTier.IRON);
        builder.gonzo("neodymium", 42, 48, 68, 40, 56, 50, 59, 0xA79AB7, ToolTier.IRON);
        builder.gonzo("ferromagnetic", 74, 32, 72, 42, 80, 50, 64, 0x485F67, ToolTier.IRON);
        builder.gonzo("cantor", 91, 18, 92, 38, 95, 66, 70, 0x60686D, ToolTier.NETHERITE_PLUS);
        builder.gonzo("vitreloy", 92, 42, 96, 22, 89, 34, 57, 0x536E82, ToolTier.DIAMOND);
        builder.gonzo("semiconductor", 22, 66, 88, 86, 55, 30, 31, 0x476E89, ToolTier.IRON);
        builder.gonzo("vr20", 98, 46, 94, 28, 100, 30, 89, 0x59656B, ToolTier.NETHERITE_PLUS);
        builder.gonzo("stellite", 94, 44, 92, 37, 96, 38, 72, 0x566B7A, ToolTier.NETHERITE_PLUS);
        builder.gonzo("alnico", 61, 36, 70, 48, 68, 55, 54, 0xAD8158, ToolTier.IRON);
        builder.gonzo("telluride", 38, 82, 90, 18, 60, 29, 62, 0x4C786E, ToolTier.DIAMOND);
        builder.gonzo("bismuth", 20, 58, 72, 36, 35, 66, 75, 0xA67F9C, ToolTier.STONE);
        builder.gonzo("rhenium", 88, 42, 95, 32, 99, 40, 83, 0xAAB2B4, ToolTier.DIAMOND);
        builder.gonzo("radium", 18, 65, 15, 18, 58, 42, 74, 0x91D172, ToolTier.IRON);
        builder.gonzo("lithium", 12, 48, 18, 42, 18, 86, 9, 0xD8E5DE, ToolTier.STONE);

        // Vanilla and standalone Gonzo materials called out for custom alloys.
        builder.vanilla("iron", Items.IRON_INGOT, 50, 45, 45, 25, 60, 52, 58, 0xB5B5B5, ToolTier.IRON);
        builder.form(Items.IRON_NUGGET, builder.require("minecraft:iron"), 1, true);
        builder.form(ModItems.IRON_DUST.get(), builder.require("minecraft:iron"), UNITS_PER_PORTION, true);
        builder.vanilla("copper", Items.COPPER_INGOT, 30, 25, 65, 95, 35, 73, 48, 0xC8754B, ToolTier.STONE);
        builder.form(ModItems.COPPER_DUST.get(), builder.require("minecraft:copper"), UNITS_PER_PORTION, true);
        builder.form(ModItems.COPPER_NUGGET.get(), builder.require("minecraft:copper"), 1, true);
        builder.vanilla("gold", Items.GOLD_INGOT, 20, 25, 95, 100, 30, 82, 72, 0xF4C546, ToolTier.IRON);
        builder.form(Items.GOLD_NUGGET, builder.require("minecraft:gold"), 1, true);
        builder.vanilla("diamond", Items.DIAMOND, 95, 88, 97, 8, 92, 8, 36, 0x5FD9DB, ToolTier.DIAMOND);
        builder.vanilla("redstone", Items.REDSTONE, 12, 70, 45, 100, 40, 20, 29, 0xB52626, ToolTier.IRON);
        builder.gonzoStandalone("silicon", ModItems.SILICON.get(), 28, 76, 84, 72, 65, 18, 33, 0x66737F, ToolTier.IRON);

        // They are legal only for exact named formulas, never generic custom alloy.
        builder.recipeOnly("minecraft:coal", Items.COAL, 90, 85, 95, 5, 100, 12, 20, 0x262626, ToolTier.IRON);
        builder.recipeOnly("minecraft:clay", Blocks.CLAY.asItem(), 10, 60, 80, 5, 45, 90, 38, 0x9AA5AC, ToolTier.IRON);

        return builder.build();
    }

    public enum ToolTier {
        STONE(0), IRON(1), DIAMOND(2), NETHERITE_PLUS(3);

        private final int rank;

        ToolTier(int rank) {
            this.rank = rank;
        }

        public boolean isAtLeast(ToolTier other) {
            return rank >= other.rank;
        }
    }

    public record Material(
        ResourceLocation id,
        Item displayItem,
        int strength,
        int brittleness,
        int inertness,
        int conductivity,
        int heatResistance,
        int plasticity,
        int weight,
        int rgb,
        ToolTier toolTier
    ) {
        public ItemStack displayStack() {
            return new ItemStack(displayItem);
        }
    }

    /** One concrete accepted item form and its contribution to a material ratio. */
    public record Ingredient(Material material, int units, boolean genericAllowed) {
    }

    private record Catalog(Map<ResourceLocation, Material> byId, Map<Item, Ingredient> byItem) {
    }

    private static final class Builder {
        private final Map<ResourceLocation, Material> byId = new LinkedHashMap<>();
        private final Map<Item, Ingredient> byItem = new HashMap<>();

        void gonzo(String id, int s, int b, int i, int c, int h, int p, int weight, int rgb, ToolTier tier) {
            Item ingot = ModItems.INGOT_ITEMS.get(id + "_ingot").get();
            Material material = material(ResourceLocation.fromNamespaceAndPath("gonzotech", id), ingot,
                s, b, i, c, h, p, weight, rgb, tier);
            form(ingot, material, UNITS_PER_PORTION, true);
            var dust = ModItems.DUST_ITEMS.get(id + "_dust");
            if (dust != null) form(dust.get(), material, UNITS_PER_PORTION, true);
            var nugget = ModItems.NUGGET_ITEMS.get(id + "_nugget");
            if (nugget != null) form(nugget.get(), material, 1, true);
        }

        void vanilla(String id, Item ingot, int s, int b, int i, int c, int h, int p, int weight, int rgb, ToolTier tier) {
            Material material = material(ResourceLocation.withDefaultNamespace(id), ingot,
                s, b, i, c, h, p, weight, rgb, tier);
            form(ingot, material, UNITS_PER_PORTION, true);
        }

        void gonzoStandalone(String id, Item item, int s, int b, int i, int c, int h, int p, int weight, int rgb, ToolTier tier) {
            Material material = material(ResourceLocation.fromNamespaceAndPath("gonzotech", id), item,
                s, b, i, c, h, p, weight, rgb, tier);
            form(item, material, UNITS_PER_PORTION, true);
        }

        void recipeOnly(String id, Item item, int s, int b, int i, int c, int h, int p, int weight, int rgb, ToolTier tier) {
            Material material = material(ResourceLocation.parse(id), item, s, b, i, c, h, p, weight, rgb, tier);
            form(item, material, UNITS_PER_PORTION, false);
        }

        private Material material(ResourceLocation id, Item displayItem, int s, int b, int i, int c, int h, int p,
                                  int weight, int rgb, ToolTier tier) {
            Material material = new Material(id, displayItem, s, b, i, c, h, p, weight, rgb, tier);
            if (byId.put(id, material) != null) throw new IllegalStateException("Duplicate alloy material: " + id);
            return material;
        }

        void form(Item item, Material material, int units, boolean genericAllowed) {
            if (byItem.put(item, new Ingredient(material, units, genericAllowed)) != null) {
                throw new IllegalStateException("Duplicate alloy item form: " + item);
            }
        }

        Material require(String id) {
            Material material = byId.get(ResourceLocation.parse(id));
            if (material == null) throw new IllegalStateException("Missing alloy material: " + id);
            return material;
        }

        Catalog build() {
            return new Catalog(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(byItem));
        }
    }
}
