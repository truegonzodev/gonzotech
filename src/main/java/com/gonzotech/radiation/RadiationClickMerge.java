package com.gonzotech.radiation;

import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Reconciles the per-item radiation after vanilla has completed an explicit
 * click. Vanilla is allowed to move/count items; we only repair the component
 * of a destination stack that actually grew during this click. */
public final class RadiationClickMerge {
    private static final ThreadLocal<List<ItemStackSnapshot>> BEFORE = new ThreadLocal<>();
    private static final ThreadLocal<ItemStackSnapshot> BEFORE_CARRIED = new ThreadLocal<>();

    private RadiationClickMerge() {}

    public static void begin(List<Slot> slots, net.minecraft.world.item.ItemStack carried) {
        List<ItemStackSnapshot> snapshot = new ArrayList<>(slots.size());
        for (Slot slot : slots) snapshot.add(new ItemStackSnapshot(slot.getItem().copy()));
        BEFORE.set(snapshot);
        BEFORE_CARRIED.set(new ItemStackSnapshot(carried.copy()));
    }

    public static void end(List<Slot> slots, net.minecraft.world.item.ItemStack carried) {
        List<ItemStackSnapshot> before = BEFORE.get();
        try {
            if (before == null || before.size() != slots.size()) return;
            for (int targetIndex = 0; targetIndex < slots.size(); targetIndex++) {
                var target = slots.get(targetIndex).getItem();
                int oldTargetCount = before.get(targetIndex).stack().getCount();
                if (target.isEmpty() || target.getCount() <= oldTargetCount) continue;
                int moved = target.getCount() - oldTargetCount;
                double total = ItemRadioactivity.getInduced(before.get(targetIndex).stack())
                        * oldTargetCount;
                int accounted = 0;
                for (int sourceIndex = 0; sourceIndex < slots.size() && accounted < moved; sourceIndex++) {
                    if (sourceIndex == targetIndex) continue;
                    var oldSource = before.get(sourceIndex).stack();
                    var source = slots.get(sourceIndex).getItem();
                    int lost = oldSource.getCount() - source.getCount();
                    if (lost <= 0 || !ItemRadioactivity.sameExceptRadiation(oldSource, target)) continue;
                    int take = Math.min(lost, moved - accounted);
                    total += ItemRadioactivity.getInduced(oldSource) * take;
                    accounted += take;
                }
                if (accounted > 0) {
                    ItemRadioactivity.setInduced(target, total / target.getCount());
                }
            }
            ItemStackSnapshot oldCarriedSnapshot = BEFORE_CARRIED.get();
            if (oldCarriedSnapshot != null) {
                var oldCarried = oldCarriedSnapshot.stack();
                int oldCount = oldCarried.getCount();
                if (!carried.isEmpty() && carried.getCount() > oldCount) {
                    int moved = carried.getCount() - oldCount;
                    double total = ItemRadioactivity.getInduced(oldCarried) * oldCount;
                    int accounted = 0;
                    for (int i = 0; i < slots.size() && accounted < moved; i++) {
                        var oldSource = before.get(i).stack();
                        int lost = oldSource.getCount() - slots.get(i).getItem().getCount();
                        if (lost <= 0 || !ItemRadioactivity.sameExceptRadiation(oldSource, carried)) continue;
                        int take = Math.min(lost, moved - accounted);
                        total += ItemRadioactivity.getInduced(oldSource) * take;
                        accounted += take;
                    }
                    if (accounted > 0) ItemRadioactivity.setInduced(carried, total / carried.getCount());
                }
            }
        } finally {
            BEFORE.remove();
            BEFORE_CARRIED.remove();
        }
    }

    private record ItemStackSnapshot(net.minecraft.world.item.ItemStack stack) {}
}
