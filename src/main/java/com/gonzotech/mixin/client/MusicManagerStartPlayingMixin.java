package com.gonzotech.mixin.client;

import com.gonzotech.music.client.GonzoMusicClient;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Enforces single-track music playback and adds Bitter to eligible Overworld starts. */
@Mixin(MusicManager.class)
public abstract class MusicManagerStartPlayingMixin {

    @ModifyVariable(
            method = "startPlaying(Lnet/minecraft/client/sounds/MusicInfo;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private MusicInfo gonzotech$prepareExclusiveMusicStart(MusicInfo vanillaMusic) {
        return GonzoMusicClient.prepareMusicStart(vanillaMusic);
    }
}
