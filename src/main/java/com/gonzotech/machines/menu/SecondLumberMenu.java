package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SecondLumberBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** One validated axe slot plus synchronized energy, cut progress, and redstone state. */
public final class SecondLumberMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 1;

    public SecondLumberMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, SecondLumberBlockEntity.class),
            new SimpleContainerData(4));
    }

    public SecondLumberMenu(int id, Inventory inventory, SecondLumberBlockEntity be, ContainerData data) {
        super(ModMenus.SECOND_LUMBER.get(), id, be, data, MACHINE_SLOTS);
        addSlot(new AxeSlot(be, SecondLumberBlockEntity.SLOT_AXE, 44, 35));
        addPlayerInventory(inventory, 8, 84);
    }

    public int gtu() {
        return data.get(0);
    }

    public int cutProgress() {
        return data.get(1);
    }

    public int cutTotal() {
        return data.get(2);
    }

    public boolean powered() {
        return data.get(3) != 0;
    }

    private static final class AxeSlot extends Slot {
        private final Container container;
        private final int index;

        AxeSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
            this.container = container;
            this.index = index;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(index, stack);
        }
    }
}
