package com.gonzotech.mixin;

import com.gonzotech.swag.FatPetLogic;
import com.gonzotech.swag.PetBowlGoal;
import com.gonzotech.swag.PetFatness;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.animal.Cat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Жирные коты (автор, 2026-09-19): к коту добавляется синхронизированная
 * «жирность» (множитель торса 1.0..2.3), персист в NBT, сдувание −0.1%/с
 * и цель-ходьбы к миске (приоритет 7: ниже Follow-владельца (6) и лежаний,
 * выше прогулки (11)). Его еда из рук игрока давно ванильная — не трогаем.
 */
@Mixin(Cat.class)
public abstract class CatFatnessMixin implements PetFatness {

    @Unique
    private static final EntityDataAccessor<Float> GONZO$FATNESS =
        SynchedEntityData.defineId(Cat.class, EntityDataSerializers.FLOAT);

    @Unique private long gonzo$lastEatAt;
    @Unique private long gonzo$lastEmptyVisitAt;
    @Unique private int gonzo$deflateCounter;

    @Shadow @Final @SuppressWarnings("unused")
    protected GoalSelector goalSelector;

    // ─────────────────── данные ───────────────────

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void gonzo$registerFatness(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(GONZO$FATNESS, 1.0F);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void gonzo$bowlGoal(CallbackInfo ci) {
        this.goalSelector.addGoal(7, new PetBowlGoal((Cat) (Object) this, 0.8D));
    }

    // ─────────────────── тик: сдувание ───────────────────
    // У Cat нет своего aiStep — у него customServerAiStep(ServerLevel),
    // уже только на серверной стороне (гард на isClientSide не нужен).

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void gonzo$fatTick(ServerLevel level, CallbackInfo ci) {
        FatPetLogic.serverTick((Cat) (Object) this, this);
    }

    // ─────────────────── персист ───────────────────

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

    // ─────────────────── PetFatness ───────────────────

    @Override
    public float gonzotech$fatness() {
        return ((Cat) (Object) this).getEntityData().get(GONZO$FATNESS);
    }

    @Override
    public void gonzotech$setFatness(float value) {
        ((Cat) (Object) this).getEntityData().set(GONZO$FATNESS, Mth.clamp(value, 1.0F, FatPetLogic.MAX_FATNESS));
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
