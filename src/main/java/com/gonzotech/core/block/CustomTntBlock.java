package com.gonzotech.core.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Shared TNT-style ignition hooks for Gonzo Tech explosives. */
public abstract class CustomTntBlock extends TntBlock {

    protected CustomTntBlock(Properties properties) {
        super(properties);
    }

    /** Performs this block's specific detonation behavior on the logical server. */
    protected abstract void ignite(Level level, BlockPos pos, @Nullable LivingEntity igniter);

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (!level.isClientSide && !oldState.is(state.getBlock()) && level.hasNeighborSignal(pos)) {
            igniteIfPresent(state, level, pos, null);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   @Nullable Orientation orientation, boolean isMoving) {
        if (!level.isClientSide && level.hasNeighborSignal(pos)) {
            igniteIfPresent(state, level, pos, null);
        }
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, Direction face,
                             @Nullable LivingEntity igniter) {
        igniteIfPresent(state, level, pos, igniter);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        boolean flintAndSteel = stack.is(Items.FLINT_AND_STEEL);
        boolean fireCharge = stack.is(Items.FIRE_CHARGE);
        if (!flintAndSteel && !fireCharge) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!state.is(this) || !level.getBlockState(pos).is(this)) {
            return InteractionResult.PASS;
        }

        if (!player.getAbilities().instabuild) {
            if (flintAndSteel) {
                EquipmentSlot slot = hand == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            } else {
                stack.shrink(1);
            }
        }
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
            1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        igniteIfPresent(state, level, pos, player);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!projectile.isOnFire()) {
            super.onProjectileHit(level, state, hit, projectile);
            return;
        }

        LivingEntity igniter = projectile.getOwner() instanceof LivingEntity living ? living : null;
        igniteIfPresent(state, level, hit.getBlockPos(), igniter);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.getAbilities().instabuild && state.getValue(UNSTABLE)) {
            // Let the vanilla Block implementation emit its normal destroy effects,
            // but hide UNSTABLE from TntBlock so it does not spawn vanilla-fuse TNT.
            super.playerWillDestroy(level, pos, state.setValue(UNSTABLE, false), player);
            level.gameEvent(player, GameEvent.PRIME_FUSE, pos);
            ignite(level, pos, player);
            return state;
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void wasExploded(ServerLevel level, BlockPos pos, Explosion explosion) {
        // Chain reactions are started by the explosion callback, even if the block
        // is already being removed as part of the surrounding explosion.
        level.gameEvent(null, GameEvent.PRIME_FUSE, pos);
        ignite(level, pos, null);
    }

    private void igniteIfPresent(BlockState state, Level level, BlockPos pos,
                                 @Nullable LivingEntity igniter) {
        if (level.isClientSide || !state.is(this) || !level.getBlockState(pos).is(this)) {
            return;
        }
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
        ignite(level, pos, igniter);
    }
}
