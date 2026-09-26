package com.gonzotech.mixin.client;

import com.gonzotech.core.psyche.AlcoholFainting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Restyle the native wake button only during a faint; keep its callback and keyboard behaviour. */
@Mixin(InBedChatScreen.class)
public abstract class InBedFaintingScreenMixin {
    @Shadow private Button leaveBedButton;
    @Unique private int gonzotech$bedButtonY;
    @Unique private Component gonzotech$bedButtonMessage;
    @Unique private boolean gonzotech$faintPresentation;

    @Inject(method = "init", at = @At("TAIL"))
    private void gonzotech$initFaintButton(CallbackInfo ci) {
        // init also runs on resize/GUI-scale changes and creates a new vanilla button.
        gonzotech$bedButtonY = leaveBedButton.getY();
        gonzotech$bedButtonMessage = leaveBedButton.getMessage();
        gonzotech$faintPresentation = false;
        gonzotech$updateFaintButton();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void gonzotech$syncFaintButton(GuiGraphics graphics, int mouseX, int mouseY,
                                         float partialTick, CallbackInfo ci) {
        // Also handle synced faint metadata arriving after the screen was initialized.
        gonzotech$updateFaintButton();
    }

    @Unique
    private void gonzotech$updateFaintButton() {
        var player = Minecraft.getInstance().player;
        boolean faint = player != null && AlcoholFainting.isFainting(player);
        if (leaveBedButton == null || faint == gonzotech$faintPresentation) return;
        gonzotech$faintPresentation = faint;
        // Absolute baseline, never subtract every frame (which would make the button drift).
        leaveBedButton.setY(faint ? gonzotech$bedButtonY - 48 : gonzotech$bedButtonY);
        leaveBedButton.setMessage(faint ? Component.translatable("gui.gonzotech.faint.get_up")
                : gonzotech$bedButtonMessage);
    }
}
