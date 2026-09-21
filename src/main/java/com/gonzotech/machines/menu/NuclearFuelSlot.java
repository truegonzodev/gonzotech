package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.NuclearFireboxBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Единственный топливный слот. Принимает только природные формы урана/тория
 * (руда, слиток, самородок, блок, пыль) — список задаёт
 * {@link NuclearFireboxBlockEntity#burnTicks}; изотопы и готовые топливные
 * смеси пойдут в будущие продвинутые реакторы.
 */

public final class NuclearFuelSlot extends Slot {

    public NuclearFuelSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return NuclearFireboxBlockEntity.isNuclearFuel(stack);
    }
}
