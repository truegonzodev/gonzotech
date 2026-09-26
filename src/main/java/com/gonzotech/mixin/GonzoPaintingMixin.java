package com.gonzotech.mixin;

import com.gonzotech.core.item.GonzoPaintingItem;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only our variant returns our item; all other paintings retain vanilla behavior. */
@Mixin(Painting.class)
public abstract class GonzoPaintingMixin {
    @Inject(method = "getPickResult", at = @At("HEAD"), cancellable = true)
    private void gonzotech$pickPainting(CallbackInfoReturnable<ItemStack> callback) {
        if (((Painting) (Object) this).getVariant().is(GonzoPaintingItem.VARIANT)) {
            callback.setReturnValue(new ItemStack(ModItems.GONZO_PAINTING.get()));
        }
    }

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void gonzotech$dropPainting(ServerLevel level, Entity breaker, CallbackInfo callback) {
        Painting painting = (Painting) (Object) this;
        if (!painting.getVariant().is(GonzoPaintingItem.VARIANT)) return;
        callback.cancel();
        // Same vanilla rules: doEntityDrops, break sound, no creative duplication.
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) return;
        painting.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
        if (breaker instanceof Player player && player.getAbilities().instabuild) return;
        painting.spawnAtLocation(level, new ItemStack(ModItems.GONZO_PAINTING.get()));
    }
}
