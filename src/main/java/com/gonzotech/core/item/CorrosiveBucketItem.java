package com.gonzotech.core.item;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * Ведро с едким химикатом (серная кислота, этилен).
 * Обычное ведро разъедается за 180 тиков (9 секунд) в любом месте:
 * в инвентаре, в станке или на земле, превращаясь в «дырявое ведро».
 */
public class CorrosiveBucketItem extends Item {

    public static final int LIFETIME_TICKS = 180;
    private static final String TAG_LEAK_AT = "leak_at";

    public CorrosiveBucketItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static void initLeakAt(ItemStack stack, long gameTime) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data -> data.update(tag -> {
            tag.putLong(TAG_LEAK_AT, gameTime + LIFETIME_TICKS);
        }));
    }

    public static long getLeakAt(ItemStack stack, long gameTime) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains(TAG_LEAK_AT)) {
                return tag.getLong(TAG_LEAK_AT);
            }
        }
        long leakAt = gameTime + LIFETIME_TICKS;
        initLeakAt(stack, gameTime);
        return leakAt;
    }

    public static boolean isExpired(ItemStack stack, long gameTime) {
        return gameTime >= getLeakAt(stack, gameTime);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        long gameTime = level.getGameTime();
        if (isExpired(stack, gameTime)) {
            ItemStack leaky = new ItemStack(ModItems.LEAKY_BUCKET.get(), stack.getCount());
            if (entity instanceof Player player) {
                player.getInventory().setItem(slotId, leaky);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
        }
    }
}
