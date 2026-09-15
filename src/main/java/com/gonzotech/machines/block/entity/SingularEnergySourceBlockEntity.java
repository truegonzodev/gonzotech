package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.MachineDefs;
import com.gonzotech.machines.energy.Sinks.GtuSink;
import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.network.PipeType;
import com.gonzotech.machines.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Creative-only source which directly offers 100,000 GTU/t to energy receivers. */
public final class SingularEnergySourceBlockEntity extends SingularSourceBlockEntity {

    public SingularEnergySourceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINGULAR_ENERGY_SOURCE.get(), pos, state, PipeType.WIRE,
            MachineDefs.SINGULAR_SOURCE_GTU_OUTPUT);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  SingularEnergySourceBlockEntity source) {
        source.emit(level, pos);
    }

    @Override
    protected Transfer.Receiver receiverFor(net.minecraft.world.level.block.entity.BlockEntity target,
                                            BlockPos targetPos) {
        return target instanceof GtuSink sink ? sink::receiveGtu : null;
    }
}
