package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(BundleContents.Mutable.class)
public abstract class BundleRadiationTransferMixin {
    @Redirect(method = "tryInsert", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack gonzo$merge(ItemStack previous, int count, ItemStack source) {
        ItemStack result = previous.copyWithCount(count);
        var a = ExternalRadiation.read(previous); var b = ExternalRadiation.read(source);
        ExternalRadiation.write(result, DoseMath.merge(a.rate(), previous.getCount(), b.rate(), count - previous.getCount()),
                Math.max(a.tick(), b.tick()));
        return result;
    }
}
