package com.gonzotech.machines.miner;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Что автошахтер считает рудой (0.3.256): суффикс {@code _ore} пути реестра
 * (ванильные и модовые руды, включая глубинные варианты) плюс рудные блоки
 * гонзо ({@code RAW_ORE_BLOCK_ITEMS}). Финальный тег {@code mining_targets}
 * придёт с пакетом сейсмики.
 */
public final class MinerOres {

    private MinerOres() {
    }

    public static boolean isOre(BlockState state) {
        return isOre(state.getBlock());
    }

    public static boolean isOre(Block block) {
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        if (path.endsWith("_ore")) return true;
        return rawOreBlock(block.asItem());
    }

    private static boolean rawOreBlock(Item item) {
        if (item == null) return false;
        for (var entry : ModItems.RAW_ORE_BLOCK_ITEMS.values()) {
            if (entry.get().asItem() == item) return true;
        }
        return false;
    }

    /** Предмет-иконка для дисплея метки: блок руды в изометрии, как в GUI. */
    public static Item displayItem(Block block) {
        return block.asItem();
    }
}
