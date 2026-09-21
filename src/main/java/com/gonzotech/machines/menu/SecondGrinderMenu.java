package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SecondGrinderBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Two-slot menu for Grinder II: validated material input and protected output. */
public final class SecondGrinderMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 2;
    private final SecondGrinderBlockEntity be;

    public SecondGrinderMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, SecondGrinderBlockEntity.class),
            new SimpleContainerData(3));
    }

    public SecondGrinderMenu(int id, Inventory inventory, SecondGrinderBlockEntity be, ContainerData data) {
        super(ModMenus.SECOND_GRINDER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;
        addSlot(new InputSlot(be, SecondGrinderBlockEntity.SLOT_INPUT, 44, 35));
        addSlot(new OutputOnlySlot(be, SecondGrinderBlockEntity.SLOT_OUTPUT, 116, 35));
        addPlayerInventory(inventory, 8, 84);
    }

    public int gtu() {
        return data.get(0);
    }

    public int grindProgress() {
        return data.get(1);
    }

    public int grindTotal() {
        return data.get(2);
    }

    public int grindProgressPercent() {
        int total = grindTotal();
        return total <= 0 ? 0 : Math.min(100, (int) ((long) grindProgress() * 100L / total));
    }

    private static final class InputSlot extends Slot {
        private final Container container;
        private final int index;

        InputSlot(Container container, int index, int x, int y) {
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
