package com.gonzotech.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes vanilla's current music instance for client-side conditional OST checks. */
@Mixin(MusicManager.class)
public interface MusicManagerAccessor {

    @Accessor("currentMusic")
    SoundInstance gonzotech$getCurrentMusic();
}
