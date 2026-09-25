package com.gonzotech.mixin;

import com.gonzotech.radiation.RadiationStackContext;
import com.gonzotech.radiation.ItemRadioactivity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows an explicit container click to stack equal items whose only
 * difference is gonzo_rad. Pickup/automation remain strict because the
 * context is enabled only around AbstractContainerMenu.doClick. */
@Mixin(ItemStack.class)
public abstract class ItemStackRadiationMixin {
    @Inject(method = "isSameItemSameComponents", at = @At("HEAD"), cancellable = true)
    private static void gonzotech$ignoreRadForExplicitClick(ItemStack first, ItemStack second,
                                                              CallbackInfoReturnable<Boolean> cir) {
        if (!RadiationStackContext.explicit() || RadiationStackContext.bypass()) return;
        if (!ItemStack.isSameItem(first, second)) return;
        ItemStack a = first.copy();
        ItemStack b = second.copy();
        ItemRadioactivity.removeRadiationForComparison(a);
        ItemRadioactivity.removeRadiationForComparison(b);
        final boolean[] same = {false};
        RadiationStackContext.withBypass(() -> same[0] = ItemStack.matches(a, b));
        if (same[0]) cir.setReturnValue(true);
    }
}
