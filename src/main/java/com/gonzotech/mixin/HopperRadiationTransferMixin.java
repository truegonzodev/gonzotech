package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(HopperBlockEntity.class)
public abstract class HopperRadiationTransferMixin {
    @Redirect(method = "tryMoveInItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;grow(I)V"))
    private static void gonzo$merge(ItemStack destination, int amount, Container from,
                                    Container to, ItemStack source, int slot, Direction direction) {
        ExternalRadiation.merge(destination, source, amount);
        destination.grow(amount);
    }
}
