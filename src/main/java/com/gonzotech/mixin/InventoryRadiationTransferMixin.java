package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(Inventory.class)
public abstract class InventoryRadiationTransferMixin {
    @Redirect(method = "addResource(ILnet/minecraft/world/item/ItemStack;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;grow(I)V"))
    private void gonzo$merge(ItemStack destination, int amount, int slot, ItemStack source) {
        ExternalRadiation.merge(destination, source, amount);
        destination.grow(amount);
    }
}
