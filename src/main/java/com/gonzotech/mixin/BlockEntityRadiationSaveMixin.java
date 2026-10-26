package com.gonzotech.mixin;

import com.gonzotech.radiation.engine.RadiationOwners;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(BlockEntity.class)
public abstract class BlockEntityRadiationSaveMixin {
    // Chunk persistence only. saveToItem/saveCustomOnly must NOT embed this data in items.
    @Inject(method = "saveWithFullMetadata", at = @At("RETURN"))
    private void gonzo$save(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        RadiationOwners.save(this, cir.getReturnValue());
    }
    @Inject(method = "loadWithComponents", at = @At("RETURN"))
    private void gonzo$load(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        RadiationOwners.load(this, tag);
    }
}
