package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.AccumulatorBlock;
import com.gonzotech.machines.block.BoilerBlock;
import com.gonzotech.machines.block.CobbleGeneratorBlock;
import com.gonzotech.machines.block.CondenserBlock;
import com.gonzotech.machines.block.ElectricFurnaceBlock;
import com.gonzotech.machines.block.FireboxBlock;
import com.gonzotech.machines.block.PumpBlock;
import com.gonzotech.machines.block.StirlingBlock;
import com.gonzotech.machines.item.WrenchItem;
import com.gonzotech.machines.network.CompositePipeBlock;
import com.gonzotech.machines.network.ItemNodeBlock;
import com.gonzotech.machines.network.ItemPipeBlock;
import com.gonzotech.machines.network.NodeBlock;
import com.gonzotech.machines.network.PipeBlock;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.network.UniversalFluidNodeBlock;
import com.gonzotech.machines.network.UniversalFluidPipeBlock;
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

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .sound(SoundType.METAL)
            .strength(3.5f, 6.0f)
            .requiresCorrectToolForDrops();
    }

    /**
     * Как {@link #metal()}, но с {@code .noOcclusion()} — для блоков с кастомной
     * 3D-моделью, которая НЕ является полным кубом (например котёл). Без этого
     * Minecraft считает блок полным непрозрачным кубом и на стыках отсекает грани
     * (выступающие части «пропадают», соседние блоки затеняются некорректно).
     */
    private static BlockBehaviour.Properties metalCustomShape() {
        return metal().noOcclusion();
    }

    // ─────────────────────────── блоки ───────────────────────────

    public static final DeferredBlock<FireboxBlock> FIREBOX =
        BLOCKS.registerBlock("firebox", FireboxBlock::new, metal());

    public static final DeferredBlock<BoilerBlock> BOILER =
        BLOCKS.registerBlock("boiler", BoilerBlock::new, metalCustomShape());

    public static final DeferredBlock<StirlingBlock> STIRLING =
        BLOCKS.registerBlock("stirling_generator", StirlingBlock::new, metal());

    public static final DeferredBlock<ElectricFurnaceBlock> ELECTRIC_FURNACE =
        BLOCKS.registerBlock("electric_furnace", ElectricFurnaceBlock::new, metal());

    public static final DeferredBlock<CondenserBlock> CONDENSER =
        BLOCKS.registerBlock("condenser", CondenserBlock::new, metal());

    public static final DeferredBlock<PumpBlock> PUMP =
        BLOCKS.registerBlock("pump", PumpBlock::new, metal());

    public static final DeferredBlock<AccumulatorBlock> ACCUMULATOR =
        BLOCKS.registerBlock("accumulator", AccumulatorBlock::new, metal());

    public static final DeferredBlock<CobbleGeneratorBlock> COBBLE_GENERATOR =
        BLOCKS.registerBlock("cobble_generator", CobbleGeneratorBlock::new, metal());

    // ─────────────────────────── трубы энергосети (логистика) ───────────────────────────
    // Axis-блоки без BlockEntity: состояние (ось + режим) в блокстейте, передача —
    // пассивны: слив дотягивает PipeRouting (труба не тикает). noOcclusion, модель не
    // полный куб (тонкая труба).

    private static BlockBehaviour.Properties pipe() {
        return BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .sound(SoundType.METAL)
            .strength(1.5f, 6.0f)
            .requiresCorrectToolForDrops()
            .noOcclusion();
    }

    public static final DeferredBlock<PipeBlock> WIRE =
        BLOCKS.registerBlock("first_wire", props -> new PipeBlock(props, PipeType.WIRE), pipe());

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
        BLOCKS.registerBlock("first_wire_node", props -> new NodeBlock(props, PipeType.WIRE), pipe());

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

    // Составной блок: несколько типов труб в одном кубе (стакаемость), каждый в
    // своём углу сечения, не соединяясь между собой. Обычно не крафтится — в него
    // собирается связка при добавлении трубы другого типа к уже стоящей.
    public static final DeferredBlock<CompositePipeBlock> COMPOSITE_PIPE =
        BLOCKS.registerBlock("composite_pipe", CompositePipeBlock::new, pipe());

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
