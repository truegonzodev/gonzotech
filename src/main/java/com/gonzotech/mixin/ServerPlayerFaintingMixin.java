package com.gonzotech.mixin;

import com.gonzotech.core.psyche.AlcoholFainting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Preserve insomnia/scoreboard state and suppress only bed-status messages during a faint. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerFaintingMixin {
    /** Other players' real sleep can still broadcast status while this player is fainting. */
    @Inject(method = "displayClientMessage", at = @At("HEAD"), cancellable = true)
    private void gonzotech$hideBedStatusDuringFaint(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay && AlcoholFainting.isFainting((ServerPlayer)(Object)this)
                && message.getContents() instanceof TranslatableContents contents
                && (contents.getKey().equals("sleep.skipping_night")
                    || contents.getKey().equals("sleep.players_sleeping"))) {
            ci.cancel(); // Do not suppress chat, other actionbar messages, or real sleepers' status.
        }
    }

    @WrapOperation(method = "startSleeping", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetStat(Lnet/minecraft/stats/Stat;)V"))
    private void gonzotech$onlyRealSleepResetsRest(ServerPlayer self, Stat<?> stat, Operation<Void> original) {
        if (!AlcoholFainting.isFainting(self) || !stat.equals(Stats.CUSTOM.get(Stats.TIME_SINCE_REST))) {
            original.call(self, stat);
        }
    }
}
