package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.PumpBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Слот входа помпы: принимает ТОЛЬКО пустую тару, которую помпа умеет наполнять
 * (пустое ведро или стеклянный пузырёк). Всё остальное — запрещено.
 */
public class PumpInputSlot extends Slot {

    public PumpInputSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return PumpBlockEntity.isEmptyContainer(stack);
    }
}
