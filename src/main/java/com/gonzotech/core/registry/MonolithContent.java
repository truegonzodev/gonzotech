package com.gonzotech.core.registry;

import com.gonzotech.GonzoTechMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Decorative, rotatable monolith pillar and its block item. */
public final class MonolithContent {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GonzoTechMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GonzoTechMod.MOD_ID);

    public static final DeferredBlock<RotatedPillarBlock> MONOLITH = BLOCKS.registerBlock(
        "monolith", RotatedPillarBlock::new,
        BlockBehaviour.Properties.ofFullCopy(Blocks.COBBLESTONE)
            .mapColor(MapColor.STONE)
            .sound(SoundType.STONE)
            .strength(3.0F, 6.0F)
    );
    public static final DeferredItem<BlockItem> MONOLITH_ITEM = ITEMS.registerSimpleBlockItem("monolith", MONOLITH);

    private MonolithContent() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(MonolithContent::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ModCreativeTabs.BLOCKS_TAB.getKey())) {
            event.accept(MONOLITH_ITEM.get());
        }
    }
}
