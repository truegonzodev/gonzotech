package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.ExternalRadiation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** SidedInvWrapper overrides insertItem, so the ordinary InvWrapper hook cannot cover it. */
@Mixin(SidedInvWrapper.class)
public abstract class SidedInvWrapperRadiationMixin {
    // Both full and partial acceptance copy the accepted offer, then grow it by
    // the OLD slot count. Merge that old slot's dose BEFORE growing the copy.
    // These sites are commit-only: simulate/rejection never modifies owner dose.
    @Redirect(method = "insertItem", require = 2, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;grow(I)V"))
    private void gonzo$merge(ItemStack destination, int amount, int slot, ItemStack source, boolean simulate,
                             @Local(ordinal = 1) ItemStack existing) {
        ExternalRadiation.merge(destination, existing, amount);
        destination.grow(amount);
    }
}
