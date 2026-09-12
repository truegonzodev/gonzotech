package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GthSink;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Creative-only source which directly offers 100,000 GTH/t to heat receivers. */
public final class SingularHeatSourceBlockEntity extends SingularSourceBlockEntity {

    public SingularHeatSourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINGULAR_HEAT_SOURCE.get(), pos, state, PipeType.HEAT,
            MachineDefs.SINGULAR_SOURCE_GTH_OUTPUT);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SingularHeatSourceBlockEntity source) {
        source.emit(level, pos);
    }

    @Override
    protected Transfer.Receiver receiverFor(net.minecraft.world.level.block.entity.BlockEntity target,
                                            BlockPos targetPos) {
        return target instanceof GthSink sink ? sink::receiveGth : null;
    }
}
