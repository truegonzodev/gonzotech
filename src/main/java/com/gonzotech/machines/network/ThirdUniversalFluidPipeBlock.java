package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;

/** Universal multi-fluid pipe of the third opening (shielded, stats x0.88). */
public class ThirdUniversalFluidPipeBlock extends UniversalFluidPipeBlock implements ThirdTierPipe {

    public ThirdUniversalFluidPipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(ThirdUniversalFluidPipeBlock::new);
    }

    @Override
    public long throughputLimit(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        if (type == PipeType.MASH) {
            return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT / 2;
        }
        return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
    }

    @Override
    public long sharedFluidThroughputLimit(net.minecraft.world.level.block.state.BlockState state) {
        return SecondTierDefs.UNIVERSAL_FLUID_THROUGHPUT;
    }

    @Override
    public double throughputFactor(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
        return STAT_FACTOR;
    }
}
