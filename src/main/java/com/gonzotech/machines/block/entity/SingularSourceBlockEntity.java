package com.gonzotech.machines.block.entity;

import com.gonzotech.machines.energy.Transfer;
import com.gonzotech.machines.network.PipeRouting;
import com.gonzotech.machines.network.PipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base for creative-only singular sources.
 *
 * <p>This deliberately has no buffer and no replenishment state: each server tick
 * it offers a fixed virtual budget directly to {@link PipeRouting}.  The router
 * still applies the connected pipe's throughput, all receiver intake caps and the
 * ordinary fair distribution rule.  Thus the block never accumulates a large
 * amount of GTH/GTU and does not require NBT writes while it is running.</p>
 */
abstract class SingularSourceBlockEntity extends BlockEntity {

    private final PipeType pipeType;
    private final long outputPerTick;

    protected SingularSourceBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                        PipeType pipeType, long outputPerTick) {
        super(type, pos, state);
        this.pipeType = pipeType;
        this.outputPerTick = outputPerTick;
    }

    /** Offers this tick's virtual resource budget without persisting any storage state. */
    protected final void emit(Level level, BlockPos pos) {
        if (level.isClientSide() || outputPerTick <= 0) return;
        PipeRouting.drain(level, pos, pipeType, outputPerTick, level.getGameTime(), this::receiverFor);
    }

    /** Maps eligible endpoints to the appropriate resource receiver. */
    protected abstract Transfer.Receiver receiverFor(BlockEntity target, BlockPos targetPos);
}
