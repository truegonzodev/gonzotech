package com.gonzotech.machines.registry;

/**
 * Стаб компилятора для миксинов, которым нужен доступ к реестру (полный
 * ModMachines в локальный typecheck не входит — тянет полдерева блоков).
 * Если реальный ModMachines добавят в список typecheck — удалить этот стаб.
 */
public final class ModMachines {
    public static final net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block>
        THIRD_LEAD_PISTON_HEAD = null;


    // 0.3.319: registry boundary for the real TierThreeCrafting class.
    public static final com.gonzotech.core.registry.ModItems.ItemRef PCFSOZ_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_ACCUMULATOR_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_BATTERY_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_CHEMICAL_PLANT_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_DISPENSING_TAP_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_DISTILLER_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_FERMENTATION_VAT_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_FILLER_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_GRINDER_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_HEAT_NODE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_HEAT_PIPE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_ITEM_NODE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_ITEM_PIPE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_LEAD_CHEST_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_LEAD_PISTON_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_RECTIFIER_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_SNAKETYPE_CONDENSER_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_STICKY_LEAD_PISTON_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_UNIVERSAL_FLUID_NODE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_UNIVERSAL_FLUID_PIPE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_UNIVERSAL_NODE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_WIRE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_WIRE_NODE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    public static final com.gonzotech.core.registry.ModItems.ItemRef THIRD_WORT_KETTLE_ITEM = new com.gonzotech.core.registry.ModItems.ItemRef();
    private ModMachines() {
    }
}
