package com.gonzotech.mixin;

import com.gonzotech.core.psyche.AlcoholFainting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.SleepStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Fainting keeps the native pose, but is not a vote to skip the night. */
@Mixin(SleepStatus.class)
public abstract class SleepStatusFaintingMixin {
    @WrapOperation(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;isSleeping()Z"))
    private boolean gonzotech$countOnlyRealSleep(ServerPlayer player, Operation<Boolean> original) {
        // Leave activePlayers alone: a fainting player still counts as an awake player.
        // No counter change on faint/wake -> no skipping-night or "0 of N" announcement.
        return !AlcoholFainting.isFainting(player) && original.call(player);
    }
}
