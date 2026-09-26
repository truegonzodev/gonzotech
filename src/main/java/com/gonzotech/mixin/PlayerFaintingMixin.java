package com.gonzotech.mixin;

import com.gonzotech.core.psyche.FaintingPlayer;
import com.gonzotech.core.psyche.AlcoholFainting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native sleeping pose/UI, but never a completed night or persistent sleep state. */
@Mixin(Player.class)
public abstract class PlayerFaintingMixin implements FaintingPlayer {
    @Unique private static final EntityDataAccessor<Boolean> GONZO$FAINT =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.BOOLEAN);
    @Unique private static final EntityDataAccessor<Float> GONZO$FLOOR_OFFSET =
            SynchedEntityData.defineId(Player.class, EntityDataSerializers.FLOAT);
    @Shadow private int sleepCounter;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void gonzotech$registerFaint(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(GONZO$FAINT, false);
        builder.define(GONZO$FLOOR_OFFSET, 0.0F);
    }
    public boolean gonzotech$isFainting() { return ((Player)(Object)this).getEntityData().get(GONZO$FAINT); }
    public float gonzotech$faintFloorOffset() { return ((Player)(Object)this).getEntityData().get(GONZO$FLOOR_OFFSET); }
    public boolean gonzotech$isFaintData(EntityDataAccessor<?> key) {
        return key.equals(GONZO$FAINT) || key.equals(GONZO$FLOOR_OFFSET);
    }
    public void gonzotech$beginFaint(float floorOffset) {
        var data = ((Player)(Object)this).getEntityData();
        data.set(GONZO$FLOOR_OFFSET, floorOffset);
        data.set(GONZO$FAINT, true);
        sleepCounter = 0;
    }
    public void gonzotech$endFaint() {
        ((Player)(Object)this).getEntityData().set(GONZO$FAINT, false);
        sleepCounter = 0;
    }
    @Inject(method = "stopSleepInBed", at = @At("RETURN"))
    private void gonzotech$cancelFaintOnWake(boolean immediately, boolean updateSleepers, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer player) AlcoholFainting.afterWake(player);
    }
    @Inject(method = "isSleepingLongEnough", at = @At("HEAD"), cancellable = true)
    private void gonzotech$neverCompleteFaint(CallbackInfoReturnable<Boolean> cir) {
        if (gonzotech$isFainting()) cir.setReturnValue(false);
    }
}
