package com.gonzotech.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** A compact gunpowder block that detonates immediately when ignited. */
public final class GunpowderBlock extends CustomTntBlock {

    public static final float EXPLOSION_STRENGTH = 6.0F;

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
        // BLOCK gives an immediate block-breaking blast; fire is explicitly disabled.
        serverLevel.explode(igniter, center.x, center.y, center.z,
            EXPLOSION_STRENGTH, false, Level.ExplosionInteraction.BLOCK);
    }
}
