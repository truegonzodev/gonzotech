package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.item.GreatswordItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry and creative-tab placement for the six crafted large swords. */
public final class GreatswordContent {

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GonzoTechMod.MOD_ID);

    public static final DeferredItem<GreatswordItem> STONE_GREATSWORD =
        register("stone_greatsword", ToolMaterial.STONE, 113, 11.0F, 0.35F, false);
    public static final DeferredItem<GreatswordItem> IRON_GREATSWORD =
        register("iron_greatsword", ToolMaterial.IRON, 280, 13.0F, 0.32F, false);
    public static final DeferredItem<GreatswordItem> GOLDEN_GREATSWORD =
        register("golden_greatsword", ToolMaterial.GOLD, 23, 14.0F, 0.29F, false);
    public static final DeferredItem<GreatswordItem> DIAMOND_GREATSWORD =
        register("diamond_greatsword", ToolMaterial.DIAMOND, 432, 15.0F, 0.44F, false);
    public static final DeferredItem<GreatswordItem> NETHERITE_GREATSWORD =
        register("netherite_greatsword", ToolMaterial.NETHERITE, 881, 17.0F, 0.38F, false);
    /** Template defaults are replaced with composition-stamped components by the dynamic recipe. */
    public static final DeferredItem<GreatswordItem> ALLOY_GREATSWORD =
        register("alloy_greatsword", ToolMaterial.IRON, 280, 13.0F, 0.32F, true);

    private GreatswordContent() {
    }

    private static DeferredItem<GreatswordItem> register(String id, ToolMaterial material,
                                                         int durability, float damage,
                                                         float speed, boolean noGenericRepair) {
        return ITEMS.registerItem(id, properties ->
            new GreatswordItem(material, durability, damage, speed, noGenericRepair, properties));
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(GreatswordContent::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(ModCreativeTabs.EQUIPMENT_TAB.getKey())) return;
        event.accept(STONE_GREATSWORD.get());
        event.accept(IRON_GREATSWORD.get());
        event.accept(GOLDEN_GREATSWORD.get());
        event.accept(DIAMOND_GREATSWORD.get());
        event.accept(NETHERITE_GREATSWORD.get());
        event.accept(ALLOY_GREATSWORD.get());
    }
}
