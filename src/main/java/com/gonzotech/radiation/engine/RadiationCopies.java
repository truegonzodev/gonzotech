package com.gonzotech.radiation.engine;

import com.gonzotech.mixin.ContainerContentsConstructorInvoker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import java.util.ArrayList;

/** Vanilla may share immutable contents between stack copies. External per-item state
 * requires distinct nested stack instances. Initialize only the NEW copy with equal
 * component values; no radiation is encoded and no live inventory slot is rewritten. */
public final class RadiationCopies {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private RadiationCopies() {}

    public static void detachNested(ItemStack from, ItemStack to) {
        if (from == to || to.isEmpty()) return;
        var container = from.get(DataComponents.CONTAINER);
        var bundle = from.get(DataComponents.BUNDLE_CONTENTS);
        if (container == null && bundle == null) return;
        int depth = DEPTH.get();
        if (depth >= 4) return;
        DEPTH.set(depth + 1);
        try {
            if ((Object)container instanceof ContainerContentsAccess access) {
                var items = new ArrayList<ItemStack>();
                for (ItemStack child : access.gonzo$radiationItems()) items.add(child.copy());
                to.set(DataComponents.CONTAINER, ContainerContentsConstructorInvoker.gonzo$create(items));
            }
            if (bundle != null) {
                var items = new ArrayList<ItemStack>();
                for (ItemStack child : bundle.items()) items.add(child.copy());
                var copy = new BundleContents.Mutable(new BundleContents(items));
                if (bundle.getSelectedItem() >= 0) copy.toggleSelectedItem(bundle.getSelectedItem());
                to.set(DataComponents.BUNDLE_CONTENTS, copy.toImmutable());
            }
        } finally {
            if (depth == 0) DEPTH.remove(); else DEPTH.set(depth);
        }
    }
}
