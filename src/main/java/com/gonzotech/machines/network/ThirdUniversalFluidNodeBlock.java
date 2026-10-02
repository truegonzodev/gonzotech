package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;

/** Universal multi-fluid node of the third opening (shielded, stats x0.88). */
public class ThirdUniversalFluidNodeBlock extends UniversalFluidNodeBlock implements ThirdTierPipe {

    public ThirdUniversalFluidNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(ThirdUniversalFluidNodeBlock::new);
    }

    @Override
    public long throughputLimit(net.minecraft.world.level.block.state.BlockState state, PipeType type) {
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
