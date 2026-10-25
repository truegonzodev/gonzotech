package com.gonzotech.mixin;

import com.gonzotech.core.config.GonzoServerConfig;
import com.gonzotech.radiation.DropTraceFormat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.3.291 (пункт 5 таблицы автора): слияние дропов. В 1.21.4 события merge
 * нет, поэтому — ванильный {@code ItemEntity#merge(ItemEntity,ItemStack,ItemEntity,ItemStack)}
 * (private static, вызывается из tryToMerge). Момент, когда «минус» источника
 * ещё не закоммичен: если после этой строки не видно ENT− проигравшей сущности
 * с полной передачей, а у победителя рост больше суммы — это окно дюпа.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMergeDebugMixin {
    @Unique
    private static final Logger GONZOTECH$LOG =
        org.slf4j.LoggerFactory.getLogger("gonzotech/radiation-inventory");

    @Inject(method = "merge(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private static void gonzotech$traceMerge(ItemEntity target, ItemStack targetStack,
                                             ItemEntity source, ItemStack sourceStack, CallbackInfo callback) {
        if (!GonzoServerConfig.RADIATION_INVENTORY_DEBUG.get()) return;
        GONZOTECH$LOG.info("[RAD-INV][ENT-MERGE] tgt#{} {} ← src#{} {} | сумма была {} шт, compat={}",
            target.getId(), DropTraceFormat.fmt(targetStack), source.getId(),
            DropTraceFormat.fmt(sourceStack),
            targetStack.getCount() + sourceStack.getCount(),
            DropTraceFormat.compat(targetStack, sourceStack));
    }
}
