package com.gonzotech.core.block;

import com.gonzotech.core.event.ExplosiveEffects;
import com.gonzotech.core.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A compact gunpowder block that detonates immediately when ignited. */
public final class GunpowderBlock extends CustomTntBlock {

    public static final float EXPLOSION_STRENGTH = 5.4F;

    public GunpowderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void ignite(Level level, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (serverLevel.getBlockState(pos).is(this)) {
            serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        Vec3 center = Vec3.atCenterOf(pos);
        // Preserve the immediate, fire-free block blast while replacing only its large emitter.
        serverLevel.explode(igniter, null, null, center.x, center.y, center.z,
            EXPLOSION_STRENGTH, false, Level.ExplosionInteraction.BLOCK,
            ParticleTypes.EXPLOSION, ModParticles.GUNPOWDER_EXPLOSION_EMITTER.get(),
            SoundEvents.GENERIC_EXPLODE);
        ExplosiveEffects.scheduleGunpowderSmoke(serverLevel, center, EXPLOSION_STRENGTH);
    }
}
