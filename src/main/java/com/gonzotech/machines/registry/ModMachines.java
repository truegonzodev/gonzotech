package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.AccumulatorBlock;
import com.gonzotech.machines.block.AlloyFoundryBlock;
import com.gonzotech.machines.block.BoilerBlock;
import com.gonzotech.machines.block.CentrifugeBlock;
import com.gonzotech.machines.block.CrusherBlock;
import com.gonzotech.machines.block.CobbleGeneratorBlock;
import com.gonzotech.machines.block.CondenserBlock;
import com.gonzotech.machines.block.ElectricFurnaceBlock;
import com.gonzotech.machines.block.FireboxBlock;
import com.gonzotech.machines.block.PumpBlock;
import com.gonzotech.machines.block.SecondAccumulatorBlock;
import com.gonzotech.machines.block.SecondCobbleGeneratorBlock;
import com.gonzotech.machines.block.SecondElectricFurnaceBlock;
import com.gonzotech.machines.block.SecondMachineStubBlock;
import com.gonzotech.machines.block.SecondPumpBlock;
import com.gonzotech.machines.block.SingularEnergySourceBlock;
import com.gonzotech.machines.block.SingularHeatSourceBlock;
import com.gonzotech.machines.block.StirlingBlock;
import com.gonzotech.machines.block.TurbineCasingBlock;
import com.gonzotech.machines.block.TurbineRotorBlock;
import com.gonzotech.machines.item.WrenchItem;
import com.gonzotech.machines.network.CompositePipeBlock;
import com.gonzotech.machines.network.ItemFilterBlock;
import com.gonzotech.machines.network.SecondItemFilterBlock;
import com.gonzotech.machines.network.SecondCompositePipeBlock;
import com.gonzotech.machines.network.SecondItemNodeBlock;
import com.gonzotech.machines.network.SecondItemPipeBlock;
import com.gonzotech.machines.network.SecondNodeBlock;
import com.gonzotech.machines.network.SecondPipeBlock;
import com.gonzotech.machines.network.SecondUniversalFluidNodeBlock;
import com.gonzotech.machines.network.SecondUniversalFluidPipeBlock;
import com.gonzotech.machines.network.SecondUniversalNodeBlock;
import com.gonzotech.machines.network.ItemScavengerBlock;
import com.gonzotech.machines.network.ItemNodeBlock;
import com.gonzotech.machines.network.ItemPipeBlock;
import com.gonzotech.machines.network.NodeBlock;
import com.gonzotech.machines.network.PipeBlock;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.network.UniversalFluidNodeBlock;
import com.gonzotech.machines.network.UniversalFluidPipeBlock;
import com.gonzotech.machines.network.UniversalNodeBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Реестр блоков и предметов паровой ветки энергетики (Фаза 2).
 * <p>
 * Отдельный реестр в пакете {@code machines}, чтобы не смешивать
 * машинный контент с рудной базой в {@code core.registry}.
 */
public final class ModMachines {

    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(GonzoTechMod.MOD_ID);

    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(GonzoTechMod.MOD_ID);

    /**
     * Базовые свойства машин и логистики из утверждённой таблицы. Они намеренно
     * не требуют корректного инструмента для дропа: кирка остаётся лишь лучшим
     * инструментом через тег minecraft:mineable/pickaxe.
     */
    private static BlockBehaviour.Properties material(SoundType sound, float hardness, float explosionResistance) {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .sound(sound)
            .strength(hardness, explosionResistance);
    }

    /** Топка, котёл и электропечь. */
    private static BlockBehaviour.Properties machineMetal() {
        return material(SoundType.METAL, 3.5f, 6.0f);
    }

    /** Помпа, генератор Стирлинга, конденсатор и генератор булыжника. */
    private static BlockBehaviour.Properties machineCopper() {
        return material(SoundType.COPPER, 3.5f, 5.0f);
    }

    /** Энергохранилище, провод и его узел. */
    private static BlockBehaviour.Properties lightMetal() {
        return material(SoundType.METAL, 1.9f, 6.0f);
    }

    /** Остальные трубы и узлы. */
    private static BlockBehaviour.Properties copperLogistics() {
        return material(SoundType.COPPER, 1.5f, 5.0f);
    }

    /** Фильтр и отсеиватель: медный звук, но корпус прочнее тонкой трубы. */
    private static BlockBehaviour.Properties copperLogisticsMachine() {
        return material(SoundType.COPPER, 3.5f, 6.0f);
    }

    /**
     * Как {@link #machineMetal()}, но с {@code .noOcclusion()} — для блоков с
     * кастомной 3D-моделью, которая НЕ является полным кубом (котёл).
     */
    private static BlockBehaviour.Properties machineMetalCustomShape() {
        return machineMetal().noOcclusion();
    }

    // ─────────────────────────── блоки ───────────────────────────

    public static final DeferredBlock<FireboxBlock> FIREBOX =
        BLOCKS.registerBlock("firebox", FireboxBlock::new, machineMetal());

    public static final DeferredBlock<BoilerBlock> BOILER =
        BLOCKS.registerBlock("boiler", BoilerBlock::new, machineMetalCustomShape());

    public static final DeferredBlock<StirlingBlock> STIRLING =
        BLOCKS.registerBlock("stirling_generator", StirlingBlock::new, machineCopper());

    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE =
        BLOCKS.registerBlock("electric_furnace", ElectricFurnaceBlock::new, machineMetal());

    public static final DeferredBlock<CondenserBlock> CONDENSER =
        BLOCKS.registerBlock("condenser", CondenserBlock::new, machineCopper());

    public static final DeferredBlock<PumpBlock> PUMP =
        BLOCKS.registerBlock("pump", PumpBlock::new, machineCopper());

    public static final DeferredBlock<AccumulatorBlock> ACCUMULATOR =
        BLOCKS.registerBlock("accumulator", AccumulatorBlock::new, lightMetal());

    public static final DeferredBlock<CobbleGeneratorBlock> COBBLE_GENERATOR =
        BLOCKS.registerBlock("cobble_generator", CobbleGeneratorBlock::new, machineCopper());

    /** Дробилка: первая стадия извлечения raw-материала из рудного блока. */
    public static final DeferredBlock<CrusherBlock> CRUSHER =
        BLOCKS.registerBlock("crusher", CrusherBlock::new, machineMetal());

    /** ЦФ1УР — атомная центрифуга для первой стадии промывки руд. */
    public static final DeferredBlock<CentrifugeBlock> CENTRIFUGE =
        BLOCKS.registerBlock("centrifuge", CentrifugeBlock::new, machineMetal());

    /** Creative-only GTH emitter without an internal storage or crafting recipe. */
    public static final DeferredBlock<SingularHeatSourceBlock> SINGULAR_HEAT_SOURCE =
        BLOCKS.registerBlock("singular_heat_source", SingularHeatSourceBlock::new, machineMetal());

    /** Creative-only GTU emitter without an internal storage or crafting recipe. */
    public static final DeferredBlock<SingularEnergySourceBlock> SINGULAR_ENERGY_SOURCE =
        BLOCKS.registerBlock("singular_energy_source", SingularEnergySourceBlock::new, lightMetal());

    /** Бесшовный после формирования внешний корпус прямоугольной паровой турбины. */
    public static final DeferredBlock<TurbineCasingBlock> TURBINE_CASING =
        BLOCKS.registerBlock("turbine_casing", TurbineCasingBlock::new, machineMetal());

    /** Единственно допустимое содержимое внутреннего объёма турбины. */
    public static final DeferredBlock<TurbineRotorBlock> TURBINE_ROTOR =
        BLOCKS.registerBlock("turbine_rotor", TurbineRotorBlock::new, machineMetal());

    // ─────────────────────────── трубы энергосети (логистика) ───────────────────────────
    // Axis-блоки без BlockEntity: состояние (ось + режим) в блокстейте, передача —
    // пассивны: слив дотягивает PipeRouting (труба не тикает). noOcclusion, модель не
    // полный куб (тонкая труба).

    private static BlockBehaviour.Properties powerLine() {
        return lightMetal().noOcclusion();
    }

    private static BlockBehaviour.Properties pipe() {
        return copperLogistics().noOcclusion();
    }

    public static final DeferredBlock<PipeBlock> WIRE =
        BLOCKS.registerBlock("first_wire", props -> new PipeBlock(props, PipeType.WIRE), powerLine());

    public static final DeferredBlock<PipeBlock> HEAT_PIPE =
        BLOCKS.registerBlock("first_heat_pipe", props -> new PipeBlock(props, PipeType.HEAT), pipe());

    public static final DeferredBlock<PipeBlock> WATER_PIPE =
        BLOCKS.registerBlock("first_water_pipe", props -> new PipeBlock(props, PipeType.WATER), pipe());

    public static final DeferredBlock<PipeBlock> STEAM_PIPE =
        BLOCKS.registerBlock("first_steam_pipe", props -> new PipeBlock(props, PipeType.STEAM), pipe());

    // Предметная труба: активная (тикает, тянет/раздаёт предметы мгновенно).
    public static final DeferredBlock<ItemPipeBlock> ITEM_PIPE =
        BLOCKS.registerBlock("first_item_pipe", ItemPipeBlock::new, pipe());

    // Блоки-узлы: та же труба, но открыта во все 6 сторон (ветвления/уголки).
    public static final DeferredBlock<NodeBlock> WIRE_NODE =
        BLOCKS.registerBlock("first_wire_node", props -> new NodeBlock(props, PipeType.WIRE), powerLine());

    public static final DeferredBlock<NodeBlock> HEAT_NODE =
        BLOCKS.registerBlock("first_heat_node", props -> new NodeBlock(props, PipeType.HEAT), pipe());

    public static final DeferredBlock<NodeBlock> WATER_NODE =
        BLOCKS.registerBlock("first_water_node", props -> new NodeBlock(props, PipeType.WATER), pipe());

    public static final DeferredBlock<NodeBlock> STEAM_NODE =
        BLOCKS.registerBlock("first_steam_node", props -> new NodeBlock(props, PipeType.STEAM), pipe());

    // Предметный узел: активный, обычная точка забора (ставится к сундуку, ПКМ→ЗАБОР).
    public static final DeferredBlock<ItemNodeBlock> ITEM_NODE =
        BLOCKS.registerBlock("first_item_node", ItemNodeBlock::new, pipe());

    // Универсальная жидкостная труба/узел: несут воду И пар одновременно (общий
    // бюджет 800 mB/t), стыкуются и с водными, и с паровыми трубами/машинами.
    public static final DeferredBlock<UniversalFluidPipeBlock> UNIVERSAL_FLUID_PIPE =
        BLOCKS.registerBlock("first_universal_fluid_pipe", UniversalFluidPipeBlock::new, pipe());

    public static final DeferredBlock<UniversalFluidNodeBlock> UNIVERSAL_FLUID_NODE =
        BLOCKS.registerBlock("first_universal_fluid_node", UniversalFluidNodeBlock::new, pipe());

    // Универсальный узел: один куб, несёт ВСЕ 4 типа первого тира (провод +
    // теплотруба + жидкости вода/пар + предметы), открыт во все стороны. Штраф
    // −10% к пропускной способности каждого типа.
    public static final DeferredBlock<UniversalNodeBlock> UNIVERSAL_NODE =
        BLOCKS.registerBlock("first_universal_node", UniversalNodeBlock::new, pipe());

    // Составной блок: несколько типов труб в одном кубе (стакаемость), каждый в
    // своём углу сечения, не соединяясь между собой. Обычно не крафтится — в него
    // собирается связка при добавлении трубы другого типа к уже стоящей.
    public static final DeferredBlock<CompositePipeBlock> COMPOSITE_PIPE =
        BLOCKS.registerBlock("composite_pipe", CompositePipeBlock::new, pipe());


    /** Внутренняя связка труб II, формируется только из совместимых труб II. */
    public static final DeferredBlock<SecondCompositePipeBlock> SECOND_COMPOSITE_PIPE =
        BLOCKS.registerBlock("second_composite_pipe", SecondCompositePipeBlock::new, pipe());

    // ─────────────────────────── фильтр + отсеиватель ───────────────────────────
    // Фильтр — активный полный куб с меню (3 ghost-слота): пропускает совпавшее в
    // свою выходную сеть, отсеянное — в сеть Отсеивателя. Отсеиватель — пассивный
    // блок-якорь второй сети, без меню.

    public static final DeferredBlock<ItemFilterBlock> ITEM_FILTER =
        BLOCKS.registerBlock("item_filter", ItemFilterBlock::new, copperLogisticsMachine());

    public static final DeferredBlock<ItemScavengerBlock> ITEM_SCAVENGER =
        BLOCKS.registerBlock("item_scavenger", ItemScavengerBlock::new, copperLogisticsMachine());


    // ─────────────────────────── второе открытие ───────────────────────────
    // До отдельного балансового patch это полноценные функциональные копии
    // первого уровня. Отдельные registry-id/BlockEntityType оставляют для второго
    // открытия независимую точку роста, не меняя рабочие значения первого.

    public static final DeferredBlock<SecondPipeBlock> SECOND_WIRE =
        BLOCKS.registerBlock("second_wire", props -> new SecondPipeBlock(props, PipeType.WIRE), powerLine());

    public static final DeferredBlock<SecondPipeBlock> SECOND_HEAT_PIPE =
        BLOCKS.registerBlock("second_heat_pipe", props -> new SecondPipeBlock(props, PipeType.HEAT), pipe());

    public static final DeferredBlock<SecondPipeBlock> SECOND_WATER_PIPE =
        BLOCKS.registerBlock("second_water_pipe", props -> new SecondPipeBlock(props, PipeType.WATER), pipe());

    public static final DeferredBlock<SecondPipeBlock> SECOND_STEAM_PIPE =
        BLOCKS.registerBlock("second_steam_pipe", props -> new SecondPipeBlock(props, PipeType.STEAM), pipe());

    public static final DeferredBlock<SecondItemPipeBlock> SECOND_ITEM_PIPE =
        BLOCKS.registerBlock("second_item_pipe", SecondItemPipeBlock::new, pipe());

    public static final DeferredBlock<SecondUniversalFluidPipeBlock> SECOND_UNIVERSAL_FLUID_PIPE =
        BLOCKS.registerBlock("second_universal_fluid_pipe", SecondUniversalFluidPipeBlock::new, pipe());

    public static final DeferredBlock<SecondNodeBlock> SECOND_WIRE_NODE =
        BLOCKS.registerBlock("second_wire_node", props -> new SecondNodeBlock(props, PipeType.WIRE), powerLine());

    public static final DeferredBlock<SecondNodeBlock> SECOND_HEAT_NODE =
        BLOCKS.registerBlock("second_heat_node", props -> new SecondNodeBlock(props, PipeType.HEAT), pipe());

    public static final DeferredBlock<SecondNodeBlock> SECOND_WATER_NODE =
        BLOCKS.registerBlock("second_water_node", props -> new SecondNodeBlock(props, PipeType.WATER), pipe());

    public static final DeferredBlock<SecondNodeBlock> SECOND_STEAM_NODE =
        BLOCKS.registerBlock("second_steam_node", props -> new SecondNodeBlock(props, PipeType.STEAM), pipe());

    public static final DeferredBlock<SecondItemNodeBlock> SECOND_ITEM_NODE =
        BLOCKS.registerBlock("second_item_node", SecondItemNodeBlock::new, pipe());

    public static final DeferredBlock<SecondUniversalFluidNodeBlock> SECOND_UNIVERSAL_FLUID_NODE =
        BLOCKS.registerBlock("second_universal_fluid_node", SecondUniversalFluidNodeBlock::new, pipe());

    public static final DeferredBlock<SecondUniversalNodeBlock> SECOND_UNIVERSAL_NODE =
        BLOCKS.registerBlock("second_universal_node", SecondUniversalNodeBlock::new, pipe());

    public static final DeferredBlock<SecondAccumulatorBlock> SECOND_ACCUMULATOR =
        BLOCKS.registerBlock("second_accumulator", SecondAccumulatorBlock::new, lightMetal());

    public static final DeferredBlock<SecondElectricFurnaceBlock> SECOND_ELECTRIC_FURNACE =
        BLOCKS.registerBlock("second_electric_furnace", SecondElectricFurnaceBlock::new, machineMetal());

    /** Завод сплавов II: 5×5 сетка и первые беспитательные именные плавки. */
    public static final DeferredBlock<AlloyFoundryBlock> SECOND_ALLOY_FOUNDRY =
        BLOCKS.registerBlock("second_alloy_foundry", AlloyFoundryBlock::new, machineMetal());

    /** Размещаемая заготовка: processing/GUI намеренно будут добавлены отдельной задачей. */
    public static final DeferredBlock<SecondMachineStubBlock> SECOND_GRINDER =
        BLOCKS.registerBlock("second_grinder", SecondMachineStubBlock::new, machineMetal());

    public static final DeferredBlock<SecondItemFilterBlock> SECOND_ITEM_FILTER =
        BLOCKS.registerBlock("second_item_filter", SecondItemFilterBlock::new, copperLogisticsMachine());

    public static final DeferredBlock<ItemScavengerBlock> SECOND_ITEM_SCAVENGER =
        BLOCKS.registerBlock("second_item_scavenger", ItemScavengerBlock::new, copperLogisticsMachine());

    public static final DeferredBlock<SecondPumpBlock> SECOND_PUMP =
        BLOCKS.registerBlock("second_pump", SecondPumpBlock::new, machineCopper());

    public static final DeferredBlock<SecondCobbleGeneratorBlock> SECOND_COBBLE_GENERATOR =
        BLOCKS.registerBlock("second_cobble_generator", SecondCobbleGeneratorBlock::new, machineCopper());

    // ─────────────────────────── предметы-блоки ───────────────────────────

    public static final DeferredItem<BlockItem> FIREBOX_ITEM =
        ITEMS.registerSimpleBlockItem("firebox", FIREBOX);

    public static final DeferredItem<BlockItem> BOILER_ITEM =
        ITEMS.registerSimpleBlockItem("boiler", BOILER);

    public static final DeferredItem<BlockItem> STIRLING_ITEM =
        ITEMS.registerSimpleBlockItem("stirling_generator", STIRLING);

    public static final DeferredItem<BlockItem> ELECTRIC_FURNACE_ITEM =
        ITEMS.registerSimpleBlockItem("electric_furnace", ELECTRIC_FURNACE);

    public static final DeferredItem<BlockItem> CONDENSER_ITEM =
        ITEMS.registerSimpleBlockItem("condenser", CONDENSER);

    public static final DeferredItem<BlockItem> PUMP_ITEM =
        ITEMS.registerSimpleBlockItem("pump", PUMP);

    public static final DeferredItem<BlockItem> ACCUMULATOR_ITEM =
        ITEMS.registerSimpleBlockItem("accumulator", ACCUMULATOR);

    public static final DeferredItem<BlockItem> COBBLE_GENERATOR_ITEM =
        ITEMS.registerSimpleBlockItem("cobble_generator", COBBLE_GENERATOR);

    public static final DeferredItem<BlockItem> CRUSHER_ITEM =
        ITEMS.registerSimpleBlockItem("crusher", CRUSHER);

    public static final DeferredItem<BlockItem> CENTRIFUGE_ITEM =
        ITEMS.registerSimpleBlockItem("centrifuge", CENTRIFUGE);

    /** Админский источник: выдаётся только вкладкой «Приколы»/командой. */
    public static final DeferredItem<BlockItem> SINGULAR_HEAT_SOURCE_ITEM =
        ITEMS.registerSimpleBlockItem("singular_heat_source", SINGULAR_HEAT_SOURCE);

    /** Админский источник: выдаётся только вкладкой «Приколы»/командой. */
    public static final DeferredItem<BlockItem> SINGULAR_ENERGY_SOURCE_ITEM =
        ITEMS.registerSimpleBlockItem("singular_energy_source", SINGULAR_ENERGY_SOURCE);

    public static final DeferredItem<BlockItem> TURBINE_CASING_ITEM =
        ITEMS.registerSimpleBlockItem("turbine_casing", TURBINE_CASING);

    public static final DeferredItem<BlockItem> TURBINE_ROTOR_ITEM =
        ITEMS.registerSimpleBlockItem("turbine_rotor", TURBINE_ROTOR);

    public static final DeferredItem<BlockItem> WIRE_ITEM =
        ITEMS.registerSimpleBlockItem("first_wire", WIRE);

    public static final DeferredItem<BlockItem> HEAT_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("first_heat_pipe", HEAT_PIPE);

    public static final DeferredItem<BlockItem> WATER_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("first_water_pipe", WATER_PIPE);

    public static final DeferredItem<BlockItem> STEAM_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("first_steam_pipe", STEAM_PIPE);

    public static final DeferredItem<BlockItem> ITEM_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("first_item_pipe", ITEM_PIPE);

    public static final DeferredItem<BlockItem> WIRE_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("first_wire_node", WIRE_NODE);

    public static final DeferredItem<BlockItem> HEAT_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("first_heat_node", HEAT_NODE);

    public static final DeferredItem<BlockItem> WATER_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("first_water_node", WATER_NODE);

    public static final DeferredItem<BlockItem> STEAM_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("first_steam_node", STEAM_NODE);

    public static final DeferredItem<BlockItem> ITEM_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("first_item_node", ITEM_NODE);

    public static final DeferredItem<BlockItem> UNIVERSAL_FLUID_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("first_universal_fluid_pipe", UNIVERSAL_FLUID_PIPE);

    public static final DeferredItem<BlockItem> UNIVERSAL_FLUID_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("first_universal_fluid_node", UNIVERSAL_FLUID_NODE);

    public static final DeferredItem<BlockItem> UNIVERSAL_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("first_universal_node", UNIVERSAL_NODE);

    public static final DeferredItem<BlockItem> ITEM_FILTER_ITEM =
        ITEMS.registerSimpleBlockItem("item_filter", ITEM_FILTER);

    public static final DeferredItem<BlockItem> ITEM_SCAVENGER_ITEM =
        ITEMS.registerSimpleBlockItem("item_scavenger", ITEM_SCAVENGER);


    // Порядок объявлений совпадает с порядком второго открытия во вкладке
    // Functional: сначала логистика, затем машины.
    public static final DeferredItem<BlockItem> SECOND_WIRE_ITEM =
        ITEMS.registerSimpleBlockItem("second_wire", SECOND_WIRE);

    public static final DeferredItem<BlockItem> SECOND_HEAT_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("second_heat_pipe", SECOND_HEAT_PIPE);

    public static final DeferredItem<BlockItem> SECOND_WATER_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("second_water_pipe", SECOND_WATER_PIPE);

    public static final DeferredItem<BlockItem> SECOND_STEAM_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("second_steam_pipe", SECOND_STEAM_PIPE);

    public static final DeferredItem<BlockItem> SECOND_ITEM_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("second_item_pipe", SECOND_ITEM_PIPE);

    public static final DeferredItem<BlockItem> SECOND_UNIVERSAL_FLUID_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("second_universal_fluid_pipe", SECOND_UNIVERSAL_FLUID_PIPE);

    public static final DeferredItem<BlockItem> SECOND_WIRE_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("second_wire_node", SECOND_WIRE_NODE);

    public static final DeferredItem<BlockItem> SECOND_HEAT_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("second_heat_node", SECOND_HEAT_NODE);

    public static final DeferredItem<BlockItem> SECOND_WATER_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("second_water_node", SECOND_WATER_NODE);

    public static final DeferredItem<BlockItem> SECOND_STEAM_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("second_steam_node", SECOND_STEAM_NODE);

    public static final DeferredItem<BlockItem> SECOND_ITEM_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("second_item_node", SECOND_ITEM_NODE);

    public static final DeferredItem<BlockItem> SECOND_UNIVERSAL_FLUID_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("second_universal_fluid_node", SECOND_UNIVERSAL_FLUID_NODE);

    public static final DeferredItem<BlockItem> SECOND_UNIVERSAL_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("second_universal_node", SECOND_UNIVERSAL_NODE);

    public static final DeferredItem<BlockItem> SECOND_ACCUMULATOR_ITEM =
        ITEMS.registerSimpleBlockItem("second_accumulator", SECOND_ACCUMULATOR);

    public static final DeferredItem<BlockItem> SECOND_ELECTRIC_FURNACE_ITEM =
        ITEMS.registerSimpleBlockItem("second_electric_furnace", SECOND_ELECTRIC_FURNACE);

    public static final DeferredItem<BlockItem> SECOND_ALLOY_FOUNDRY_ITEM =
        ITEMS.registerSimpleBlockItem("second_alloy_foundry", SECOND_ALLOY_FOUNDRY);

    public static final DeferredItem<BlockItem> SECOND_GRINDER_ITEM =
        ITEMS.registerSimpleBlockItem("second_grinder", SECOND_GRINDER);

    public static final DeferredItem<BlockItem> SECOND_ITEM_FILTER_ITEM =
        ITEMS.registerSimpleBlockItem("second_item_filter", SECOND_ITEM_FILTER);

    public static final DeferredItem<BlockItem> SECOND_ITEM_SCAVENGER_ITEM =
        ITEMS.registerSimpleBlockItem("second_item_scavenger", SECOND_ITEM_SCAVENGER);

    public static final DeferredItem<BlockItem> SECOND_PUMP_ITEM =
        ITEMS.registerSimpleBlockItem("second_pump", SECOND_PUMP);

    public static final DeferredItem<BlockItem> SECOND_COBBLE_GENERATOR_ITEM =
        ITEMS.registerSimpleBlockItem("second_cobble_generator", SECOND_COBBLE_GENERATOR);

    // ─────────────────────────── инструменты ───────────────────────────

    /** Гаечный ключ: ПКМ по трубе переключает её режим AUTO/PULL/PUSH. */
    public static final DeferredItem<Item> WRENCH =
        ITEMS.registerItem("wrench", props -> new WrenchItem(props.stacksTo(1)));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private ModMachines() {
    }
}
