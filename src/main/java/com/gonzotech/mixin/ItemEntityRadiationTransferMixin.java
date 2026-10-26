package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemEntity.class)
public abstract class ItemEntityRadiationTransferMixin {
    @WrapMethod(method = "merge(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;I)Lnet/minecraft/world/item/ItemStack;")
    private static ItemStack gonzo$merge(ItemStack destination, ItemStack source, int limit, Operation<ItemStack> original) {
        int oldCount = destination.getCount();
        var a = ExternalRadiation.read(destination); var b = ExternalRadiation.read(source);
        ItemStack result = original.call(destination, source, limit);
        int added = result.getCount() - oldCount;
        ExternalRadiation.write(result, DoseMath.merge(a.rate(), oldCount, b.rate(), added), Math.max(a.tick(), b.tick()));
        return result;
    }
}
