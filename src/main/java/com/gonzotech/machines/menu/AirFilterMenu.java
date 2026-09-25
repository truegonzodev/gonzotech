package com.gonzotech.machines.menu;

import com.gonzotech.cleanroom.AirFilterBlockEntity;
import com.gonzotech.machines.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Menu for the clean-room air filter: three real inventory slots plus player inventory. */
public final class AirFilterMenu extends AbstractContainerMenu {
    private final AirFilterBlockEntity filter;

    public AirFilterMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, findFilter(inventory, buffer));
    }

    public AirFilterMenu(int id, Inventory inventory, AirFilterBlockEntity filter) {
        super(ModMenus.AIR_FILTER.get(), id);
        this.filter = filter;

        for (int slot = 0; slot < 3; slot++) {
            addSlot(new Slot(filter, slot, 62 + slot * 18, 20));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 51 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 109));
        }
    }

    private static AirFilterBlockEntity findFilter(Inventory inventory, RegistryFriendlyByteBuf buffer) {
        var blockEntity = inventory.player.level().getBlockEntity(buffer.readBlockPos());
        if (blockEntity instanceof AirFilterBlockEntity filter) return filter;
        throw new IllegalStateException("Missing air filter block entity");
    }

    public AirFilterBlockEntity filter() {
        return filter;
    }

    @Override
    public boolean stillValid(Player player) {
        return !filter.isRemoved() && filter.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack source = slot.getItem();
            result = source.copy();
            if (index < 3) {
                if (!moveItemStackTo(source, 3, slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(source, 0, 3, false)) {
                return ItemStack.EMPTY;
            }
            if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }
}
