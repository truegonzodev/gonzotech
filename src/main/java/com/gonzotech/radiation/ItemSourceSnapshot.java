package com.gonzotech.radiation;

import net.minecraft.world.item.ItemStack;
import java.util.IdentityHashMap;
import java.util.List;

/** Immutable source selection for one inventory step. Counts affect total dose,
 * not the strength of an individual item inducing another item. */
public final class ItemSourceSnapshot {
    public record Source(double full, double transfer) {
        private static final Source NONE = new Source(0, 0);
    }
    private final IdentityHashMap<ItemStack, Source> owners = new IdentityHashMap<>();
    private ItemStack firstOwner;
    private Source first = Source.NONE;
    private Source second = Source.NONE;

    public ItemSourceSnapshot(List<? extends List<ItemStack>> compartments) {
        for (List<ItemStack> part : compartments) for (ItemStack stack : part) {
            if (stack.isEmpty() || owners.containsKey(stack)) continue;
            Source source = strongestItem(stack, 1.0, 0);
            owners.put(stack, source);
            if (stronger(source, first)) {
                second = first;
                first = source;
                firstOwner = stack;
            } else if (stronger(source, second)) {
                second = source;
            }
        }
    }

    /** Excludes this owner's entire tree, including its carried/nested items.
     * Equal-strength items in OTHER owners remain valid sources. */
    public double otherTransfer(ItemStack owner) {
        return (owner == firstOwner ? second : first).transfer();
    }

    private static boolean stronger(Source a, Source b) {
        return a.full() > b.full() || (a.full() == b.full() && a.transfer() > b.transfer());
    }

    private static Source strongestItem(ItemStack stack, double shielding, int depth) {
        if (stack.isEmpty()) return Source.NONE;
        double preset = stack.getItem() instanceof CarrierItem ? 0 : RadSources.emissionPerItem(stack);
        double induced = ItemRadioactivity.getInduced(stack);
        Source best = new Source((preset + induced) * shielding,
                (preset + induced * ItemRadioactivity.INDUCED_REEMIT) * shielding);
        if (depth >= 4) return best;
        if (stack.getItem() instanceof CarrierItem carrier) {
            Source cargo = strongestItem(carrier.previewCarried(stack),
                    shielding * CarrierItem.RADIOACTIVITY_FACTOR, depth + 1);
            if (stronger(cargo, best)) best = cargo;
        }
        for (ItemStack nested : RadSources.contentsOf(stack)) {
            Source source = strongestItem(nested, shielding, depth + 1);
            if (stronger(source, best)) best = source;
        }
        return best;
    }
}
