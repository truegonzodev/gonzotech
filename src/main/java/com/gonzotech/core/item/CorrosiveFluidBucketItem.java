package com.gonzotech.core.item;

import com.gonzotech.core.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/**
 * Ведро с едкой жидкостью (серная кислота), которое можно вылить в мир как полноценную жидкость,
 * но через 180 тиков (9 секунд) оно разъедается в инвентаре, станке или на земле в «дырявое ведро».
 */
public class CorrosiveFluidBucketItem extends BucketItem {

    public CorrosiveFluidBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid.get(), properties.stacksTo(1).craftRemainder(Items.BUCKET));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        long gameTime = level.getGameTime();
        if (CorrosiveBucketItem.isExpired(stack, gameTime)) {
            ItemStack leaky = new ItemStack(ModItems.LEAKY_BUCKET.get(), stack.getCount());
            if (entity instanceof Player player) {
                player.getInventory().setItem(slotId, leaky);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
        }
    }
}
