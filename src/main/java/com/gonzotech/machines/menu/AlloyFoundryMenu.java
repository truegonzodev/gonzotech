package com.gonzotech.machines.menu;

import com.gonzotech.machines.block.entity.AlloyFoundryBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Меню Завода сплавов: 25 независимых входных ячеек 5×5 и единственный output.
 * Позиция ингредиента не несёт смысла — сервер агрегирует состав всей сетки.
 */
public final class AlloyFoundryMenu extends BaseMachineMenu {

    private static final int MACHINE_SLOTS = AlloyFoundryBlockEntity.INPUT_SLOTS + 1;

    public AlloyFoundryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, AlloyFoundryBlockEntity.class));
    }

    public AlloyFoundryMenu(int id, Inventory inventory, AlloyFoundryBlockEntity be) {
        // There are no resource bars or a timed operation in the free first pass,
        // but BaseMachineMenu still provides authoritative inventory and shift-click.
        super(ModMenus.ALLOY_FOUNDRY.get(), id, be, new SimpleContainerData(0), MACHINE_SLOTS);

        for (int row = 0; row < AlloyFoundryBlockEntity.GRID_HEIGHT; row++) {
            for (int col = 0; col < AlloyFoundryBlockEntity.GRID_WIDTH; col++) {
                int slot = row * AlloyFoundryBlockEntity.GRID_WIDTH + col;
                addSlot(new InputSlot(be, slot, 18 + col * 18, 18 + row * 18));
            }
        }
        addSlot(new OutputOnlySlot(be, AlloyFoundryBlockEntity.SLOT_OUTPUT, 132, 54));
        addPlayerInventory(inventory, 8, 140);
    }

    /** Input slot whose client checks use exactly the server container rule. */
    private static final class InputSlot extends Slot {
        private final Container backing;
        private final int slotIndex;

        InputSlot(Container backing, int slotIndex, int x, int y) {
            super(backing, slotIndex, x, y);
            this.backing = backing;
            this.slotIndex = slotIndex;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return backing.canPlaceItem(slotIndex, stack);
        }
    }
}
