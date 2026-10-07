package com.gonzotech.core.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Vanilla TNT entity carrying the gunpowder block's custom blast strength. */
public final class GunpowderPrimedTnt extends PrimedTnt {

    private static final String EXPLOSION_POWER_TAG = "explosion_power";

    public GunpowderPrimedTnt(Level level, double x, double y, double z,
                              @Nullable LivingEntity igniter) {
        super(level, x, y, z, igniter);

        CompoundTag explosionData = new CompoundTag();
        explosionData.putFloat(EXPLOSION_POWER_TAG, GunpowderBlock.EXPLOSION_STRENGTH);
        readAdditionalSaveData(explosionData);
        setFuse(GunpowderBlock.FUSE_TICKS);
    }
}
