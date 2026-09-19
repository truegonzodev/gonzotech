package com.gonzotech.mixin;

import com.gonzotech.swag.FatPetLogic;
import com.gonzotech.swag.PetBowlGoal;
import com.gonzotech.swag.PetFatness;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.animal.Cat;
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

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void gonzo$registerFatness(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(GONZO$FATNESS, 1.0F);
    }

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void gonzo$bowlGoal(CallbackInfo ci) {
        // goalSelector живёт в Mob — берём через MobGoalAccessorMixin
        // (@Shadow поля родителя из миксина подкласса не работает).
        ((MobGoalAccessorMixin) (Object) this).gonzotech$getGoalSelector()
            .addGoal(7, new PetBowlGoal((Cat) (Object) this, 0.8D));
    }

    // ─────────────────── кормёжка из рук ───────────────────
    // Автор 19.09: «нельзя кормить с рук больше 1 раза — оно считает это не как
    // кормёжку, а как разведение». Ваниль: здоровому приручённому коту еда
    // уходит в love_mode. Захватываем еду ДО ванили: пока торс ниже ×2.3 —
    // еда = кормление (+10% торса, лечение сохранено). На потолке остаётся
    // ванильное разведение. Котят не трогаем: их еда ускоряет рост (ваниль).

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void gonzo$handFeed(Player player, InteractionHand hand,
        CallbackInfoReturnable<InteractionResult> ci) {
        Cat self = (Cat) (Object) this;
        ItemStack stack = player.getItemInHand(hand);
        if (!self.isTame() || self.isBaby() || !self.isFood(stack)
            || this.gonzotech$fatness() >= FatPetLogic.MAX_FATNESS) {
            return;
        }
        if (!self.level().isClientSide()) {
            stack.consume(1, player); // как ванильный usePlayerItem (в креативе не тратит)
            if (self.getHealth() < self.getMaxHealth()) {
                FoodProperties food = stack.get(DataComponents.FOOD);
                self.heal(food != null ? (float) food.nutrition() : 1.0F);
            }
            self.playSound(SoundEvents.CAT_EAT, 1.0F, 1.0F);
            FatPetLogic.onEat(this, self.level().getGameTime());
        }
        ci.setReturnValue(InteractionResult.SUCCESS);
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
