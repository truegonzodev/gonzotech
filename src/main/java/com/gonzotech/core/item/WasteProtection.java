package com.gonzotech.core.item;

import com.gonzotech.GonzoTechMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;

/** Shared waste policy: future slags need only join the tag, not duplicate handlers.
 * Damage/age protection is for dropped items, NOT for placed blocks or explicit admin removal.
 */
@EventBusSubscriber(modid = GonzoTechMod.MOD_ID)
public final class WasteProtection {
    public static final TagKey<Item> NON_DISPOSABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(GonzoTechMod.MOD_ID, "non_disposable"));

    private WasteProtection() {}

    public static boolean isProtected(ItemStack stack) {
        return stack.is(NON_DISPOSABLE);
    }

    @SubscribeEvent
    public static void onDamageCheck(EntityInvulnerabilityCheckEvent event) {
        if (event.getEntity() instanceof ItemEntity item && isProtected(item.getItem())) {
            event.setInvulnerable(true);
        }
    }

    @SubscribeEvent
    public static void onExpire(ItemExpireEvent event) {
        if (isProtected(event.getEntity().getItem())) {
            // No per-tick listener or repeated extension: age becomes the vanilla unlimited sentinel.
            event.getEntity().setUnlimitedLifetime();
        }
    }
}
