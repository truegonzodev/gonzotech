package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.ore.OreDefinition.Host;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GonzoTechMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORES_TAB = CREATIVE_TABS.register(
        "ores",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gonzotech.ores"))
            // uranium существует только как DEEPSLATE-вариант — берём его как иконку.
            .icon(() -> new ItemStack(ModItems.ORE_BLOCK_ITEMS.get("uranium").get(Host.DEEPSLATE).get()))
            .displayItems((params, output) -> {
                ModItems.ORE_BLOCK_ITEMS.values().forEach(byHost ->
                    byHost.values().forEach(item -> output.accept(item.get())));
                ModItems.RAW_ORE_ITEMS.values().forEach(item -> output.accept(item.get()));
                output.accept(ModItems.CHALKBOARD_ITEM.get());
            })
            .build()
    );

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
