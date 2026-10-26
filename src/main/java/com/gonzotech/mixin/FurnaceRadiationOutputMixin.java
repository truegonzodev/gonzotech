package com.gonzotech.mixin;
import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceRadiationOutputMixin {
 @Redirect(method="burn",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/ItemStack;grow(I)V"))
 private static void gonzo$output(ItemStack dest,int amount) {
  ExternalRadiation.dilute(dest,amount);dest.grow(amount);
 }
}
