package com.gonzotech.radiation.engine;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Reconciles external rates after one authoritative menu operation. Never repairs item counts. */
public final class MenuDoseTransaction implements AutoCloseable {
    private static final ThreadLocal<MenuDoseTransaction> ACTIVE = new ThreadLocal<>();
    private final AbstractContainerMenu menu;
    private final boolean quickCraft;
    private final List<ItemStack> originalSlots = new ArrayList<>();
    private final MenuDoseTransaction parent;
    private final List<Lot> lots = new ArrayList<>();
    private final IdentityHashMap<ItemStack, Lot> origins = new IdentityHashMap<>();
    private final boolean root;
    private boolean custom;
    public static void customTransfer() { var t = ACTIVE.get(); if (t != null) t.custom = true; }

    private static final class Lot {
        final ItemStack identity, type;
        final ExternalRadiation.Dose dose;
        int remaining;
        Lot(ItemStack stack) {
            identity = stack; type = stack.copy(); remaining = stack.getCount();
            dose = ExternalRadiation.read(stack);
        }
    }
    private static final class Destination {
        final ItemStack stack;
        int need, assigned;
        double mass;
        long tick = Long.MIN_VALUE;
        Destination(ItemStack stack) { this.stack = stack; need = stack.getCount(); }
        void take(Lot lot) { take(lot, Integer.MAX_VALUE); }
        void take(Lot lot, int limit) {
            int n = Math.min(limit, Math.min(need, lot.remaining));
            need -= n; assigned += n; lot.remaining -= n;
            mass += lot.dose.rate() * n; tick = Math.max(tick, lot.dose.tick());
        }
    }
    public MenuDoseTransaction(AbstractContainerMenu menu, net.minecraft.world.inventory.ClickType type) {
        quickCraft = type == net.minecraft.world.inventory.ClickType.QUICK_CRAFT;
        this.menu = menu; parent = ACTIVE.get(); root = parent == null;
        if (!root) return;
        for (var slot : menu.slots) { originalSlots.add(slot.getItem()); add(slot.getItem()); }
        add(menu.getCarried());
        ACTIVE.set(this);
    }
    private void add(ItemStack stack) {
        if (stack.isEmpty() || origins.containsKey(stack)) return;
        Lot lot = new Lot(stack); lots.add(lot); origins.put(stack, lot);
    }
    public static void copied(ItemStack from, ItemStack to) {
        MenuDoseTransaction t = ACTIVE.get();
        if (t != null && t.origins.containsKey(from)) t.origins.put(to, t.origins.get(from));
    }
    @Override public void close() {
        if (!root) return;
        ACTIVE.remove();
        if (custom) return; // Bundles/carriers have explicit transfer adapters.
        List<Destination> destinations = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var slot : menu.slots) destination(slot.getItem(), seen, destinations);
        destination(menu.getCarried(), seen, destinations);
        // QUICK_CRAFT replaces a populated slot with a copy of carried. Reserve
        // the old slot contribution before assigning that copy's cursor origin.
        if (quickCraft) for (int i = 0; i < originalSlots.size(); i++) {
            ItemStack before = originalSlots.get(i), after = menu.slots.get(i).getItem();
            Lot old = origins.get(before);
            if (old == null || before == after || !ItemStack.isSameItemSameComponents(old.type, after)) continue;
            for (Destination d : destinations) if (d.stack == after) { d.take(old); break; }
        }
        // Existing live objects first; copied quick-craft replacements must not steal
        // the part still remaining on the cursor. Then explicit copy provenance.
        for (Destination d : destinations) {
            Lot lot = origins.get(d.stack);
            if (lot != null && lot.identity == d.stack) d.take(lot);
        }
        for (Destination d : destinations) {
            Lot lot = origins.get(d.stack);
            if (lot != null && d.need > 0) d.take(lot);
        }
        for (Destination d : destinations) {
            for (Lot lot : lots) {
                if (d.need == 0) break;
                if (lot.remaining > 0 && ItemStack.isSameItemSameComponents(lot.type, d.stack)) d.take(lot);
            }
            // Unmatched output is new material, not proof that a removed ingredient
            // was transferred to it. Recipe conversion needs an explicit policy.
            if (d.assigned > 0) ExternalRadiation.write(d.stack,
                    d.mass / d.stack.getCount(), d.tick);
        }
    }
    private static void destination(ItemStack stack, Set<ItemStack> seen, List<Destination> out) {
        if (!stack.isEmpty() && seen.add(stack)) out.add(new Destination(stack));
    }
}
