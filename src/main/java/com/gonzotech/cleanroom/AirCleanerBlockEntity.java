package com.gonzotech.cleanroom;

import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Eight-second redstone-pulsed vertical cleaner. */
public final class AirCleanerBlockEntity extends BlockEntity {
    private int activeTicks;

    public AirCleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIR_CLEANER.get(), pos, state);
    }

    public boolean active() { return activeTicks > 0; }

    public void pulse() {
        activeTicks = 160;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirCleanerBlockEntity be) {
        if (be.activeTicks > 0) {
            be.activeTicks--;
            if (be.activeTicks % 20 == 0) be.setChanged();
        }
        if (level instanceof net.minecraft.server.level.ServerLevel server && be.active()) {
            // CLOUD particles fall in the client renderer. Use campfire smoke,
            // whose native motion is upward, for the cleaner's vertical stream.
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    3, 0.12, 0.05, 0.12, 0.003);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ActiveTicks", activeTicks);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        activeTicks = tag.getInt("ActiveTicks");
    }
}
