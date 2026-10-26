package com.gonzotech.mixin;
import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Slot.class)
public abstract class SlotRadiationTransferMixin {
 @Redirect(method="safeInsert(Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;", at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;grow(I)V"))
 private void gonzo$merge(ItemStack dest,int amount,ItemStack source,int increment) {
  ExternalRadiation.merge(dest,source,amount); dest.grow(amount);
 }
}
