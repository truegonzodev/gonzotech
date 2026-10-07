package com.gonzotech.core.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Vanilla primed TNT entity configured for the industrial block's longer fuse and stronger blast. */
public final class IndustrialPrimedTnt extends PrimedTnt {

    private static final String EXPLOSION_POWER_TAG = "explosion_power";

    public IndustrialPrimedTnt(Level level, double x, double y, double z,
                               @Nullable LivingEntity igniter) {
        super(level, x, y, z, igniter);

        // PrimedTnt's own NBT-supported field keeps the stronger blast intact if
        // the entity is saved or crosses a dimension before it detonates.
        CompoundTag explosionData = new CompoundTag();
        explosionData.putFloat(EXPLOSION_POWER_TAG, IndustrialTntBlock.EXPLOSION_STRENGTH);
        readAdditionalSaveData(explosionData);
        setFuse(IndustrialTntBlock.FUSE_TICKS);
    }
}
