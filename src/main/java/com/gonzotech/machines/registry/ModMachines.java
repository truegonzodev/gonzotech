package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.BoilerBlock;
import com.gonzotech.machines.block.CondenserBlock;
import com.gonzotech.machines.block.ElectricFurnaceBlock;
import com.gonzotech.machines.block.FireboxBlock;
import com.gonzotech.machines.block.PumpBlock;
import com.gonzotech.machines.block.StirlingBlock;
import com.gonzotech.machines.item.WrenchItem;
import com.gonzotech.machines.network.CompositePipeBlock;
import com.gonzotech.machines.network.NodeBlock;
import com.gonzotech.machines.network.PipeBlock;
import com.gonzotech.machines.network.PipeType;
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
        BLOCKS.registerBlock("wire", props -> new PipeBlock(props, PipeType.WIRE), pipe());

    public static final DeferredBlock<PipeBlock> HEAT_PIPE =
        BLOCKS.registerBlock("heat_pipe", props -> new PipeBlock(props, PipeType.HEAT), pipe());

    // Блоки-узлы: та же труба, но открыта во все 6 сторон (ветвления/уголки).
    public static final DeferredBlock<NodeBlock> WIRE_NODE =
        BLOCKS.registerBlock("wire_node", props -> new NodeBlock(props, PipeType.WIRE), pipe());

    public static final DeferredBlock<NodeBlock> HEAT_NODE =
        BLOCKS.registerBlock("heat_node", props -> new NodeBlock(props, PipeType.HEAT), pipe());

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

    public static final DeferredItem<BlockItem> WIRE_ITEM =
        ITEMS.registerSimpleBlockItem("wire", WIRE);

    public static final DeferredItem<BlockItem> HEAT_PIPE_ITEM =
        ITEMS.registerSimpleBlockItem("heat_pipe", HEAT_PIPE);

    public static final DeferredItem<BlockItem> WIRE_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("wire_node", WIRE_NODE);

    public static final DeferredItem<BlockItem> HEAT_NODE_ITEM =
        ITEMS.registerSimpleBlockItem("heat_node", HEAT_NODE);

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
