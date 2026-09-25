package com.gonzotech.mixin;

import com.gonzotech.radiation.RadiationStackContext;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Makes only a player container click eligible for radiation-aware stacking. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuRadiationMixin {
    @Shadow @Final protected NonNullList<Slot> slots;
    @Shadow public abstract ItemStack getCarried();

    @Inject(method = "doClick", at = @At("HEAD"))
    private void gonzotech$beginRadiationClick(int slotId, int button, ClickType clickType,
                                                 Player player, CallbackInfo ci) {
        RadiationStackContext.begin();
        RadiationClickMerge.begin(slots, getCarried());
    }

    @Inject(method = "doClick", at = @At("RETURN"))
    private void gonzotech$endRadiationClick(int slotId, int button, ClickType clickType,
                                               Player player, CallbackInfo ci) {
        RadiationClickMerge.end(slots, getCarried());
        RadiationStackContext.end();
    }
}
