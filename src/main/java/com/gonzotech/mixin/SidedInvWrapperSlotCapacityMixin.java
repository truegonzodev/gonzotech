package com.gonzotech.mixin;

import com.gonzotech.machines.inventory.SlotCapacity;
import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SidedInvWrapper.class)
public abstract class SidedInvWrapperSlotCapacityMixin {
    @Shadow @Final protected WorldlyContainer inv;
    @Shadow @Final protected Direction side;

    @Inject(method = "getSlotLimit", at = @At("RETURN"), cancellable = true)
    private void gonzo$slotLimit(int slot, CallbackInfoReturnable<Integer> cir) {
        if (inv instanceof SlotCapacity) {
            int inventorySlot = slot < 0 ? -1 : SidedInvWrapper.getSlot(inv, slot, side);
            cir.setReturnValue(Math.min(cir.getReturnValue(), SlotCapacity.limit(inv, inventorySlot)));
        }
    }
}
