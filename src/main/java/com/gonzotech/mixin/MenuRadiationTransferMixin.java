package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.MenuDoseTransaction;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

@Mixin(AbstractContainerMenu.class)
public abstract class MenuRadiationTransferMixin {
    @Inject(method="tryItemClickBehaviourOverride", at=@At("RETURN"))
    private void gonzo$custom(Player p, ClickAction action, Slot slot, ItemStack clicked, ItemStack carried, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) MenuDoseTransaction.customTransfer();
    }
    @WrapMethod(method = "clicked")
    private void gonzo$transfer(int slot, int button, ClickType type, Player player, Operation<Void> original) {
        if (player.level().isClientSide()) { original.call(slot, button, type, player); return; }
        try (var transfer = new MenuDoseTransaction((AbstractContainerMenu)(Object)this, type)) {
            original.call(slot, button, type, player);
        }
    }
}
