package com.gonzotech.mixin;

import com.gonzotech.core.psyche.FaintingPlayer;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the native bed cascade, but put the fainting body on the floor rather than 0.6875 blocks above it. */
@Mixin(LivingEntity.class)
public abstract class LivingFaintPoseMixin {
    @Unique
    private boolean gonzotech$placeFaintOnFloor(BlockPos pos) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (!(self instanceof FaintingPlayer faint) || !faint.gonzotech$isFainting()) return false;
        self.setPos(pos.getX() + 0.5, pos.getY() + faint.gonzotech$faintFloorOffset(), pos.getZ() + 0.5);
        return true;
    }

    @Inject(method = "setPosToBed", at = @At("HEAD"), cancellable = true)
    private void gonzotech$floorAnchor(BlockPos pos, CallbackInfo ci) {
        if (gonzotech$placeFaintOnFloor(pos)) ci.cancel();
    }

    @Inject(method = "onSyncedDataUpdated", at = @At("RETURN"))
    private void gonzotech$alignAfterSync(EntityDataAccessor<?> key, CallbackInfo ci) {
        // SleepingPos has a lower data ID than the Player flags. Re-align after later
        // flag/offset delivery as well, including players newly entering tracking range.
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.getPose() == Pose.SLEEPING && self.isSleeping()
                && self instanceof FaintingPlayer faint && faint.gonzotech$isFaintData(key)) {
            self.getSleepingPos().ifPresent(this::gonzotech$placeFaintOnFloor);
        }
    }

    @WrapOperation(method = "hurtServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;stopSleeping()V"))
    private void gonzotech$damageDoesNotCancelFaint(LivingEntity self, Operation<Void> original) {
        // Vodka's own poison would otherwise wake the player on its first damage tick.
        // Do not cancel damage; only skip automatic wakeup. Death still ends the faint.
        if (!(self instanceof FaintingPlayer faint) || !faint.gonzotech$isFainting()) original.call(self);
    }

    @Inject(method = "getBedOrientation", at = @At("HEAD"), cancellable = true)
    private void gonzotech$horizontalFloorPose(CallbackInfoReturnable<Direction> cir) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self instanceof FaintingPlayer faint && faint.gonzotech$isFainting()) cir.setReturnValue(self.getDirection());
    }
}
