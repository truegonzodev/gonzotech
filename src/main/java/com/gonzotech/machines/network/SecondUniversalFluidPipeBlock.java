package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;

/** Universal Water+Steam pipe II with its own shared 1500 mB/t fluid budget. */
public final class SecondUniversalFluidPipeBlock extends UniversalFluidPipeBlock implements SecondTierPipe {

    public SecondUniversalFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(SecondUniversalFluidPipeBlock::new);
    }

    @Override
    public long throughputLimit(BlockState state, PipeType type) {
        return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
    }

    @Override
    public long sharedFluidThroughputLimit(BlockState state) {
        return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
    }
}
