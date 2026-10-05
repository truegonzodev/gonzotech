package com.gonzotech.mixin.client;

import net.minecraft.advancements.AdvancementNode;
import net.minecraft.client.gui.screens.advancements.AdvancementTab;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives GonzoTech's advancement tab its own name without changing the root achievement title. */
@Mixin(AdvancementTab.class)
public abstract class AdvancementTabMixin {
    @Shadow
    public abstract AdvancementNode getRootNode();

    @Inject(method = "getTitle", at = @At("HEAD"), cancellable = true)
    private void gonzotech$renameEnergyTab(CallbackInfoReturnable<Component> cir) {
        if ("gonzotech".equals(this.getRootNode().holder().id().getNamespace())
                && "root".equals(this.getRootNode().holder().id().getPath())) {
            cir.setReturnValue(Component.translatable("advancements.gonzotech.energy_tab"));
        }
    }
}
