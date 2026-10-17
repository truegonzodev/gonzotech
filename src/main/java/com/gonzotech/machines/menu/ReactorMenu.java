package com.gonzotech.machines.menu;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Fuel inventory menu; the server-provided active slot count follows the sheath count. */
public final class ReactorMenu extends BaseMachineMenu {

    private static final int PLAYER_INVENTORY_X = 8;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int GRID_FIRST_X = 62;
    private static final int GRID_FIRST_Y = 17;
    private static final int GRID_STEP = 18;
    private static final int GRID_ROWS = 3;

    private final ReactorControlPanelBlockEntity panel;
    private final int activeFuelSlots;

    public ReactorMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory,
            MenuHelper.readBlockEntity(inventory, buffer, ReactorControlPanelBlockEntity.class),
            buffer.readVarInt());
    }

    public ReactorMenu(int id, Inventory inventory, ReactorControlPanelBlockEntity panel, int activeFuelSlots) {
        super(ModMenus.REACTOR.get(), id, panel, new SimpleContainerData(0), clampSlots(activeFuelSlots));
        this.panel = panel;
        this.activeFuelSlots = clampSlots(activeFuelSlots);
        panel.syncMenuFuelSlotCount(this.activeFuelSlots);

        for (int slot = 0; slot < this.activeFuelSlots; slot++) {
            addSlot(new FuelSlot(panel, slot, fuelSlotX(slot), fuelSlotY(slot)));
        }
        addPlayerInventory(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    private static int clampSlots(int count) {
        return Math.max(0, Math.min(ReactorControlPanelBlockEntity.MAX_FUEL_SLOTS, count));
    }

    public int activeFuelSlots() {
        return activeFuelSlots;
    }

    public static int fuelSlotX(int slot) {
        return GRID_FIRST_X + (slot / GRID_ROWS) * GRID_STEP;
    }

    public static int fuelSlotY(int slot) {
        return GRID_FIRST_Y + (slot % GRID_ROWS) * GRID_STEP;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return super.stillValid(player);
        return panel.isFormedController()
            && panel.fuelSlotCount() == activeFuelSlots
            && super.stillValid(player);
    }

    private static final class FuelSlot extends Slot {
        FuelSlot(ReactorControlPanelBlockEntity panel, int index, int x, int y) {
            super(panel, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(ModItems.UF_TVEL.get());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
