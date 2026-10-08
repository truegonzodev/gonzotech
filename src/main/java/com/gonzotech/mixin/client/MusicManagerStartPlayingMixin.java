package com.gonzotech.mixin.client;

import com.gonzotech.music.client.GonzoMusicClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a new music track from starting over an active track or its minimum play window. */
@Mixin(MusicManager.class)
public abstract class MusicManagerStartPlayingMixin {

    @Unique
    private boolean gonzotech$recordStartPending;

    @Inject(
            method = "startPlaying(Lnet/minecraft/client/sounds/MusicInfo;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void gonzotech$preventOverlappingStart(MusicInfo requestedMusic, CallbackInfo callback) {
        gonzotech$recordStartPending = false;
        if (GonzoMusicClient.shouldSuppressMusicStart(Minecraft.getInstance())) {
            callback.cancel();
            return;
        }
        gonzotech$recordStartPending = true;
    }

    @ModifyVariable(
            method = "startPlaying(Lnet/minecraft/client/sounds/MusicInfo;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private MusicInfo gonzotech$maybeAddOverworldBitter(MusicInfo requestedMusic) {
        return GonzoMusicClient.maybeAddOverworldBitter(requestedMusic);
    }

    @Inject(
            method = "startPlaying(Lnet/minecraft/client/sounds/MusicInfo;)V",
            at = @At("RETURN"))
    private void gonzotech$recordMinimumPlayWindow(MusicInfo startedMusic, CallbackInfo callback) {
        if (gonzotech$recordStartPending) {
            gonzotech$recordStartPending = false;
            GonzoMusicClient.recordMusicStart(Minecraft.getInstance(), startedMusic);
        }
    }
}
