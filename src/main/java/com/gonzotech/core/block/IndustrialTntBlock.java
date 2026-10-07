package com.gonzotech.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** TNT with a 160-tick fuse, power 14, and vanilla TNT explosion/drop behavior. */
public final class IndustrialTntBlock extends CustomTntBlock {

    public static final int FUSE_TICKS = 160;
    public static final float EXPLOSION_STRENGTH = 14.0F;

    public IndustrialTntBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void ignite(Level level, BlockPos pos, @Nullable LivingEntity igniter) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState displayState = serverLevel.getBlockState(pos);
        if (!displayState.is(this)) {
            displayState = defaultBlockState();
        }
        serverLevel.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

        IndustrialPrimedTnt primedTnt = new IndustrialPrimedTnt(
            serverLevel, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, igniter);
        primedTnt.setBlockState(displayState);
        serverLevel.addFreshEntity(primedTnt);
        serverLevel.playSound(null, primedTnt.getX(), primedTnt.getY(), primedTnt.getZ(),
            SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
