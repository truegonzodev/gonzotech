package com.gonzotech.machines.menu;

import com.gonzotech.machines.energy.WaterProviders;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Слот приёма воды у котла: принимает ТОЛЬКО валидные вёдра-провайдеры воды
 * (обычное ведро воды, ведро рыхлого снега, вёдра с рыбой). Всё остальное —
 * запрещено.
 */
public class WaterInputSlot extends Slot {

    public WaterInputSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return WaterProviders.isWaterProvider(stack);
    }
}
