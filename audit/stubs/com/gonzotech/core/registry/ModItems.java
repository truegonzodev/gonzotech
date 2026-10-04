package com.gonzotech.core.registry;

import net.minecraft.world.item.Item;
import java.util.Map;

/** Minimal registry surface needed by PressRecipes' local typecheck. */
public final class ModItems {
    public static final class ItemRef {
        public Item get() { return null; }
    }

    public static final Map<String, ItemRef> INGOT_ITEMS = Map.of();
    public static final Map<String, ItemRef> RAW_ORE_ITEMS = Map.of();

    public static final ItemRef COPPER_PLATE = new ItemRef();
    public static final ItemRef ALUMINUM_PLATE = new ItemRef();
    public static final ItemRef IRON_PLATE = new ItemRef();
    public static final ItemRef STEEL_PLATE = new ItemRef();
    public static final ItemRef NICKEL_PLATE = new ItemRef();
    public static final ItemRef STAINLESS_STEEL_PLATE = new ItemRef();
    public static final ItemRef GOLD_PLATE = new ItemRef();
    public static final ItemRef REDSTONE_PLATE = new ItemRef();
    public static final ItemRef TITANIUM_PLATE = new ItemRef();
    public static final ItemRef ZIRCONIUM_PLATE = new ItemRef();
    public static final ItemRef SEMICONDUCTOR_PLATE = new ItemRef();
    public static final ItemRef COPPER_WIRE = new ItemRef();
    public static final ItemRef ALUMINUM_WIRE = new ItemRef();
    public static final ItemRef GOLD_WIRE = new ItemRef();
    public static final ItemRef SILVER_WIRE = new ItemRef();
    public static final ItemRef REDSTONE_CORE = new ItemRef();
    public static final ItemRef SEMICONDUCTOR_CORE = new ItemRef();
    public static final ItemRef FLAT_PUNCH = new ItemRef();
    public static final ItemRef WEDGE_PUNCH = new ItemRef();
    public static final ItemRef INGOT_FORM = new ItemRef();
    public static final ItemRef PLATE_FORM = new ItemRef();
    public static final ItemRef CORE_FORM = new ItemRef();
    public static final ItemRef URANIUM_FUEL = new ItemRef();
    public static final ItemRef TVEL = new ItemRef();
    public static final ItemRef UF_TVEL = new ItemRef();

    private ModItems() { }
}
