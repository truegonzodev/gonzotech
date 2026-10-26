package com.gonzotech.mixin;
import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(InvWrapper.class)
public abstract class InvWrapperRadiationMixin {
 @Redirect(method="insertItem",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;grow(I)V"))
 private void gonzo$merge(ItemStack dest,int amount,int slot,ItemStack source,boolean simulate,@Local(ordinal=1) ItemStack existing) {
  ExternalRadiation.merge(dest,existing,amount);dest.grow(amount);
 }
}
