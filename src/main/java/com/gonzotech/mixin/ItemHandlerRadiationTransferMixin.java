package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ItemStackHandler.class)
public abstract class ItemHandlerRadiationTransferMixin {
    @Redirect(method = "insertItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;grow(I)V"))
    private void gonzo$merge(ItemStack destination, int amount, int slot, ItemStack source, boolean simulate) {
        ExternalRadiation.merge(destination, source, amount);
        destination.grow(amount);
    }
}
