package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.ReprocessingGrinderBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Измельчитель отработки (0.3.250): два входа (отработанный ТВЭЛ и пустой
 * корпус) и два защищённых выхода (шлаки и бочка отходов).
 */
public final class ReprocessingGrinderMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = 4;
    private final ReprocessingGrinderBlockEntity be;

    public ReprocessingGrinderMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, ReprocessingGrinderBlockEntity.class),
            new SimpleContainerData(3));
    }

    public ReprocessingGrinderMenu(int id, Inventory inventory, ReprocessingGrinderBlockEntity be, ContainerData data) {
        super(ModMenus.REPROCESSING_GRINDER.get(), id, be, data, MACHINE_SLOTS);
        this.be = be;
        addSlot(new InputSlot(be, ReprocessingGrinderBlockEntity.SLOT_TVEL_INPUT, 35, 26));
        addSlot(new InputSlot(be, ReprocessingGrinderBlockEntity.SLOT_HULL_INPUT, 35, 44));
        addSlot(new OutputOnlySlot(be, ReprocessingGrinderBlockEntity.SLOT_SLAG_OUTPUT, 116, 26));
        addSlot(new OutputOnlySlot(be, ReprocessingGrinderBlockEntity.SLOT_BARREL_OUTPUT, 116, 44));
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
            // Щипцы/ковш проходят по содержимому (0.3.79).
            if (stack.getItem() instanceof com.gonzotech.radiation.CarrierItem carrier
                    && carrier.hasCarried(stack)) {
                return mayPlace(carrier.previewCarried(stack));
            }
            return container.canPlaceItem(index, stack);
        }
    }
}
