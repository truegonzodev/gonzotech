package com.gonzotech.mixin;

import com.gonzotech.core.psyche.AlcoholFainting;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A faint must not reset vanilla insomnia or scoreboard objectives tied to that statistic. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerFaintingMixin {
    @WrapOperation(method = "startSleeping", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;resetStat(Lnet/minecraft/stats/Stat;)V"))
    private void gonzotech$onlyRealSleepResetsRest(ServerPlayer self, Stat<?> stat, Operation<Void> original) {
        if (!AlcoholFainting.isFainting(self) || !stat.equals(Stats.CUSTOM.get(Stats.TIME_SINCE_REST))) {
            original.call(self, stat);
        }
    }
}
