package com.gonzotech.core.block;

import com.gonzotech.core.event.ExplosiveEffects;
import com.gonzotech.core.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Vanilla primed TNT entity carrying the gunpowder block's custom blast strength. */
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

    @Override
    public void tick() {
        if (getFuse() > 1) {
            super.tick();
            return;
        }

        // PrimedTnt.explode() is private in 1.21.4. Let the vanilla tick perform
        // its final movement/fuse update without reaching that method, then detonate
        // once through the explicit damage-source overload below.
        setFuse(2);
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            detonate(serverLevel);
        }
        discard();
    }

    private void detonate(ServerLevel serverLevel) {
        Vec3 center = new Vec3(getX(), getY(0.0625D), getZ());
        ExplosiveEffects.scheduleGunpowderSmoke(serverLevel, center, GunpowderBlock.EXPLOSION_STRENGTH);
        serverLevel.explode(this, Explosion.getDefaultDamageSource(serverLevel, this), null,
            center.x, center.y, center.z, GunpowderBlock.EXPLOSION_STRENGTH, false,
            Level.ExplosionInteraction.BLOCK, ParticleTypes.EXPLOSION,
            ModParticles.GUNPOWDER_EXPLOSION_EMITTER.get(), SoundEvents.GENERIC_EXPLODE);
    }
}
