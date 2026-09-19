package com.gonzotech.mixin;

import com.gonzotech.swag.FatPetLogic;
import com.gonzotech.swag.PetBowlGoal;
import com.gonzotech.swag.PetFatness;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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

    // ─────────────────── кормёжка из рук ───────────────────
    // Двойник CatFatnessMixin#gonzo$handFeed (автор 19.09: еда ≠ разведение):
    // здоровый приручённый волк до потолка ×2.3 хавает в +10% торса; лечение
    // ванильное сохранено (2 × nutrition). На потолке — ванильный love_mode.

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void gonzo$handFeed(Player player, InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> ci) {
        Wolf self = (Wolf) (Object) this;
        ItemStack stack = player.getItemInHand(hand);
        if (!self.isTame() || self.isBaby() || !self.isFood(stack)
            || this.gonzotech$fatness() >= FatPetLogic.MAX_FATNESS) {
            return;
        }
        if (!self.level().isClientSide()) {
            stack.consume(1, player);
            if (self.getHealth() < self.getMaxHealth()) {
                FoodProperties food = stack.get(DataComponents.FOOD);
                float nutrition = food != null ? (float) food.nutrition() : 1.0F;
                self.heal(2.0F * nutrition);
            }
            // GENERIC_EAT в 1.21.4 — Holder.Reference<SoundEvent>, нужен .value()
            self.playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
            FatPetLogic.onEat(this, self.level().getGameTime());
        }
        ci.setReturnValue(InteractionResult.SUCCESS);
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
