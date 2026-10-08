package com.gonzotech.mixin.client;

import com.gonzotech.music.client.GonzoMusicClient;
import net.minecraft.client.sounds.MusicInfo;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adds the custom track to the ordinary Overworld pool at vanilla start attempts. */
@Mixin(MusicManager.class)
public abstract class MusicManagerStartPlayingMixin {

    @ModifyVariable(
            method = "startPlaying(Lnet/minecraft/client/sounds/MusicInfo;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private MusicInfo gonzotech$addBitterToOverworld(MusicInfo vanillaMusic) {
        return GonzoMusicClient.maybeAddOverworldBitter(vanillaMusic);
    }
}
