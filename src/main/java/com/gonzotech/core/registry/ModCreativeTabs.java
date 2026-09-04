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
                ModItems.INGOT_ITEMS.values().forEach(item -> output.accept(item.get()));
            })
            .build()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FUNCTIONAL_TAB = CREATIVE_TABS.register(
        "functional",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gonzotech.functional"))
            .icon(() -> new ItemStack(com.gonzotech.machines.registry.ModMachines.STIRLING_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.CHALKBOARD_ITEM.get());
                // Заметки учёного — сразу за доской резонанса.
                output.accept(ModItems.SCHOLAR_NOTES.get());
                ModItems.DISCOVERY_ITEMS.forEach(item -> output.accept(item.get()));

                // Фаза 2 — паровая ветка энергетики.
                output.accept(com.gonzotech.machines.registry.ModMachines.FIREBOX_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.BOILER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.STIRLING_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.ELECTRIC_FURNACE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.CONDENSER_ITEM.get());
            })
            .build()
    );

    // ─────────────────────────── Фаза 3: новые вкладки ───────────────────────────

    /** «Блоки Gonzo Tech» — размещаемые декоративные/тестовые блоки. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS_TAB = CREATIVE_TABS.register(
        "blocks",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gonzotech.blocks"))
            .icon(() -> new ItemStack(ModItems.LUNAR_DIRT_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.LUNAR_DIRT_ITEM.get());
            })
            .build()
    );

    /** «Компоненты Gonzo Tech» — предметы-ингредиенты для крафтов. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COMPONENTS_TAB = CREATIVE_TABS.register(
        "components",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gonzotech.components"))
            .icon(() -> new ItemStack(ModItems.PSEUDO_COIL.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.PSEUDO_COIL.get());
            })
            .build()
    );

    /** «Приколы Gonzo Tech» — бесполезные/шуточные предметы. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GAGS_TAB = CREATIVE_TABS.register(
        "gags",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gonzotech.gags"))
            .icon(() -> new ItemStack(ModItems.OBSIDIAN_BUCKET.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.OBSIDIAN_BUCKET.get());
                output.accept(ModItems.BOTCHED_MECHANISM.get());
            })
            .build()
    );

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
