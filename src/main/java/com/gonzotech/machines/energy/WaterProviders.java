package com.gonzotech.machines.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Какие вёдра котёл принимает как источник воды и сколько mB даёт каждое.
 * <p>
 * «Обычные» провайдеры — вода/рыхлый снег. Особые — вёдра с рыбой: помимо воды
 * они спавнят соответствующую рыбу-сущность на котле (см.
 * {@link #spawnFishIfAny}).
 */
public final class WaterProviders {

    private WaterProviders() {
    }

    /** @return сколько mB воды даёт это ведро, или 0 если предмет не является провайдером. */
    public static int millibucketsFor(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.WATER_BUCKET) return MachineDefs.WATER_PER_BUCKET;
        if (item == Items.POWDER_SNOW_BUCKET) return MachineDefs.WATER_PER_POWDER_SNOW;
        if (item == Items.COD_BUCKET
            || item == Items.SALMON_BUCKET
            || item == Items.PUFFERFISH_BUCKET
            || item == Items.TROPICAL_FISH_BUCKET) {
            return MachineDefs.WATER_PER_FISH_BUCKET;
        }
        return 0;
    }

    /** Является ли предмет допустимым провайдером воды (для валидации слота). */
    public static boolean isWaterProvider(ItemStack stack) {
        return millibucketsFor(stack) > 0;
    }

    /**
     * Если ведро было ведром с рыбой — заспавнить соответствующую рыбу над котлом.
     * Вызывать на сервере до/после осушения ведра.
     */
    public static void spawnFishIfAny(ServerLevel level, BlockPos pos, ItemStack bucket) {
        Item item = bucket.getItem();
        EntityType<?> type;
        if (item == Items.COD_BUCKET) {
            type = EntityType.COD;
        } else if (item == Items.SALMON_BUCKET) {
            type = EntityType.SALMON;
        } else if (item == Items.PUFFERFISH_BUCKET) {
            type = EntityType.PUFFERFISH;
        } else if (item == Items.TROPICAL_FISH_BUCKET) {
            type = EntityType.TROPICAL_FISH;
        } else {
            return;
        }
        Entity entity = type.create(level, null, pos.above(), EntitySpawnReason.BUCKET, true, false);
        if (entity != null) {
            level.addFreshEntity(entity);
        }
    }
}
