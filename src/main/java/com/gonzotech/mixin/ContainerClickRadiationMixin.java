package com.gonzotech.mixin;

import com.gonzotech.radiation.RadiationClickWatch;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (0.3.271, автор 23.10) Помечает клик по контейнеру до исполнения меню-логики,
 * чтобы радиационный пересчёт не переписывал стаки в окне drag&drop (дюп через
 * carried). Никаких изменений самих кликов.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ContainerClickRadiationMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleContainerClick",
            at = @At("HEAD"))
    private void gonzotech$markContainerClick(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        RadiationClickWatch.markClick(this.player);
    }
}
