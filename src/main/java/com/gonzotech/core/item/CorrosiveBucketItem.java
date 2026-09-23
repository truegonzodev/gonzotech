package com.gonzotech.core.item;

import com.gonzotech.core.registry.ModItems;
import com.gonzotech.core.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Ведро с едким летучим химикатом (этилен).
 * Обычное ведро разъедается за 180 тиков (9 секунд) в любом месте:
 * в инвентаре, в станке или на земле, превращаясь в «дырявое ведро».
 * При разъедании или при попытке вылить ПКМ по земле взрывается (взрыв этилена).
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

    public static void triggerEthyleneExplosion(Level level, double x, double y, double z, @Nullable Entity source) {
        if (level.isClientSide) return;
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ModParticles.ETHYLEN_EXPLOSION_EMITTER.get(), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        level.explode(source, x, y, z, 3.5F, Level.ExplosionInteraction.BLOCK);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos target = pos.relative(face);

        if (!level.isClientSide) {
            triggerEthyleneExplosion(level, target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5, player);
            if (player != null && !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
                ItemStack leaky = new ItemStack(ModItems.LEAKY_BUCKET.get());
                if (!player.getInventory().add(leaky)) {
                    player.drop(leaky, false);
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            triggerEthyleneExplosion(level, player.getX(), player.getY() + 1.0, player.getZ(), player);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                ItemStack leaky = new ItemStack(ModItems.LEAKY_BUCKET.get());
                if (!player.getInventory().add(leaky)) {
                    player.drop(leaky, false);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide) return;
        long gameTime = level.getGameTime();
        if (isExpired(stack, gameTime)) {
            ItemStack leaky = new ItemStack(ModItems.LEAKY_BUCKET.get(), stack.getCount());
            if (entity instanceof Player player) {
                player.getInventory().setItem(slotId, leaky);
                triggerEthyleneExplosion(level, player.getX(), player.getY(), player.getZ(), player);
            }
        }
    }
}
