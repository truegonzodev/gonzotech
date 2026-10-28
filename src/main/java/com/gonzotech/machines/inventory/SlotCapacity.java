package com.gonzotech.machines.inventory;

import java.util.function.Function;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Slot-local admission limits, shared by pipes, vanilla transfer and handlers. */
public interface SlotCapacity {
    int slotCapacity(int slot);

    static int limit(Container inventory, int slot) {
        int limit = inventory.getMaxStackSize();
        return inventory instanceof SlotCapacity slots
            ? Math.max(0, Math.min(limit, slots.slotCapacity(slot))) : limit;
    }

    /** Bound the offer BEFORE the delegate can remove/store anything. No drops/trim. */
    static ItemStack insert(Container inventory, int slot, ItemStack stack,
                            Function<ItemStack, ItemStack> delegate) {
        if (stack.isEmpty()) return delegate.apply(stack);
        // Preserve the vanilla path for ordinary, already-valid offers. This also
        // honours globally one-item containers (e.g. reactor fuel) without opt-in.
        if (!(inventory instanceof SlotCapacity) && stack.getCount() <= stack.getMaxStackSize()
            && inventory.getMaxStackSize() >= stack.getMaxStackSize()) return delegate.apply(stack);
        int room = Math.min(stack.getMaxStackSize(), limit(inventory, slot))
            - inventory.getItem(slot).getCount();
        if (room <= 0) return stack;
        if (stack.getCount() <= room) return delegate.apply(stack);
        ItemStack offered = stack.copyWithCount(room);
        ItemStack remainder = delegate.apply(offered);
        int accepted = room - remainder.getCount();
        return accepted <= 0 ? stack : stack.copyWithCount(stack.getCount() - accepted);
    }

    /** setItem is replacement, NOT insertion: never silently truncate or spill. */
    static void requireReplacement(Container inventory, int slot, ItemStack stack) {
        // A pre-existing legacy stack may be updated in place; do not migrate it here.
        if (stack == inventory.getItem(slot)) return;
        if (!stack.isEmpty() && stack.getCount() > Math.min(stack.getMaxStackSize(), limit(inventory, slot))) {
            throw new IllegalArgumentException("Replacement exceeds slot capacity: " + slot);
        }
    }
}
