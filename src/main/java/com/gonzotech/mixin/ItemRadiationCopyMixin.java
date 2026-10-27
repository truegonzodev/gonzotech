package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.ExternalRadiation;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemRadiationCopyMixin {
    @Inject(method = "copy", at = @At("RETURN"))
    private void gonzo$copyExternalDose(CallbackInfoReturnable<ItemStack> cir) {
        ExternalRadiation.copy((ItemStack)(Object)this, cir.getReturnValue());
        com.gonzotech.radiation.engine.RadiationCopies.detachNested((ItemStack)(Object)this, cir.getReturnValue());
    }
}
