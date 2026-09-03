package com.gonzotech.machines.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Слот только на вывод: игрок НЕ может ничего в него положить, только забрать.
 * <p>
 * Используется для слота результата переплавки и для слота забора пустых вёдер
 * у котла (туда предметы кладёт только сама машина автоматически).
 */
public class OutputOnlySlot extends Slot {

    public OutputOnlySlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }
}
