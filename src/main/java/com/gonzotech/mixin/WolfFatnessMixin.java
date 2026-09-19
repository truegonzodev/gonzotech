package com.gonzotech.mixin;

import com.gonzotech.swag.FatPetLogic;
import com.gonzotech.swag.PetBowlGoal;
import com.gonzotech.swag.PetFatness;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Wolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Жирные волки — двойник {@link CatFatnessMixin}: та же синхронизированная
 * жирность, сдувание и цель к миске (приоритет 9: ниже Follow-владельца (6) и
 * прогулки (8); BegGoal тоже 9, но он LOOK-флага — конфликта по флагам нет).
 */
@Mixin(Wolf.class)
public abstract class WolfFatnessMixin implements PetFatness {

    @Unique
    private static final EntityDataAccessor<Float> GONZO$FATNESS =
        SynchedEntityData.defineId(Wolf.class, EntityDataSerializers.FLOAT);

    @Unique private long gonzo$lastEatAt;
    @Unique private long gonzo$lastEmptyVisitAt;
    @Unique private int gonzo$deflateCounter;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void gonzo$registerFatness(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(GONZO$FATNESS, 1.0F);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void gonzo$bowlGoal(CallbackInfo ci) {
        // goalSelector живёт в Mob — через MobGoalAccessorMixin (см. CatFatnessMixin).
        ((MobGoalAccessorMixin) (Object) this).gonzotech$getGoalSelector()
            .addGoal(9, new PetBowlGoal((Wolf) (Object) this, 0.9D));
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void gonzo$fatTick(CallbackInfo ci) {
        FatPetLogic.serverTick((Wolf) (Object) this, this);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void gonzo$saveFatness(CompoundTag tag, CallbackInfo ci) {
        tag.putFloat("GonzoFatness", this.gonzotech$fatness());
        tag.putLong("GonzoLastEatAt", this.gonzo$lastEatAt);
        tag.putLong("GonzoLastBowlMissAt", this.gonzo$lastEmptyVisitAt);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void gonzo$loadFatness(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("GonzoFatness")) {
            this.gonzotech$setFatness(tag.getFloat("GonzoFatness"));
        }
        this.gonzo$lastEatAt = tag.getLong("GonzoLastEatAt");
        this.gonzo$lastEmptyVisitAt = tag.getLong("GonzoLastBowlMissAt");
    }

    @Override
    public float gonzotech$fatness() {
        return ((Wolf) (Object) this).getEntityData().get(GONZO$FATNESS);
    }

    @Override
    public void gonzotech$setFatness(float value) {
        ((Wolf) (Object) this).getEntityData().set(GONZO$FATNESS, Mth.clamp(value, 1.0F, FatPetLogic.MAX_FATNESS));
    }

    @Override
    public long gonzotech$lastEatAt() {
        return this.gonzo$lastEatAt;
    }

    @Override
    public void gonzotech$setLastEatAt(long gameTime) {
        this.gonzo$lastEatAt = gameTime;
    }

    @Override
    public long gonzotech$lastEmptyVisitAt() {
        return this.gonzo$lastEmptyVisitAt;
    }

    @Override
    public void gonzotech$setLastEmptyVisitAt(long gameTime) {
        this.gonzo$lastEmptyVisitAt = gameTime;
    }

    @Override
    public int gonzotech$deflateCounter() {
        return this.gonzo$deflateCounter;
    }

    @Override
    public void gonzotech$setDeflateCounter(int value) {
        this.gonzo$deflateCounter = value;
    }
}
