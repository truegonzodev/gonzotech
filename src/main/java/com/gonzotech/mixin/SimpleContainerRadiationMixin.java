package com.gonzotech.mixin;
import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(SimpleContainer.class)
public abstract class SimpleContainerRadiationMixin {
 @Redirect(method="moveItemsBetweenStacks",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;grow(I)V"))
 private void gonzo$merge(ItemStack dest,int amount,ItemStack source,ItemStack other) {
  ExternalRadiation.merge(dest,source,amount);dest.grow(amount);
 }
 @Redirect(method="removeItemType",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;grow(I)V"))
 private void gonzo$collect(ItemStack dest,int amount,Item item,int count,@Local(ordinal=2) ItemStack source) {
  ExternalRadiation.merge(dest,source,amount);dest.grow(amount);
 }
}
