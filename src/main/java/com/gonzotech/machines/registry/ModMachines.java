package com.gonzotech.machines.registry;

import com.gonzotech.GonzoTechMod;
import com.gonzotech.machines.block.BoilerBlock;
import com.gonzotech.machines.block.CondenserBlock;
import com.gonzotech.machines.block.ElectricFurnaceBlock;
import com.gonzotech.machines.block.FireboxBlock;
import com.gonzotech.machines.block.StirlingBlock;
import net.minecraft.world.item.BlockItem;
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

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    private ModMachines() {
    }
}
