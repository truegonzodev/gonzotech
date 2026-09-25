package com.gonzotech.cleanroom;

import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Eight seconds per rising redstone edge, with directed white poof particles. */
public final class AirCleanerBlockEntity extends BlockEntity {
    private CleanerPulse pulse = new CleanerPulse(0, false);

    public AirCleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_CLEANER.get(), pos, state);
    }

    public boolean active() {
        // Only ticks actually processed by the BE count. A pulse after the BE tick
        // must not grant an extra (ninth) one-second dirt reduction at LevelTick.Post.
        return level != null && pulse.active(level.getGameTime());
    }

    public void updateSignal(boolean signal) {
        if (pulse.signal(signal)) setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirCleanerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        // Also handles initial placement next to an already-powered source.
        be.updateSignal(level.hasNeighborSignal(pos));
        if (!be.pulse.tick(level.getGameTime())) return;
        be.setChanged();
        // count=0 means exact velocity, NOT a gaussian random direction.
        if (level.getBlockState(pos.above()).isAir()) {
            for (int i = 0; i < 3; i++) {
                server.sendParticles(ParticleTypes.POOF,
                        pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4,
                        pos.getY() + 1.05,
                        pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.4,
                        0, 0.0, 0.18, 0.0, 1.0);
            }
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ActiveTicks", pulse.remaining());
        tag.putBoolean("Powered", pulse.powered());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pulse = new CleanerPulse(tag.getInt("ActiveTicks"), tag.getBoolean("Powered"));
    }
}
