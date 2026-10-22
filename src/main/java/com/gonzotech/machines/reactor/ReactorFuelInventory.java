package com.gonzotech.machines.reactor;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.machines.block.entity.ReactorControlPanelBlockEntity;
import com.gonzotech.machines.block.entity.ReactorFuelSheathBlockEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Composite GUI view: each visible slot resolves to a real slot in one fuel-sheath block entity. */
public final class ReactorFuelInventory implements Container {

    private final ReactorControlPanelBlockEntity panel;
    private final int activeSlots;
    private final boolean serverAuthoritative;
    private final List<ReactorFuelSheathBlockEntity> sheaths;
    private final NonNullList<ItemStack> clientItems = NonNullList.withSize(
        ReactorControlPanelBlockEntity.MAX_FUEL_SLOTS, ItemStack.EMPTY);

    public ReactorFuelInventory(ReactorControlPanelBlockEntity panel, int activeSlots) {
        this.panel = panel;
        this.activeSlots = Math.max(0, Math.min(ReactorControlPanelBlockEntity.MAX_FUEL_SLOTS, activeSlots));
        if (panel.getLevel() instanceof ServerLevel server && panel.isFormedController()) {
            serverAuthoritative = true;
            sheaths = ReactorFuelStorage.sheathsCached(server, panel);
        } else {
            serverAuthoritative = false;
            sheaths = List.of();
        }
    }

    @Override
    public int getContainerSize() {
        return ReactorControlPanelBlockEntity.MAX_FUEL_SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (int slot = 0; slot < activeSlots; slot++) {
            if (!getItem(slot).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= activeSlots) return ItemStack.EMPTY;
        if (!serverAuthoritative) return clientItems.get(slot);
        SlotAddress address = address(slot);
        return address == null ? ItemStack.EMPTY : sheaths.get(address.sheathIndex()).getItem(address.localSlot());
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= activeSlots) return ItemStack.EMPTY;
        if (!serverAuthoritative) return ContainerHelper.removeItem(clientItems, slot, count);
        if (!canMutate()) return ItemStack.EMPTY;
        SlotAddress address = address(slot);
        return address == null ? ItemStack.EMPTY
            : sheaths.get(address.sheathIndex()).removeItem(address.localSlot(), count);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= activeSlots) return ItemStack.EMPTY;
        if (!serverAuthoritative) return ContainerHelper.takeItem(clientItems, slot);
        if (!canMutate()) return ItemStack.EMPTY;
        SlotAddress address = address(slot);
        return address == null ? ItemStack.EMPTY
            : sheaths.get(address.sheathIndex()).removeItemNoUpdate(address.localSlot());
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= activeSlots || (!stack.isEmpty() && !canPlaceItem(slot, stack))) return;
        if (!serverAuthoritative) {
            clientItems.set(slot, stack.isEmpty()
                ? ItemStack.EMPTY
                : stack.copyWithCount(Math.min(1, stack.getCount())));
            return;
        }
        if (!canMutate()) return;
        SlotAddress address = address(slot);
        if (address != null) sheaths.get(address.sheathIndex()).setItem(address.localSlot(), stack);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot >= 0 && slot < activeSlots && stack.is(ModItems.UF_TVEL.get());
    }

    @Override
    public void clearContent() {
        if (!serverAuthoritative) {
            for (int slot = 0; slot < clientItems.size(); slot++) clientItems.set(slot, ItemStack.EMPTY);
            return;
        }
        if (!canMutate()) return;
        for (ReactorFuelSheathBlockEntity sheath : sheaths) sheath.clearContent();
    }

    @Override
    public void setChanged() {
        // Server-side slot mutations call the owning sheath BE directly; client changes are menu sync only.
    }

    @Override
    public boolean stillValid(Player player) {
        return panel != null
            && panel.isFormedController()
            && panel.fuelSlotCount() == activeSlots
            && Container.stillValidBlockEntity(panel, player);
    }

    private SlotAddress address(int slot) {
        if (sheaths.isEmpty()) return null;
        int sheathIndex = slot % sheaths.size();
        int localSlot = slot / sheaths.size();
        if (localSlot >= ReactorFuelSheathBlockEntity.MAX_TVEL_SLOTS) return null;
        return new SlotAddress(sheathIndex, localSlot);
    }

    private boolean canMutate() {
        return panel != null && panel.isFormedController() && panel.fuelSlotCount() == activeSlots;
    }

    private record SlotAddress(int sheathIndex, int localSlot) {
    }
}
