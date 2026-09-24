package com.gonzotech.core.item;

import com.gonzotech.core.fluid.ModFluids;
import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.core.registry.ModItems;
import net.minecraft.core.BlockPos;
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
 * Ведро с едкой кислотой (серная кислота), которое можно вылить в мир как полноценную жидкость,
 * но через 180 тиков (9 секунд) оно разъедается в инвентаре, станке или на земле в «дырявое ведро».
 * В момент разъедания выливает блок серной кислоты в ближайший свободный блок воздуха (или в ноги игрока).
 */
public class CorrosiveFluidBucketItem extends BucketItem {

    public CorrosiveFluidBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid.get(), properties.stacksTo(1).craftRemainder(Items.BUCKET));
    }

    /**
     * Разливает кислоту в ближайший блок воздуха в радиусе 1.
     * Если все блоки вокруг заняты (игрок застрял), заменяет блок в центре (в ногах).
     */
    public static void spillAcidNear(Level level, BlockPos origin) {
        if (level.isClientSide) return;
        BlockPos bestAir = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = origin.offset(dx, dy, dz);
                    if (level.getBlockState(p).isAir()) {
                        double d = origin.distSqr(p);
                        if (d < bestDistSq) {
                            bestDistSq = d;
                            bestAir = p;
                        }
                    }
                }
            }
        }

        BlockPos target = (bestAir != null) ? bestAir : origin;
        level.setBlock(target, ModBlocks.SULFURIC_ACID.get().defaultBlockState(), 3);
        level.scheduleTick(target, ModFluids.SULFURIC_ACID.get(), ModFluids.SULFURIC_ACID.get().getTickDelay(level));
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
                spillAcidNear(level, player.blockPosition());
            }
        }
    }
}
