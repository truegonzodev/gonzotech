package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.core.ore.OreDefinition.Host;
import com.gonzotech.core.fluid.ModFluids;
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
                output.accept(ModItems.CUSTOM_ALLOY.get());
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
                output.accept(ModItems.SPEEDOMETER.get());
                ModItems.DISCOVERY_ITEMS.forEach(item -> output.accept(item.get()));

                // Фаза 2 — паровая ветка энергетики.
                output.accept(com.gonzotech.machines.registry.ModMachines.FIREBOX_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.BOILER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.STIRLING_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.TURBINE_CASING_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.TURBINE_ROTOR_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.STEAMGEN_CASING_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.STEAMGEN_CORE_ITEM.get());
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


                // Порядок фиксирует progression-витрину второго открытия.
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_WIRE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_HEAT_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_WATER_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_STEAM_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_ITEM_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_UNIVERSAL_FLUID_PIPE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_WIRE_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_HEAT_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_WATER_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_STEAM_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_ITEM_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_UNIVERSAL_FLUID_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_UNIVERSAL_NODE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_ACCUMULATOR_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_ELECTRIC_FURNACE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_ALLOY_FOUNDRY_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_GRINDER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_PRESS_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_ITEM_FILTER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_ITEM_SCAVENGER_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_PUMP_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SECOND_COBBLE_GENERATOR_ITEM.get());
                // Ядерная топка замыкает вкладку: самая опасная и поздняя машина.
                output.accept(com.gonzotech.machines.registry.ModMachines.NUCLEAR_FIREBOX_ITEM.get());
            })
            .build()
    );

    // ─────────────────────────── Фаза 3: новые вкладки ───────────────────────────

    /** «Снаряжение Gonzo Tech» — будущая линейка вооружения и брони из сплавов. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EQUIPMENT_TAB = CREATIVE_TABS.register(
        "equipment",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gonzotech.equipment"))
            .icon(() -> new ItemStack(ModItems.ALLOY_PICKAXE.get()))
            .displayItems((params, output) -> {
                output.accept(ModItems.ALLOY_PICKAXE.get());
                output.accept(ModItems.ALLOY_SWORD.get());
                output.accept(ModItems.ALLOY_CHESTPLATE.get());
                output.accept(ModItems.ALLOY_HELMET.get());
                output.accept(ModItems.ALLOY_LEGGINGS.get());
                output.accept(ModItems.ALLOY_BOOTS.get());
            })
            .build()
    );

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
                // Саспенс: скалковые руины/данжи.
                output.accept(ModItems.LEAD_STAINED_GLASS_ITEM.get());
                output.accept(ModItems.CRIMSON_OBSIDIAN_ITEM.get());
                output.accept(ModItems.SCULK_BRICKS_ITEM.get());
                output.accept(ModItems.CHISELED_SCULK_BRICKS_ITEM.get());
                output.accept(ModItems.SMOOTH_SCULK_BRICKS_ITEM.get());
                output.accept(ModItems.SCULK_BRICK_STAIRS_ITEM.get());
                output.accept(ModItems.SCULK_BRICK_SLAB_ITEM.get());
                output.accept(ModItems.SCULK_BRICK_WALL_ITEM.get());
                // Радиация: блоки радиоактивных зон и комплексов.
                output.accept(ModItems.DEAD_DIRT_ITEM.get());
                output.accept(ModItems.DEAD_SAND_ITEM.get());
                output.accept(ModItems.DEAD_STONE_ITEM.get());
                output.accept(ModItems.DEAD_LOG_ITEM.get());
                output.accept(ModItems.CORIUM_ITEM.get());
                output.accept(ModFluids.CORIUM_BUCKET.get());
                output.accept(ModItems.WASTE_BARREL_ITEM.get());
                output.accept(ModItems.DEAD_SLIME_BLOCK_ITEM.get());
                output.accept(ModItems.RADIOACTIVE_SLIME_BLOCK_ITEM.get());
                // Метеоры и старые механизмы: строительные элементы руин.
                output.accept(ModItems.WEATHERED_PLATING_ITEM.get());
                output.accept(ModItems.DEBRIS_ITEM.get());
                output.accept(ModItems.WEATHERED_DEBRIS_ITEM.get());
                output.accept(ModItems.MECHANISMS_ITEM.get());
                output.accept(ModItems.WEATHERED_MECHANISMS_ITEM.get());
                output.accept(ModItems.SILICON_CACHE_ITEM.get());
                output.accept(ModItems.PLASTIC_WASTE_ITEM.get());
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
                output.accept(ModItems.COPPER_PLATE.get());
                output.accept(ModItems.COPPER_WIRE.get());
                output.accept(ModItems.ALUMINUM_PLATE.get());
                output.accept(ModItems.ALUMINUM_WIRE.get());
                output.accept(ModItems.IRON_PLATE.get());
                output.accept(ModItems.STEEL_PLATE.get());
                output.accept(ModItems.NICKEL_PLATE.get());
                output.accept(ModItems.STAINLESS_STEEL_PLATE.get());
                output.accept(ModItems.GOLD_PLATE.get());
                output.accept(ModItems.GOLD_WIRE.get());
                output.accept(ModItems.SILVER_WIRE.get());
                output.accept(ModItems.REDSTONE_PLATE.get());
                output.accept(ModItems.REDSTONE_CORE.get());
                output.accept(ModItems.TITANIUM_PLATE.get());
                output.accept(ModItems.SEMICONDUCTOR_PLATE.get());
                output.accept(ModItems.SEMICONDUCTOR_CORE.get());
                output.accept(ModItems.FLAT_PUNCH.get());
                output.accept(ModItems.WEDGE_PUNCH.get());
                output.accept(ModItems.INGOT_FORM.get());
                output.accept(ModItems.PLATE_FORM.get());
                output.accept(ModItems.CORE_FORM.get());
                output.accept(ModItems.COIL.get());
                output.accept(ModItems.INDUCTIVE_MODULE.get());
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

    /** «Приколы Gonzo Tech» — шуточные предметы и некарфтящиеся админ-инструменты. */
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
                // Админские сингулярности — специально не имеют crafting recipes.
                output.accept(com.gonzotech.machines.registry.ModMachines.SINGULAR_HEAT_SOURCE_ITEM.get());
                output.accept(com.gonzotech.machines.registry.ModMachines.SINGULAR_ENERGY_SOURCE_ITEM.get());
            })
            .build()
    );

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }

    private ModCreativeTabs() {
    }
}
