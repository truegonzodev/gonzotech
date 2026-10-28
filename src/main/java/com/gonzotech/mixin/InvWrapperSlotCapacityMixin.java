package com.gonzotech.mixin;

import com.gonzotech.machines.inventory.SlotCapacity;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InvWrapper.class)
public abstract class InvWrapperSlotCapacityMixin {
    @Inject(method = "getSlotLimit", at = @At("RETURN"), cancellable = true)
    private void gonzo$slotLimit(int slot, CallbackInfoReturnable<Integer> cir) {
        InvWrapper wrapper = (InvWrapper) (Object) this;
        if (wrapper.getInv() instanceof SlotCapacity) {
            cir.setReturnValue(Math.min(cir.getReturnValue(), SlotCapacity.limit(wrapper.getInv(), slot)));
        }
    }
}
