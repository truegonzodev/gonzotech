package com.gonzotech.core.block.entity;

import com.gonzotech.core.registry.ModBlocks;
import com.gonzotech.machines.energy.GtBuffer;
import com.gonzotech.machines.energy.NuclearDefs;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.network.PipeCarrier;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.nuclear.ThermalHazards;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Hidden GTH buffer carried by every tungsten block; inactive until a heat carrier touches it. */
public final class TungstenAbsorberBlockEntity extends BlockEntity implements GthSink {

    private final GtBuffer gth = new GtBuffer((long) NuclearDefs.TUNGSTEN_ABSORBER_GTH_CAPACITY);

    public TungstenAbsorberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TUNGSTEN_ABSORBER.get(), pos, state);
    }

    /** Displayed GTH amount for the wrench HUD. */
    public int storedGth() {
        return gth.amountUnitsInt();
    }

    /** Only heat pipes/nodes/universal nodes directly connected to this block activate it. */
    public boolean hasHeatCarrierConnection() {
        if (level == null) return false;
        for (Direction direction : Direction.values()) {
            BlockState adjacent = level.getBlockState(worldPosition.relative(direction));
            if (adjacent.getBlock() instanceof PipeCarrier carrier
                && carrier.carries(adjacent, PipeType.HEAT)
                && carrier.opensToward(adjacent, PipeType.HEAT, direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public long receiveGth(long amount, boolean simulate) {
        if (!hasHeatCarrierConnection()) return 0;
        long accepted = gth.receive(Math.min(amount, (long) NuclearDefs.TUNGSTEN_ABSORBER_GTH_INTAKE), simulate);
        if (!simulate && accepted > 0) setChanged();
        return accepted;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TungstenAbsorberBlockEntity absorber) {
        if (!(level instanceof ServerLevel server)) return;
        boolean changed = false;
        if (!absorber.gth.isEmpty()) {
            absorber.gth.extract(coolingPerTick(level, pos), false);
            changed = true;
        }

        // Above the thresholds the absorber rolls once per second for a 5%
        // ignition and a 3% melt of one random block in the 3×3×3 around it.
        if (absorber.gth.amountAsLong() > NuclearDefs.TUNGSTEN_ABSORBER_LAVA_THRESHOLD) {
            ThermalHazards.maybeMeltToLava(server, pos);
        }
        if (absorber.gth.amountAsLong() > NuclearDefs.TUNGSTEN_ABSORBER_IGNITION_THRESHOLD) {
            ThermalHazards.maybeIgniteAround(server, pos);
        }
        if (changed) absorber.setChanged();
    }

    /**
     * Base dissipation plus 32 GTH/t for every superdense ice block touching the
     * tungsten absorber from one of the six face directions.
     */
    private static long coolingPerTick(Level level, BlockPos pos) {
        long loss = (long) NuclearDefs.TUNGSTEN_ABSORBER_GTH_LOSS;
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).is(ModBlocks.SUPERDENSE_ICE.get())) {
                loss += NuclearDefs.SUPERDENSE_ICE_COOLING_PER_BLOCK;
            }
        }
        return loss;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        gth.save(tag, "Gth");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gth.load(tag, "Gth");
    }
}
