package com.gonzotech.core.block;

import com.gonzotech.core.registry.ModEntities;
import com.gonzotech.core.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;


/** Persistent primed entity for the forward-only industrial tunnel blast. */
public final class TunnelPrimedTnt extends PrimedTnt {

    private static final String TUNNEL_FACING_TAG = "tunnel_facing";
    private static final String EXPLOSION_POWER_TAG = "explosion_power";
    private static final double BLAST_SEGMENT_LENGTH = 7.0D;
    private static final double[] EXTRA_EMITTER_DISTANCES = {2.0D, 6.0D, 14.0D, 18.0D};

    private Direction facing = Direction.NORTH;
    @Nullable
    private LivingEntity tunnelOwner;

    public TunnelPrimedTnt(EntityType<? extends TunnelPrimedTnt> entityType, Level level) {
        super(entityType, level);
    }

    public TunnelPrimedTnt(Level level, double x, double y, double z,
                            @Nullable LivingEntity igniter, Direction facing) {
        this(ModEntities.TUNNEL_TNT.get(), level);
        this.facing = facing;
        this.tunnelOwner = igniter;
        setPos(x, y, z);
        double angle = level.random.nextDouble() * (Math.PI * 2.0D);
        setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        this.xo = x;
        this.yo = y;
        this.zo = z;

        // Persist the normal industrial strength in PrimedTnt's own data field.
        CompoundTag explosionData = new CompoundTag();
        explosionData.putFloat(EXPLOSION_POWER_TAG, IndustrialTntBlock.EXPLOSION_STRENGTH);
        readAdditionalSaveData(explosionData);
        setFuse(IndustrialTntBlock.FUSE_TICKS);
        this.facing = facing;
    }

    @Override
    public void tick() {
        if (getFuse() > 1) {
            super.tick();
            return;
        }

        // Match IndustrialPrimedTnt's final movement/fuse step, then detonate once.
        setFuse(2);
        super.tick();
        if (level() instanceof ServerLevel serverLevel) {
            detonate(serverLevel);
        }
        discard();
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        return tunnelOwner;
    }

    public Direction getTunnelFacing() {
        return facing;
    }

    public Vec3 getBlastOrigin() {
        return new Vec3(getX(), getY(0.0625D), getZ());
    }

    /** Emit a handful of the existing industrial blast particles along the forward axis. */
    public void emitDirectionalExplosionParticles(ServerLevel level) {
        Vec3 origin = getBlastOrigin();
        Vec3 direction = directionVector(facing);
        for (double distance : EXTRA_EMITTER_DISTANCES) {
            Vec3 point = origin.add(direction.scale(distance));
            level.sendParticles(ModParticles.INDUSTRIAL_TNT_EXPLOSION_EMITTER.get(),
                point.x, point.y, point.z, 0, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString(TUNNEL_FACING_TAG, facing.getName());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TUNNEL_FACING_TAG)) {
            Direction loadedFacing = Direction.byName(tag.getString(TUNNEL_FACING_TAG));
            if (loadedFacing != null) {
                facing = loadedFacing;
            }
        }
    }

    @Override
    public void restoreFrom(Entity entity) {
        super.restoreFrom(entity);
        if (entity instanceof TunnelPrimedTnt tunnelTnt) {
            tunnelOwner = tunnelTnt.tunnelOwner;
            facing = tunnelTnt.facing;
        }
    }

    private void detonate(ServerLevel serverLevel) {
        Vec3 origin = getBlastOrigin();
        Vec3 direction = directionVector(facing);
        emitDirectionalExplosionParticles(serverLevel);
        DamageSource damageSource = Explosion.getDefaultDamageSource(serverLevel, this);

        // Industrial-strength blast segments tile the full tunnel without increasing
        // the power or repeatedly damaging an entity/block at the segment boundaries.
        for (double segmentStart = 0.0D; segmentStart < TunnelTntBlock.BLAST_LENGTH;
             segmentStart += BLAST_SEGMENT_LENGTH) {
            double segmentEnd = Math.min(segmentStart + BLAST_SEGMENT_LENGTH,
                TunnelTntBlock.BLAST_LENGTH);
            double centerDistance = (segmentStart + segmentEnd) / 2.0D;
            Vec3 center = origin.add(direction.scale(centerDistance));
            ExplosionDamageCalculator calculator = new TunnelExplosionDamageCalculator(
                origin, facing, segmentStart, segmentEnd);
            serverLevel.explode(this, damageSource, calculator,
                center.x, center.y, center.z, IndustrialTntBlock.EXPLOSION_STRENGTH, false,
                Level.ExplosionInteraction.TNT, ParticleTypes.EXPLOSION,
                ModParticles.INDUSTRIAL_TNT_EXPLOSION_EMITTER.get(), SoundEvents.GENERIC_EXPLODE);
        }
    }

    private static Vec3 directionVector(Direction direction) {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    private static final class TunnelExplosionDamageCalculator extends ExplosionDamageCalculator {
        private final Vec3 origin;
        private final Direction facing;
        private final double segmentStart;
        private final double segmentEnd;

        private TunnelExplosionDamageCalculator(Vec3 origin, Direction facing,
                                                double segmentStart, double segmentEnd) {
            this.origin = origin;
            this.facing = facing;
            this.segmentStart = segmentStart;
            this.segmentEnd = segmentEnd;
        }

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos,
                                          BlockState state, float remainingPower) {
            return isInsideTunnel(new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D))
                && super.shouldBlockExplode(explosion, level, pos, state, remainingPower);
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return !(entity instanceof ItemEntity)
                && isInsideTunnel(entity.getBoundingBox().getCenter())
                && super.shouldDamageEntity(explosion, entity);
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return !(entity instanceof ItemEntity)
                && isInsideTunnel(entity.getBoundingBox().getCenter())
                ? super.getKnockbackMultiplier(entity) : 0.0F;
        }

        private boolean isInsideTunnel(Vec3 point) {
            Vec3 offset = point.subtract(origin);
            double forward = offset.x * facing.getStepX()
                + offset.y * facing.getStepY()
                + offset.z * facing.getStepZ();
            if (forward < segmentStart || forward >= segmentEnd) {
                return false;
            }

            double lateral;
            double vertical;
            switch (facing.getAxis()) {
                case X -> {
                    lateral = offset.z;
                    vertical = offset.y;
                }
                case Y -> {
                    lateral = offset.x;
                    vertical = offset.z;
                }
                case Z -> {
                    lateral = offset.x;
                    vertical = offset.y;
                }
                default -> throw new IllegalStateException("Unexpected tunnel TNT axis: " + facing.getAxis());
            }
            return Math.abs(lateral) <= TunnelTntBlock.BLAST_HALF_WIDTH
                && Math.abs(vertical) <= TunnelTntBlock.BLAST_HALF_WIDTH;
        }
    }
}
