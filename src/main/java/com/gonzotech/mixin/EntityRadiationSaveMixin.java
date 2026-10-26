package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.RadiationOwners;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Entity.class)
public abstract class EntityRadiationSaveMixin {
    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void gonzo$save(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        RadiationOwners.save(this, cir.getReturnValue());
    }
    @Inject(method = "load", at = @At("RETURN"))
    private void gonzo$load(CompoundTag tag, CallbackInfo ci) { RadiationOwners.load(this, tag); }
}
