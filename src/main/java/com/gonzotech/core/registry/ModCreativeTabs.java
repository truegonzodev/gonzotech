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
                // Порядок во вкладке: слитки → блоки → пыль → самородки.
                ModItems.INGOT_ITEMS.values().forEach(item -> output.accept(item.get()));
                ModItems.METAL_BLOCK_ITEMS.values().forEach(item -> output.accept(item.get()));
                ModItems.DUST_ITEMS.values().forEach(item -> output.accept(item.get()));
                ModItems.NUGGET_ITEMS.values().forEach(item -> output.accept(item.get()));
                output.accept(ModItems.SILICON.get());
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
                // Инструменты: ключ сразу за заметками, затем измерительные приборы.
                output.accept(com.gonzotech.machines.registry.ModMachines.WRENCH.get());
                output.accept(ModItems.DOSIMETER.get());
                output.accept(ModItems.UV_METER.get());
                ModItems.DISCOVERY_ITEMS.forEach(item -> output.accept(item.get()));

                // Фаза 2 — паровая ветка энергетики.
                output.accept(com.gonzotech.machines.registry.ModMachines.FIREBOX_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.BOILER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.STIRLING_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.ELECTRIC_FURNACE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.CONDENSER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.PUMP_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.ACCUMULATOR_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.COBBLE_GENERATOR_ITEM.get());

                // Логистика — трубы энергосети + гаечный ключ.
                output.accept(com.gonzotech.machines.registry.ModMachines.WIRE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.HEAT_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.WATER_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.STEAM_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.ITEM_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.UNIVERSAL_FLUID_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.WIRE_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.HEAT_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.WATER_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.STEAM_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.ITEM_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.UNIVERSAL_FLUID_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.UNIVERSAL_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.ITEM_FILTER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.ITEM_SCAVENGER_ITEM.get());
                // Технологическая цепочка обработки руды завершает список машин.
                output.accept(com.gonzotech.machines.registry.ModMachines.CRUSHER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.CENTRIFUGE_ITEM.get());
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
                // Строительные материалы переработки — в согласованном порядке.
                output.accept(ModItems.ARMOR_CONCRETE_ITEM.get());
                output.accept(ModItems.REINFORCED_ARMOR_CONCRETE_ITEM.get());
                output.accept(ModItems.DURABLE_CONCRETE_ITEM.get());
                output.accept(ModItems.PORCELAIN_ITEM.get());
                output.accept(ModItems.SLAG_CONCRETE_ITEM.get());
                output.accept(ModItems.INDUSTRIAL_CONCRETE_ITEM.get());
                output.accept(ModItems.REINFORCED_INDUSTRIAL_CONCRETE_ITEM.get());
                // Фаза 4 — блоки космоса.
                output.accept(ModItems.LUNAR_STONE_ITEM.get());
                output.accept(ModItems.RICH_LUNAR_STONE_ITEM.get());
                output.accept(ModItems.LUNAR_SAND_ITEM.get());
                output.accept(ModItems.MARTIAN_STONE_ITEM.get());
                output.accept(ModItems.RICH_MARTIAN_STONE_ITEM.get());
                output.accept(ModItems.MARTIAN_DIRT_ITEM.get());
                output.accept(ModItems.SUPERDENSE_ICE_ITEM.get());
                output.accept(ModItems.EUROPAN_ICE_ITEM.get());
                output.accept(ModItems.METEOR_ITEM.get());
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
                output.accept(ModItems.GRANITE_GRIT.get());
                output.accept(ModItems.ANDESITE_GRIT.get());
                output.accept(ModItems.DIORITE_GRIT.get());
                output.accept(ModItems.TRIO_GRIT.get());
                output.accept(ModItems.CLINKER_GRIT.get());
                output.accept(ModItems.ARMOR_MIX.get());
                output.accept(ModItems.ANDESITE_SILICATE_CLINKER.get());
                output.accept(ModItems.WHITE_PORCELAIN_BATCH.get());
                output.accept(ModItems.REBAR.get());
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
                output.accept(ModItems.THE_PROTO_MASH.get());
                output.accept(ModItems.THE_FRUIT_MASH.get());
            })
            .build()
    );

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
