package com.gonzotech.machines.network;

import com.gonzotech.machines.energy.SecondTierDefs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockState;

/** Six-sided universal Water+Steam node II with a shared 1500 mB/t budget. */
public final class SecondUniversalFluidNodeBlock extends UniversalFluidNodeBlock implements SecondTierPipe {

    public SecondUniversalFluidNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends PipeBlock> makeCodec() {
        return simpleCodec(SecondUniversalFluidNodeBlock::new);
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
