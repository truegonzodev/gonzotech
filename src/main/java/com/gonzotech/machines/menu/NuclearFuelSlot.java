package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.NuclearFireboxBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Single fuel slot that accepts only the eight specified uranium/thorium forms. */
public final class NuclearFuelSlot extends Slot {

    public NuclearFuelSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return NuclearFireboxBlockEntity.isNuclearFuel(stack);
    }
}
