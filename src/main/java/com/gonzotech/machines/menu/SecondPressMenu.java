package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.SecondPressBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Four-slot Press II menu: source, protected output, reusable form, and reusable
 * punch. Form/punch placement is filtered locally by the exact same container
 * rule used by server-side automation.
 */
public final class SecondPressMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 4;
    private final SecondPressBlockEntity be;

    public SecondPressMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, SecondPressBlockEntity.class),
            new SimpleContainerData(3));
    }

    public SecondPressMenu(int id, Inventory inventory, SecondPressBlockEntity be, ContainerData data) {
        super(ModMenus.SECOND_PRESS.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;
        addSlot(new FilteredSlot(be, SecondPressBlockEntity.SLOT_INPUT, 50, 35));
        addSlot(new OutputOnlySlot(be, SecondPressBlockEntity.SLOT_OUTPUT, 128, 35));
        addSlot(new FilteredSlot(be, SecondPressBlockEntity.SLOT_PUNCH, 76, 53));
        addSlot(new FilteredSlot(be, SecondPressBlockEntity.SLOT_FORM, 76, 17));
        addPlayerInventory(inventory, 8, 84);
    }

    public int gtu() {
        return data.get(0);
    }

    public int fatigueProgress() {
        return data.get(1);
    }

    public int fatigueTotal() {
        return data.get(2);
    }

    public int fatiguePercent() {
        int total = fatigueTotal();
        return total <= 0 ? 0 : Math.min(100, (int) ((long) fatigueProgress() * 100L / total));
    }

    private static final class FilteredSlot extends Slot {
        private final Container container;
        private final int index;

        FilteredSlot(Container container, int index, int x, int y) {
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
